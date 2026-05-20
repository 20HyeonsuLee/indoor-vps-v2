package kr.ac.koreatech.indoor.vps.contexts.mapping.application;

import java.util.Map;
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
        String requestId = UUID.randomUUID().toString();
        long primaryStart = System.currentTimeMillis();
        SLAMLocalizeResult primaryResult;
        ResultSnapshot primarySnapshot;
        try {
            primaryResult = primary.localize(command);
            primarySnapshot = new ResultSnapshot(primaryResult, System.currentTimeMillis() - primaryStart, null);
        } catch (RuntimeException e) {
            primarySnapshot = new ResultSnapshot(null, System.currentTimeMillis() - primaryStart, e.getMessage());
            fireShadow(command, requestId, primarySnapshot);
            throw e;
        }
        fireShadow(command, requestId, primarySnapshot);
        return primaryResult;
    }

    private void fireShadow(LocalizeCommand command, String requestId, ResultSnapshot primarySnapshot) {
        try {
            shadowExecutor.execute(() -> runShadow(command, requestId, primarySnapshot));
        } catch (RuntimeException e) {
            log.warn("[shadow] enqueue failed: {}", e.getMessage());
        }
    }

    private void runShadow(LocalizeCommand command, String requestId, ResultSnapshot primarySnapshot) {
        long t0 = System.currentTimeMillis();
        ResultSnapshot shadowSnapshot;
        try {
            SLAMLocalizeResult shadowResult = shadow.localize(command);
            shadowSnapshot = new ResultSnapshot(shadowResult, System.currentTimeMillis() - t0, null);
        } catch (RuntimeException e) {
            shadowSnapshot = new ResultSnapshot(null, System.currentTimeMillis() - t0, e.getMessage());
        }
        DiffSnapshot diff = computeDiff(primarySnapshot.result(),
                shadowSnapshot.result() == null ? null : shadowSnapshot.result());
        int depthCount = command.depths() == null ? 0 : (int) command.depths().stream().filter(d -> d != null).count();
        shadowLogger.append(new LocalizeShadowEntry(
                requestId,
                command.buildingId(),
                command.floorId(),
                command.images() == null ? 0 : command.images().size(),
                depthCount,
                primarySnapshot,
                shadowSnapshot,
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
