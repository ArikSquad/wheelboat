package eu.mikart.wheelboat;

public final class WheelboatConfig {
    private static final WheelboatConfig INSTANCE = new WheelboatConfig();

    boolean enabled = true;
    int joystickId = -1;
    String deviceNameContains = "";
    boolean logAxes;
    boolean analogInput = true;

    int steeringAxis = 0;
    boolean autoCenterSteering = true;
    float steeringMinimum = -1.0F;
    float steeringCenter;
    float steeringMaximum = 1.0F;
    boolean invertSteering;
    float steeringDeadzone = 0.02F;

    boolean combinedPedals;
    boolean invertCombinedPedals;
    float combinedPedalsAcceleratorValue = -1.0F;
    float combinedPedalsRestingValue;
    float combinedPedalsBrakeValue = 1.0F;

    int acceleratorAxis = 2;
    boolean invertAccelerator;
    float acceleratorRestingValue = -1.0F;
    float acceleratorPressedValue = 1.0F;
    float acceleratorThreshold = 0.1F;

    int brakeAxis = 3;
    boolean invertBrake;
    float brakeRestingValue = 1.0F;
    float brakePressedValue = -1.0F;
    float brakeThreshold = 0.1F;

    boolean forceFeedback = true;
    String forceFeedbackDevice = "";
    float forceFeedbackStrength = 0.25F;
    MaterialForceFeedback waterForceFeedback = new MaterialForceFeedback(1.0F, 0.04F);
    MaterialForceFeedback iceForceFeedback = new MaterialForceFeedback(0.55F, 0.01F);
    MaterialForceFeedback sandForceFeedback = new MaterialForceFeedback(1.2F, 0.45F);
    MaterialForceFeedback dirtForceFeedback = new MaterialForceFeedback(1.0F, 0.28F);
    MaterialForceFeedback stoneForceFeedback = new MaterialForceFeedback(1.35F, 0.35F);
    MaterialForceFeedback woodForceFeedback = new MaterialForceFeedback(0.9F, 0.18F);
    MaterialForceFeedback defaultForceFeedback = new MaterialForceFeedback(1.0F, 0.15F);

    public static WheelboatConfig current() {
        return INSTANCE;
    }

    void applyChanges() {
        sanitize();
        WheelController.configChanged();
    }

    private void sanitize() {
        steeringDeadzone = Math.clamp(steeringDeadzone, 0.0F, 1.0F);
        acceleratorThreshold = Math.clamp(acceleratorThreshold, 0.0F, 1.0F);
        brakeThreshold = Math.clamp(brakeThreshold, 0.0F, 1.0F);
        forceFeedbackStrength = Math.clamp(forceFeedbackStrength, 0.0F, 1.0F);
        waterForceFeedback = sanitizeProfile(waterForceFeedback, 1.0F, 0.04F);
        iceForceFeedback = sanitizeProfile(iceForceFeedback, 0.55F, 0.01F);
        sandForceFeedback = sanitizeProfile(sandForceFeedback, 1.2F, 0.45F);
        dirtForceFeedback = sanitizeProfile(dirtForceFeedback, 1.0F, 0.28F);
        stoneForceFeedback = sanitizeProfile(stoneForceFeedback, 1.35F, 0.35F);
        woodForceFeedback = sanitizeProfile(woodForceFeedback, 0.9F, 0.18F);
        defaultForceFeedback = sanitizeProfile(defaultForceFeedback, 1.0F, 0.15F);
        if (deviceNameContains == null) {
            deviceNameContains = "";
        }
        if (forceFeedbackDevice == null) {
            forceFeedbackDevice = "";
        }
    }

    MaterialForceFeedback forceFeedbackFor(SurfaceMaterial material) {
        return switch (material) {
            case WATER -> waterForceFeedback;
            case ICE -> iceForceFeedback;
            case SAND -> sandForceFeedback;
            case DIRT -> dirtForceFeedback;
            case STONE -> stoneForceFeedback;
            case WOOD -> woodForceFeedback;
            case DEFAULT -> defaultForceFeedback;
        };
    }

    private static MaterialForceFeedback sanitizeProfile(MaterialForceFeedback profile, float springMultiplier, float roughness) {
        if (profile == null) {
            profile = new MaterialForceFeedback(springMultiplier, roughness);
        }
        profile.sanitize();
        return profile;
    }
}
