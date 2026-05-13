package kr.ac.koreatech.indoor.vps.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "indoor")
public class IndoorProperties {
    private Path storageRoot = Path.of("./var/storage");
    private Python python = new Python();

    public Path getStorageRoot() {
        return storageRoot;
    }

    public void setStorageRoot(Path storageRoot) {
        this.storageRoot = storageRoot;
    }

    public Python getPython() {
        return python;
    }

    public void setPython(Python python) {
        this.python = python;
    }

    public static class Python {
        private boolean enabled;
        private String executable = "python3";
        private Path bridgeScript = Path.of("./scripts/python_bridge/bridge_entry.py");
        private long timeoutSeconds = 60;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getExecutable() {
            return executable;
        }

        public void setExecutable(String executable) {
            this.executable = executable;
        }

        public Path getBridgeScript() {
            return bridgeScript;
        }

        public void setBridgeScript(Path bridgeScript) {
            this.bridgeScript = bridgeScript;
        }

        public long getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(long timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
