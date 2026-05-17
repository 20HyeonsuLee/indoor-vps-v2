package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.Point3;

/**
 * ARKit (iOS, Y-up, -Z-forward) → RTABMap (ROS, Z-up, X-forward) 좌표 변환.
 *
 * <pre>
 *   rt_x = -arkit_z
 *   rt_y = -arkit_x
 *   rt_z =  arkit_y
 * </pre>
 */
public final class ArKitToRtabmap {

    private ArKitToRtabmap() {
    }

    public static Point3 convert(double arkitTx, double arkitTy, double arkitTz) {
        return new Point3(-arkitTz, -arkitTx, arkitTy);
    }
}
