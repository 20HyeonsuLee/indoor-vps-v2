package kr.ac.koreatech.indoor.vps.contexts.mapping.application;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.slam.LocalizeCommand;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.slam.SLAMLocalizeResult;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.slam.SlamLocalizer;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.logging.ShadowLocalizeLogger;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.logging.ShadowLocalizeLogger.DiffSnapshot;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.logging.ShadowLocalizeLogger.LocalizeShadowEntry;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.logging.ShadowLocalizeLogger.ResultSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Controller entry. Primary localizer는 동기 호출 후 응답에 사용, Shadow localizer는
 * 별도 thread pool에서 fire-and-forget으로 호출. 두 결과 + diff를 jsonl 로깅.
 *
 * <p>Shadow가 실패해도(예: v2 artefact 없는 floor) primary 응답에는 영향 없음.
 */
@Service
public class SlamLocalizationFacade {
    private static final Logger log = LoggerFactory.getLogger(SlamLocalizationFacade.class);

    // 응답으로 SHADOW(B안) 결과를 돌려보낼 floor. 나머지 floor는 PRIMARY가 응답을 줌.
    // 의도: 특정 floor에서 B안의 SuperPoint+rtabmap-native artefact가 더 정확한 경우
    // 코드 변경 없이 floor 단위로 점진 전환할 수 있도록 hardcoded set.
    private static final Set<UUID> SHADOW_AS_PRIMARY_FLOORS = Set.of(
            UUID.fromString("c1bc143c-052b-4d0f-8e95-f6b2f521c144")  // 2공학관(Real) 2층
    );

    private final SlamLocalizer primary;
    private final SlamLocalizer shadow;
    private final ShadowLocalizeLogger shadowLogger;
    private final ExecutorService shadowExecutor;

    public SlamLocalizationFacade(
            @Qualifier("primaryLocalizer") SlamLocalizer primary,
            @Qualifier("shadowLocalizer") SlamLocalizer shadow,
            ShadowLocalizeLogger shadowLogger
    ) {
        this.primary = primary;
        this.shadow = shadow;
        this.shadowLogger = shadowLogger;
        // Shadow 호출은 응답 path 밖이므로 작은 dedicated pool. 동시 다중 호출도 견디게 4 thread.
        this.shadowExecutor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "shadow-localize-");
            t.setDaemon(true);
            return t;
        });
    }

    public SLAMLocalizeResult localize(LocalizeCommand command) {
        boolean swap = shouldUseShadowAsPrimary(command);
        SlamLocalizer responder = swap ? shadow : primary;
        SlamLocalizer secondary = swap ? primary : shadow;
        String responderLabel = swap ? "shadow" : "primary";
        String secondaryLabel = swap ? "primary" : "shadow";

        String requestId = UUID.randomUUID().toString();
        long t0 = System.currentTimeMillis();
        SLAMLocalizeResult result;
        ResultSnapshot responderSnapshot;
        try {
            result = responder.localize(command);
            responderSnapshot = new ResultSnapshot(result, System.currentTimeMillis() - t0, null);
        } catch (RuntimeException e) {
            responderSnapshot = new ResultSnapshot(null, System.currentTimeMillis() - t0, e.getMessage());
            return fallbackToSecondary(command, requestId, responderSnapshot, secondary, responderLabel, secondaryLabel, e);
        }
        fireSecondary(command, requestId, responderSnapshot, secondary, responderLabel, secondaryLabel);
        return result;
    }

    private SLAMLocalizeResult fallbackToSecondary(
            LocalizeCommand command,
            String requestId,
            ResultSnapshot responderSnapshot,
            SlamLocalizer secondary,
            String responderLabel,
            String secondaryLabel,
            RuntimeException original
    ) {
        long t0 = System.currentTimeMillis();
        try {
            SLAMLocalizeResult fallback = secondary.localize(command);
            ResultSnapshot secondarySnapshot = new ResultSnapshot(fallback, System.currentTimeMillis() - t0, null);
            log.info("[localize] responder={} failed; fallback={} floor={}",
                    responderLabel, secondaryLabel, command.floorId());
            appendShadowLog(command, requestId, responderSnapshot, secondarySnapshot);
            return fallback;
        } catch (RuntimeException secondaryError) {
            ResultSnapshot secondarySnapshot = new ResultSnapshot(null, System.currentTimeMillis() - t0,
                    secondaryError.getMessage());
            appendShadowLog(command, requestId, responderSnapshot, secondarySnapshot);
            throw original;
        }
    }

    private boolean shouldUseShadowAsPrimary(LocalizeCommand command) {
        if (command.floorId() == null || command.floorId().isBlank()) {
            return false;
        }
        try {
            return SHADOW_AS_PRIMARY_FLOORS.contains(UUID.fromString(command.floorId()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void fireSecondary(
            LocalizeCommand command,
            String requestId,
            ResultSnapshot responderSnapshot,
            SlamLocalizer secondary,
            String responderLabel,
            String secondaryLabel
    ) {
        try {
            shadowExecutor.execute(() -> runSecondary(command, requestId, responderSnapshot, secondary, responderLabel, secondaryLabel));
        } catch (RuntimeException e) {
            log.warn("[shadow] enqueue failed: {}", e.getMessage());
        }
    }

    private void runSecondary(
            LocalizeCommand command,
            String requestId,
            ResultSnapshot responderSnapshot,
            SlamLocalizer secondary,
            String responderLabel,
            String secondaryLabel
    ) {
        long t0 = System.currentTimeMillis();
        ResultSnapshot secondarySnapshot;
        try {
            SLAMLocalizeResult res = secondary.localize(command);
            secondarySnapshot = new ResultSnapshot(res, System.currentTimeMillis() - t0, null);
        } catch (RuntimeException e) {
            secondarySnapshot = new ResultSnapshot(null, System.currentTimeMillis() - t0, e.getMessage());
        }
        log.info("[localize] responder={} secondary={} floor={}", responderLabel, secondaryLabel, command.floorId());
        appendShadowLog(command, requestId, responderSnapshot, secondarySnapshot);
    }

    private void appendShadowLog(
            LocalizeCommand command,
            String requestId,
            ResultSnapshot responderSnapshot,
            ResultSnapshot secondarySnapshot
    ) {
        DiffSnapshot diff = computeDiff(responderSnapshot.result(),
                secondarySnapshot.result() == null ? null : secondarySnapshot.result());
        int depthCount = command.depths() == null ? 0 : (int) command.depths().stream().filter(d -> d != null).count();
        // LocalizeShadowEntry는 [primary, shadow] 슬롯 의미. swap된 경우 응답한 쪽(shadow)을
        // primary 슬롯에 두면 사후 분석할 때 "응답에 사용한 결과"가 항상 primary 슬롯에 있어 일관.
        shadowLogger.append(new LocalizeShadowEntry(
                requestId,
                command.buildingId(),
                command.floorId(),
                command.images() == null ? 0 : command.images().size(),
                depthCount,
                responderSnapshot,
                secondarySnapshot,
                diff
        ));
    }

    private DiffSnapshot computeDiff(SLAMLocalizeResult a, SLAMLocalizeResult b) {
        if (a == null || b == null) {
            return null;
        }
        Map<String, Object> pa = a.pose();
        Map<String, Object> pb = b.pose();
        if (pa == null || pb == null) {
            return null;
        }
        double dx = num(pa, "x") - num(pb, "x");
        double dy = num(pa, "y") - num(pb, "y");
        double dz = num(pa, "z") - num(pb, "z");
        double translationM = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double yawA = yawFromQuat(num(pa, "qx"), num(pa, "qy"), num(pa, "qz"), num(pa, "qw"));
        double yawB = yawFromQuat(num(pb, "qx"), num(pb, "qy"), num(pb, "qz"), num(pb, "qw"));
        double yawDiff = Math.toDegrees(normalizeAngle(yawA - yawB));
        return new DiffSnapshot(translationM, yawDiff);
    }

    private double num(Map<String, Object> pose, String key) {
        Object v = pose.get(key);
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }

    /** ENU yaw (rotation around Z). x_axis 회전 — rtabmap world frame 기준. */
    private double yawFromQuat(double qx, double qy, double qz, double qw) {
        double siny = 2.0 * (qw * qz + qx * qy);
        double cosy = 1.0 - 2.0 * (qy * qy + qz * qz);
        return Math.atan2(siny, cosy);
    }

    private double normalizeAngle(double rad) {
        while (rad > Math.PI) rad -= 2 * Math.PI;
        while (rad < -Math.PI) rad += 2 * Math.PI;
        return rad;
    }
}
