package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.storage;

import static kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.StreamingScanStorage.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class ScanDbWriter {
    private static final java.util.Base64.Decoder BASE64 = java.util.Base64.getDecoder();

    boolean insertFrame(Connection connection, FramePayload frame) throws SQLException {
        if (existsNode(connection, frame.nodeId())) {
            return false;
        }
        try (PreparedStatement nodeInsert = connection.prepareStatement("""
                INSERT INTO Node(id, stamp, map_id, weight, label, time_enter, pose, ground_truth_pose, velocity, gps, env_sensors)
                VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL, NULL, NULL)
                """);
             PreparedStatement dataInsert = connection.prepareStatement("""
                     INSERT OR REPLACE INTO Data(id, image, depth, calibration, scan, scan_info, user_data)
                     VALUES (?, ?, ?, ?, ?, ?, ?)
                     """)) {
            nodeInsert.setInt(1, frame.nodeId());
            nodeInsert.setDouble(2, frame.stamp());
            nodeInsert.setInt(3, frame.mapId() == null ? 0 : frame.mapId());
            nodeInsert.setInt(4, frame.weight() == null ? 0 : frame.weight());
            nodeInsert.setString(5, frame.label());
            nodeInsert.setLong(6, Math.round(frame.stamp() * 1000.0));
            nodeInsert.setBytes(7, decodePose(frame.pose()));
            nodeInsert.executeUpdate();

            dataInsert.setInt(1, frame.nodeId());
            dataInsert.setBytes(2, decodeOptionalBlob(frame.image()));
            dataInsert.setBytes(3, decodeOptionalBlob(frame.depth()));
            dataInsert.setBytes(4, decodeOptionalBlob(frame.calibration()));
            dataInsert.setBytes(5, decodeOptionalBlob(frame.scan()));
            dataInsert.setBytes(6, decodeOptionalBlob(frame.scanInfo()));
            dataInsert.setBytes(7, decodeOptionalBlob(frame.userData()));
            dataInsert.executeUpdate();
            return true;
        }
    }

    boolean insertLink(Connection connection, FrameLinkPayload link) throws SQLException {
        try (PreparedStatement linkInsert = connection.prepareStatement("""
                INSERT OR IGNORE INTO Link(from_id, to_id, type, transform, information_matrix, user_data)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            linkInsert.setInt(1, link.fromId());
            linkInsert.setInt(2, link.toId());
            linkInsert.setInt(3, link.type() == null ? 0 : link.type());
            linkInsert.setBytes(4, decodeRequiredBlob(link.transform(), "INVALID_LINK_TRANSFORM", "link transform must be base64"));
            linkInsert.setBytes(5, decodeOptionalBlob(link.informationMatrix(), defaultInformationMatrix()));
            linkInsert.setBytes(6, decodeOptionalBlob(link.userData()));
            return linkInsert.executeUpdate() > 0;
        }
    }

    private boolean existsNode(Connection connection, int nodeId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM Node WHERE id = ?")) {
            statement.setInt(1, nodeId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private byte[] decodePose(String encoded) {
        try {
            byte[] bytes = BASE64.decode(encoded == null ? "" : encoded);
            if (bytes.length != 48) {
                throw new IllegalArgumentException("expected 48 bytes, got " + bytes.length);
            }
            return bytes;
        } catch (IllegalArgumentException e) {
            throw new ClientApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_FRAME_POSE",
                    "frame pose must be a base64-encoded 48-byte RTAB-Map pose blob"
            );
        }
    }

    byte[] decodeOptionalBlob(String encoded) {
        return decodeOptionalBlob(encoded, null);
    }

    byte[] decodeOptionalBlob(String encoded, byte[] fallback) {
        if (encoded == null || encoded.isBlank()) {
            return fallback;
        }
        return decodeRequiredBlob(encoded, "INVALID_FRAME_BLOB", "frame blob must be base64");
    }

    private byte[] defaultInformationMatrix() {
        ByteBuffer buffer = ByteBuffer.allocate(288).order(ByteOrder.LITTLE_ENDIAN);
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 6; col++) {
                buffer.putDouble(row == col ? 1.0 : 0.0);
            }
        }
        return buffer.array();
    }

    private byte[] decodeRequiredBlob(String encoded, String code, String message) {
        try {
            return BASE64.decode(encoded == null ? "" : encoded);
        } catch (IllegalArgumentException e) {
            throw new ClientApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
        }
    }
}
