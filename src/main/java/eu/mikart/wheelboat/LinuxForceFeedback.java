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

final class LinuxForceFeedback {
    private static final int O_RDWR = 2;
    private static final int O_NONBLOCK = 2048;
    private static final int EV_FF = 0x15;
    private static final int FF_SPRING = 0x53;
    private static final long EVIOCSFF = 0x40304580L;
    private static final long EVIOCRMFF = 0x40044581L;
    private static final int EFFECT_SIZE = 48;
    private static final int INPUT_EVENT_SIZE = 24;
    private static final int CONDITION_SIZE = 12;
    private static final int CONDITION_OFFSET = 16;
    private static final long RETRY_DELAY_MILLIS = 5000L;

    private int fd = -1;
    private short effectId = -1;
    private int lastStrength = -1;
    private long nextOpenAttempt;

    void update(boolean enabled, String configuredDevice, float strength) {
        if (!enabled || !isLinux()) {
            close();
            nextOpenAttempt = 0L;
            return;
        }

        if (fd < 0) {
            long now = System.currentTimeMillis();
            if (now < nextOpenAttempt) {
                return;
            }
            if (!open(configuredDevice)) {
                nextOpenAttempt = now + RETRY_DELAY_MILLIS;
                return;
            }
            nextOpenAttempt = 0L;
        }

        int scaledStrength = Math.round(Math.clamp(strength, 0.0F, 1.0F) * Short.MAX_VALUE);
        if (scaledStrength == lastStrength) {
            return;
        }

        if (!uploadSpringEffect(scaledStrength)) {
            WheelboatClient.LOGGER.warn("Could not upload steering wheel spring effect (errno {})", Native.getLastError());
            close();
            nextOpenAttempt = System.currentTimeMillis() + RETRY_DELAY_MILLIS;
            return;
        }

        if (!playEffect(effectId, scaledStrength > 0)) {
            WheelboatClient.LOGGER.warn("Could not start steering wheel spring effect (errno {})", Native.getLastError());
            close();
            nextOpenAttempt = System.currentTimeMillis() + RETRY_DELAY_MILLIS;
            return;
        }
        lastStrength = scaledStrength;
    }

    void close() {
        if (fd < 0) {
            return;
        }

        if (effectId >= 0) {
            playEffect(effectId, false);
            LibC.INSTANCE.ioctl(fd, EVIOCRMFF, effectId);
        }
        LibC.INSTANCE.close(fd);
        fd = -1;
        effectId = -1;
        lastStrength = -1;
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
        effect.setShort(2, effectId);
        effect.setShort(10, (short) 0xFFFF);

        setSpringCondition(effect, CONDITION_OFFSET, strength);
        setSpringCondition(effect, CONDITION_OFFSET + CONDITION_SIZE, strength);

        if (LibC.INSTANCE.ioctl(fd, EVIOCSFF, effect) < 0) {
            return false;
        }
        effectId = effect.getShort(2);
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

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
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
