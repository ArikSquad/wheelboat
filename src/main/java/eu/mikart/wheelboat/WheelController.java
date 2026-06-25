package eu.mikart.wheelboat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.nio.FloatBuffer;
import java.util.Locale;

public final class WheelController {
    private static final int DEVICE_SCAN_INTERVAL_TICKS = 20;
    private static final WheelController INSTANCE = new WheelController();

    private final WheelboatConfig config = WheelboatConfig.current();
    private final ForceFeedbackManager forceFeedback = new ForceFeedbackManager();
    private int selectedJoystick = -1;
    private int scanCooldown;
    private long nextAxisLog;
    private float capturedSteeringCenter;
    private boolean hasCapturedSteeringCenter;
    private float steering;
    private float accelerator;
    private float brake;
    private float steeringScale = 1.0F;
    private float acceleratorScale = 1.0F;
    private float brakeScale = 1.0F;
    private boolean active;

    public static Input enrichInput(Input keyboardInput) {
        return INSTANCE.applyWheelInput(keyboardInput);
    }

    public static float steeringAmount() {
        return INSTANCE.active ? INSTANCE.steeringScale : 1.0F;
    }

    public static float acceleratorAmount() {
        return INSTANCE.active ? INSTANCE.acceleratorScale : 1.0F;
    }

    public static float brakeAmount() {
        return INSTANCE.active ? INSTANCE.brakeScale : 1.0F;
    }

    static void configChanged() {
        INSTANCE.resetConfiguredDevice();
    }

    private Input applyWheelInput(Input keyboardInput) {
        Minecraft client = Minecraft.getInstance();

        if (!isDrivingBoat(client) || !config.enabled) {
            deactivate();
            return keyboardInput;
        }

        FloatBuffer axes = getAxes();
        if (axes == null) {
            deactivate();
            return keyboardInput;
        }

        updateAnalogValues(axes);
        active = true;
        steeringScale = !config.analogInput || keyboardInput.left() || keyboardInput.right() ? 1.0F : Math.abs(steering);
        acceleratorScale = !config.analogInput || keyboardInput.forward() ? 1.0F : accelerator;
        brakeScale = !config.analogInput || keyboardInput.backward() ? 1.0F : brake;
        logAxesIfEnabled(axes);

        updateForceFeedback(client);

        boolean wheelLeft = steering < -config.steeringDeadzone;
        boolean wheelRight = steering > config.steeringDeadzone;
        boolean wheelForward = accelerator > config.acceleratorThreshold;
        boolean wheelBackward = brake > config.brakeThreshold;

        return new Input(keyboardInput.forward() || wheelForward, keyboardInput.backward() || wheelBackward, keyboardInput.left() || wheelLeft, keyboardInput.right() || wheelRight, keyboardInput.jump(), keyboardInput.shift(), keyboardInput.sprint());
    }

    private void updateAnalogValues(final FloatBuffer axes) {
        float rawSteering = readAxis(axes, config.steeringAxis);
        float center = config.autoCenterSteering && hasCapturedSteeringCenter ? capturedSteeringCenter : config.steeringCenter;
        steering = normalizeSteering(rawSteering, config.steeringMinimum, center, config.steeringMaximum);
        if (config.invertSteering) {
            steering = -steering;
        }

        if (config.combinedPedals) {
            float pedals = normalizeSteering(readAxis(axes, config.acceleratorAxis), config.combinedPedalsAcceleratorValue, config.combinedPedalsRestingValue, config.combinedPedalsBrakeValue);
            if (config.invertCombinedPedals) {
                pedals = -pedals;
            }
            accelerator = Math.max(0.0F, -pedals);
            brake = Math.max(0.0F, pedals);
        } else {
            accelerator = normalizePedal(readAxis(axes, config.acceleratorAxis), config.acceleratorRestingValue, config.acceleratorPressedValue);
            brake = normalizePedal(readAxis(axes, config.brakeAxis), config.brakeRestingValue, config.brakePressedValue);
            if (config.invertAccelerator) {
                accelerator = 1.0F - accelerator;
            }
            if (config.invertBrake) {
                brake = 1.0F - brake;
            }
        }
    }

    private void resetConfiguredDevice() {
        selectedJoystick = -1;
        hasCapturedSteeringCenter = false;
        forceFeedback.close();
    }

    private FloatBuffer getAxes() {
        if (selectedJoystick >= GLFW.GLFW_JOYSTICK_1 && GLFW.glfwJoystickPresent(selectedJoystick) && matchesConfiguredDevice(selectedJoystick)) {
            return GLFW.glfwGetJoystickAxes(selectedJoystick);
        }

        if (scanCooldown-- > 0) {
            return null;
        }
        scanCooldown = DEVICE_SCAN_INTERVAL_TICKS;

        boolean preferWheelName = config.joystickId < 0 && config.deviceNameContains.isBlank();
        return findAxes(preferWheelName);
    }

    private FloatBuffer findAxes(boolean requireWheelName) {
        for (int joystick = GLFW.GLFW_JOYSTICK_1; joystick <= GLFW.GLFW_JOYSTICK_LAST; joystick++) {
            if (!GLFW.glfwJoystickPresent(joystick) || !matchesConfiguredDevice(joystick) || (requireWheelName && !isLikelyWheel(GLFW.glfwGetJoystickName(joystick)))) {
                continue;
            }

            FloatBuffer axes = GLFW.glfwGetJoystickAxes(joystick);
            if (axes == null) {
                continue;
            }

            selectedJoystick = joystick;
            capturedSteeringCenter = readAxis(axes, config.steeringAxis);
            hasCapturedSteeringCenter = true;
            WheelboatClient.LOGGER.info("Using steering wheel device {}: {} ({}), captured steering center {}", joystick, GLFW.glfwGetJoystickName(joystick), GLFW.glfwGetJoystickGUID(joystick), capturedSteeringCenter);
            return axes;
        }

        selectedJoystick = -1;
        return null;
    }

    private boolean matchesConfiguredDevice(int joystick) {
        if (config.joystickId >= 0 && joystick != config.joystickId) {
            return false;
        }

        String name = GLFW.glfwGetJoystickName(joystick);
        return config.deviceNameContains.isBlank() || (name != null && name.toLowerCase(Locale.ROOT).contains(config.deviceNameContains.toLowerCase(Locale.ROOT)));
    }

    private static boolean isLikelyWheel(final @NotNull String name) {
        if (name == null) {
            return false;
        }

        String lowerName = name.toLowerCase(Locale.ROOT);
        return lowerName.contains("wheel") || lowerName.contains("racing") || lowerName.contains("steering") || lowerName.contains("driving force");
    }

    private void deactivate() {
        active = false;
        steering = 0.0F;
        accelerator = 0.0F;
        brake = 0.0F;
        steeringScale = 1.0F;
        acceleratorScale = 1.0F;
        brakeScale = 1.0F;
        forceFeedback.update(new ForceFeedbackRequest(false, config.forceFeedbackDevice, 0.0F, 0.0F));
    }

    private static boolean isDrivingBoat(Minecraft client) {
        return client.player != null && client.screen == null && client.player.getVehicle() instanceof AbstractBoat boat && boat.getControllingPassenger() == client.player;
    }

    private void updateForceFeedback(Minecraft client) {
        if (!config.forceFeedback || client.player == null || !(client.player.getVehicle() instanceof AbstractBoat boat)) {
            forceFeedback.update(new ForceFeedbackRequest(false, config.forceFeedbackDevice, 0.0F, 0.0F));
            return;
        }

        SurfaceMaterial material = SurfaceMaterial.under(boat);
        MaterialForceFeedback materialFeedback = config.forceFeedbackFor(material);
        float spring = config.forceFeedbackStrength * materialFeedback.springMultiplier;
        float roughness = config.forceFeedbackStrength * materialFeedback.roughness * Math.max(accelerator, brake);
        forceFeedback.update(new ForceFeedbackRequest(true, config.forceFeedbackDevice, spring, roughness));
    }

    private static float readAxis(FloatBuffer axes, int index) {
        return index < 0 || index >= axes.limit() ? 0.0F : axes.get(index);
    }

    private static float normalizeSteering(float value, float minimum, float center, float maximum) {
        float range = value < center ? center - minimum : maximum - center;
        return range <= 0.0001F ? 0.0F : Math.clamp((value - center) / range, -1.0F, 1.0F);
    }

    private static float normalizePedal(float value, float resting, float pressed) {
        float range = pressed - resting;
        return Math.abs(range) <= 0.0001F ? 0.0F : Math.clamp((value - resting) / range, 0.0F, 1.0F);
    }

    private void logAxesIfEnabled(FloatBuffer axes) {
        long now = System.currentTimeMillis();
        if (!config.logAxes || now < nextAxisLog) {
            return;
        }
        nextAxisLog = now + 1000L;

        StringBuilder values = new StringBuilder();
        for (int index = 0; index < axes.limit(); index++) {
            if (index > 0) {
                values.append(", ");
            }
            values.append(index).append('=').append(String.format(Locale.ROOT, "%.3f", axes.get(index)));
        }
        WheelboatClient.LOGGER.info("Steering wheel axes: {} | normalized steering={}, accelerator={}, brake={}", values, steering, accelerator, brake);
    }
}
