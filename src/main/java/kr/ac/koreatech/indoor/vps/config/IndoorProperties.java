package kr.ac.koreatech.indoor.vps.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "indoor")
public class IndoorProperties {
    private Path storageRoot = Path.of("./var/storage");
    private Python python = new Python();
    private Rtabmap rtabmap = new Rtabmap();
    private BuildWorker buildWorker = new BuildWorker();
    private FixtureCapture fixtureCapture = new FixtureCapture();

    public Path getStorageRoot() { return storageRoot; }
    public void setStorageRoot(Path storageRoot) { this.storageRoot = storageRoot; }

    public Python getPython() { return python; }
    public void setPython(Python python) { this.python = python; }

    public Rtabmap getRtabmap() { return rtabmap; }
    public void setRtabmap(Rtabmap rtabmap) { this.rtabmap = rtabmap; }

    public BuildWorker getBuildWorker() { return buildWorker; }
    public void setBuildWorker(BuildWorker buildWorker) { this.buildWorker = buildWorker; }

    public FixtureCapture getFixtureCapture() { return fixtureCapture; }
    public void setFixtureCapture(FixtureCapture fixtureCapture) { this.fixtureCapture = fixtureCapture; }

    public static class Python {
        private boolean enabled;
        private String executable = "python3";
        private Path bridgeScript = Path.of("./scripts/python_bridge/bridge_entry.py");
        private String backendSource = "./python/legacy_backend/src";
        private String device = "cpu";
        private long timeoutSeconds = 60;
        private long buildSuperpointIndexTimeoutSeconds = 600;
        private Daemon daemon = new Daemon();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getExecutable() { return executable; }
        public void setExecutable(String executable) { this.executable = executable; }

        public Path getBridgeScript() { return bridgeScript; }
        public void setBridgeScript(Path bridgeScript) { this.bridgeScript = bridgeScript; }

        public String getBackendSource() { return backendSource; }
        public void setBackendSource(String backendSource) { this.backendSource = backendSource; }

        public String getDevice() { return device; }
        public void setDevice(String device) { this.device = device; }

        public long getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(long timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

        public long getBuildSuperpointIndexTimeoutSeconds() { return buildSuperpointIndexTimeoutSeconds; }
        public void setBuildSuperpointIndexTimeoutSeconds(long buildSuperpointIndexTimeoutSeconds) {
            this.buildSuperpointIndexTimeoutSeconds = buildSuperpointIndexTimeoutSeconds;
        }

        public Daemon getDaemon() { return daemon; }
        public void setDaemon(Daemon daemon) { this.daemon = daemon; }

        public static class Daemon {
            private boolean enabled = true;

            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
        }
    }

    public static class Rtabmap {
        private Reprocess reprocess = new Reprocess();

        public Reprocess getReprocess() { return reprocess; }
        public void setReprocess(Reprocess reprocess) { this.reprocess = reprocess; }

        public static class Reprocess {
            private boolean enabled = true;
            private boolean required;
            private String executable = "rtabmap-reprocess";
            private long timeoutSeconds = 300;

            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }

            public boolean isRequired() { return required; }
            public void setRequired(boolean required) { this.required = required; }

            public String getExecutable() { return executable; }
            public void setExecutable(String executable) { this.executable = executable; }

            public long getTimeoutSeconds() { return timeoutSeconds; }
            public void setTimeoutSeconds(long timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        }
    }

    public static class BuildWorker {
        private boolean enabled = true;
        private long pollIntervalMs = 2000;
        private int batchSize = 1;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public long getPollIntervalMs() { return pollIntervalMs; }
        public void setPollIntervalMs(long pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }

        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    }

    public static class FixtureCapture {
        private boolean enabled;
        private Path root = Path.of("./test-fixtures/captured");

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public Path getRoot() { return root; }
        public void setRoot(Path root) { this.root = root; }
    }
}
