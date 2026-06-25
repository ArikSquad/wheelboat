package eu.mikart.wheelboat;

final class ForceFeedbackManager implements ForceFeedbackDevice {
    private final ForceFeedbackDevice device = createDevice();

    @Override
    public void update(ForceFeedbackRequest request) {
        device.update(request);
    }

    @Override
    public void close() {
        device.close();
    }

    private static ForceFeedbackDevice createDevice() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("linux")) {
            return new LinuxForceFeedback();
        }
        if (os.contains("windows")) {
            return new WindowsForceFeedback();
        }
        return NoopForceFeedback.INSTANCE;
    }

    private enum NoopForceFeedback implements ForceFeedbackDevice {
        INSTANCE;

        @Override
        public void update(ForceFeedbackRequest request) {
        }

        @Override
        public void close() {
        }
    }
}
