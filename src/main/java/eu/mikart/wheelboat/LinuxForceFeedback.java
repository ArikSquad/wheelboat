package eu.mikart.wheelboat;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

final class LinuxForceFeedback implements ForceFeedbackDevice {
    private static final int O_RDWR = 2;
    private static final int O_NONBLOCK = 2048;
    private static final int EV_FF = 0x15;
    private static final int FF_RUMBLE = 0x50;
    private static final int FF_SPRING = 0x53;
    private static final long EVIOCSFF = 0x40304580L;
    private static final long EVIOCRMFF = 0x40044581L;
    private static final int EFFECT_SIZE = 48;
    private static final int INPUT_EVENT_SIZE = 24;
    private static final int CONDITION_SIZE = 12;
    private static final int CONDITION_OFFSET = 16;
    private static final long RETRY_DELAY_MILLIS = 5000L;

    private int fd = -1;
    private short springEffectId = -1;
    private short roughnessEffectId = -1;
    private int lastSpringStrength = -1;
    private int lastRoughnessStrength = -1;
    private long nextOpenAttempt;
    private boolean roughnessSupported = true;

    public void update(ForceFeedbackRequest request) {
        if (!request.enabled()) {
            close();
            nextOpenAttempt = 0L;
            return;
        }

        if (fd < 0) {
            long now = System.currentTimeMillis();
            if (now < nextOpenAttempt) {
                return;
            }
            if (!open(request.device())) {
                nextOpenAttempt = now + RETRY_DELAY_MILLIS;
                return;
            }
            nextOpenAttempt = 0L;
        }

        int springStrength = scale(request.springStrength());
        int roughnessStrength = scale(request.roughnessStrength());
        if (springStrength == lastSpringStrength && roughnessStrength == lastRoughnessStrength) {
            return;
        }

        if (!updateSpring(springStrength) || !updateRoughness(roughnessStrength)) {
            close();
            nextOpenAttempt = System.currentTimeMillis() + RETRY_DELAY_MILLIS;
            return;
        }
        lastSpringStrength = springStrength;
        lastRoughnessStrength = roughnessStrength;
    }

    private boolean updateSpring(int strength) {
        if (strength == lastSpringStrength) {
            return true;
        }
        if (!uploadSpringEffect(strength)) {
            WheelboatClient.LOGGER.warn("Could not upload steering wheel spring effect (errno {})", Native.getLastError());
            return false;
        }
        if (!playEffect(springEffectId, strength > 0)) {
            WheelboatClient.LOGGER.warn("Could not start steering wheel spring effect (errno {})", Native.getLastError());
            return false;
        }
        return true;
    }

    private boolean updateRoughness(int strength) {
        if (!roughnessSupported) {
            return true;
        }
        if (strength == lastRoughnessStrength) {
            return true;
        }
        if (!uploadRumbleEffect(strength)) {
            WheelboatClient.LOGGER.warn("Could not upload steering wheel roughness effect (errno {})", Native.getLastError());
            roughnessSupported = false;
            return true;
        }
        if (!playEffect(roughnessEffectId, strength > 0)) {
            WheelboatClient.LOGGER.warn("Could not start steering wheel roughness effect (errno {})", Native.getLastError());
            roughnessSupported = false;
            return true;
        }
        return true;
    }

    public void close() {
        if (fd < 0) {
            return;
        }

        removeEffect(springEffectId);
        removeEffect(roughnessEffectId);
        LibC.INSTANCE.close(fd);
        fd = -1;
        springEffectId = -1;
        roughnessEffectId = -1;
        lastSpringStrength = -1;
        lastRoughnessStrength = -1;
        roughnessSupported = true;
    }

    private void removeEffect(short id) {
        if (id < 0) {
            return;
        }
        playEffect(id, false);
        LibC.INSTANCE.ioctl(fd, EVIOCRMFF, id);
    }

    private boolean open(String configuredDevice) {
        Path device = configuredDevice.isBlank() ? findEventJoystick() : Path.of(configuredDevice);
        if (device == null) {
            WheelboatClient.LOGGER.warn("Could not find a Linux event joystick for force feedback");
            return false;
        }

        fd = LibC.INSTANCE.open(device.toString(), O_RDWR | O_NONBLOCK);
        if (fd < 0) {
            WheelboatClient.LOGGER.warn("Could not open force-feedback device {} (errno {})", device, Native.getLastError());
            return false;
        }
        WheelboatClient.LOGGER.info("Using force-feedback device {}", device);
        return true;
    }

    private boolean uploadSpringEffect(int strength) {
        Memory effect = new Memory(EFFECT_SIZE);
        effect.clear();
        effect.setShort(0, (short) FF_SPRING);
        effect.setShort(2, springEffectId);
        effect.setShort(10, (short) 0xFFFF);

        setSpringCondition(effect, CONDITION_OFFSET, strength);
        setSpringCondition(effect, CONDITION_OFFSET + CONDITION_SIZE, strength);

        if (LibC.INSTANCE.ioctl(fd, EVIOCSFF, effect) < 0) {
            return false;
        }
        springEffectId = effect.getShort(2);
        return true;
    }

    private boolean uploadRumbleEffect(int strength) {
        Memory effect = new Memory(EFFECT_SIZE);
        effect.clear();
        effect.setShort(0, (short) FF_RUMBLE);
        effect.setShort(2, roughnessEffectId);
        effect.setShort(10, (short) 0xFFFF);
        effect.setShort(16, (short) strength);
        effect.setShort(18, (short) Math.round(strength * 0.55F));

        if (LibC.INSTANCE.ioctl(fd, EVIOCSFF, effect) < 0) {
            return false;
        }
        roughnessEffectId = effect.getShort(2);
        return true;
    }

    private static void setSpringCondition(Memory effect, int offset, int strength) {
        effect.setShort(offset, (short) 0xFFFF);
        effect.setShort(offset + 2, (short) 0xFFFF);
        effect.setShort(offset + 4, (short) strength);
        effect.setShort(offset + 6, (short) strength);
    }

    private boolean playEffect(short id, boolean enabled) {
        Memory event = new Memory(INPUT_EVENT_SIZE);
        event.clear();
        event.setShort(16, (short) EV_FF);
        event.setShort(18, id);
        event.setInt(20, enabled ? 1 : 0);
        return LibC.INSTANCE.write(fd, event, INPUT_EVENT_SIZE) == INPUT_EVENT_SIZE;
    }

    private static Path findEventJoystick() {
        try (Stream<Path> paths = Files.list(Path.of("/dev/input/by-id"))) {
            return paths.filter(path -> path.getFileName().toString().endsWith("-event-joystick")).sorted(Comparator.comparing(Path::toString)).findFirst().orElse(null);
        } catch (IOException exception) {
            return null;
        }
    }

    private static int scale(float strength) {
        return Math.round(Math.clamp(strength, 0.0F, 1.0F) * Short.MAX_VALUE);
    }

    private interface LibC extends Library {
        LibC INSTANCE = Native.load("c", LibC.class);

        int open(String path, int flags);

        int ioctl(int fd, long request, Pointer argument);

        int ioctl(int fd, long request, int argument);

        long write(int fd, Pointer buffer, long count);

        int close(int fd);
    }
}
