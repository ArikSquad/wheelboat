package eu.mikart.wheelboat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WheelboatConfig {
    private static final int CURRENT_VERSION = 5;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("wheelboat.json");

    int configVersion;
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

    private transient long lastModified;
    private transient long nextReloadCheck;

    public static WheelboatConfig load() {
        try {
            Files.createDirectories(PATH.getParent());
            if (!Files.exists(PATH)) {
                WheelboatConfig config = new WheelboatConfig();
                config.configVersion = CURRENT_VERSION;
                config.save();
                return config;
            }

            try (Reader reader = Files.newBufferedReader(PATH)) {
                WheelboatConfig config = GSON.fromJson(reader, WheelboatConfig.class);
                if (config == null) {
                    config = new WheelboatConfig();
                }
                boolean migrated = config.migrate();
                config.sanitize();
                config.lastModified = Files.getLastModifiedTime(PATH).toMillis();
                if (migrated) {
                    config.save();
                    WheelboatClient.LOGGER.info("Migrated steering wheel configuration to version {}", CURRENT_VERSION);
                }
                return config;
            }
        } catch (IOException | RuntimeException exception) {
            WheelboatClient.LOGGER.error("Could not load {}; using defaults", PATH, exception);
            return new WheelboatConfig();
        }
    }

    WheelboatConfig reloadIfChanged() {
        long now = System.currentTimeMillis();
        if (now < nextReloadCheck) {
            return this;
        }
        nextReloadCheck = now + 1000L;

        try {
            return Files.getLastModifiedTime(PATH).toMillis() == lastModified ? this : load();
        } catch (IOException exception) {
            return this;
        }
    }

    public void save() throws IOException {
        sanitize();
        try (Writer writer = Files.newBufferedWriter(PATH)) {
            GSON.toJson(this, writer);
        }
        lastModified = Files.getLastModifiedTime(PATH).toMillis();
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

    private boolean migrate() {
        if (configVersion >= CURRENT_VERSION) {
            return false;
        }

        configVersion = CURRENT_VERSION;
        return true;
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
