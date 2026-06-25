package eu.mikart.wheelboat;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef.HINSTANCE;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import net.minecraft.client.Minecraft;

import java.util.Locale;

final class WindowsForceFeedback implements ForceFeedbackDevice {
    private static final int DIRECT_INPUT_VERSION = 0x0800;
    private static final int DEVICE_SCAN_INTERVAL_MILLIS = 5000;

    private static final int DI8DEVCLASS_GAMECTRL = 4;
    private static final int DIEDFL_ATTACHEDONLY = 0x00000001;
    private static final int DIEDFL_FORCEFEEDBACK = 0x00000100;
    private static final int DISCL_NONEXCLUSIVE = 0x00000002;
    private static final int DISCL_BACKGROUND = 0x00000008;
    private static final int DIPH_DEVICE = 0;
    private static final int DIPROP_AUTOCENTER = 0x00000002;
    private static final int DIPROPAUTOCENTER_OFF = 0;
    private static final int DIJOFS_X = 0;

    private static final int DIEFF_OBJECTOFFSETS = 0x00000002;
    private static final int DIEFF_CARTESIAN = 0x00000010;
    private static final int DIEP_ALLPARAMS = 0x000003FF;
    private static final int DIEP_TYPESPECIFICPARAMS = 0x00000100;
    private static final int DIEP_START = 0x20000000;
    private static final int INFINITE_DURATION = -1;

    private static final int VTABLE_RELEASE = 2;
    private static final int VTABLE_CREATE_DEVICE = 3;
    private static final int VTABLE_ENUM_DEVICES = 4;
    private static final int VTABLE_SET_PROPERTY = 6;
    private static final int VTABLE_ACQUIRE = 7;
    private static final int VTABLE_UNACQUIRE = 8;
    private static final int VTABLE_SET_DATA_FORMAT = 11;
    private static final int VTABLE_SET_COOPERATIVE_LEVEL = 12;
    private static final int VTABLE_CREATE_EFFECT = 18;
    private static final int VTABLE_EFFECT_SET_PARAMETERS = 6;
    private static final int VTABLE_EFFECT_START = 7;
    private static final int VTABLE_EFFECT_STOP = 8;

    private static final GUID IID_IDIRECT_INPUT_8W = guid("BF798031-483A-4DA2-AA99-5D64ED369700");
    private static final GUID GUID_SPRING = guid("13541C28-8E33-11D0-9AD0-00A0C9A06E35");
    private static final GUID GUID_SINE = guid("13541C23-8E33-11D0-9AD0-00A0C9A06E35");

    private Pointer directInput;
    private Pointer device;
    private Pointer springEffect;
    private Pointer roughnessEffect;
    private int lastSpring = -1;
    private int lastRoughness = -1;
    private long nextOpenAttempt;
    private boolean roughnessSupported = true;

    @Override
    public void update(ForceFeedbackRequest request) {
        if (!request.enabled()) {
            close();
            nextOpenAttempt = 0L;
            return;
        }

        if (device == null && !open(request.device())) {
            return;
        }

        int spring = scale(request.springStrength());
        int roughness = scale(request.roughnessStrength());
        if (spring == lastSpring && roughness == lastRoughness) {
            return;
        }

        if (!updateSpring(spring)) {
            closeAndDelayRetry();
            return;
        }
        updateRoughness(roughness);
        lastSpring = spring;
        lastRoughness = roughness;
    }

    @Override
    public void close() {
        stopAndReleaseEffect(springEffect);
        stopAndReleaseEffect(roughnessEffect);
        if (device != null) {
            invoke(device, VTABLE_UNACQUIRE);
            invoke(device, VTABLE_RELEASE);
        }
        if (directInput != null) {
            invoke(directInput, VTABLE_RELEASE);
        }
        directInput = null;
        device = null;
        springEffect = null;
        roughnessEffect = null;
        lastSpring = -1;
        lastRoughness = -1;
        roughnessSupported = true;
    }

    private boolean open(String configuredDevice) {
        long now = System.currentTimeMillis();
        if (now < nextOpenAttempt) {
            return false;
        }

        try {
            if (!createDirectInput()) {
                nextOpenAttempt = now + DEVICE_SCAN_INTERVAL_MILLIS;
                return false;
            }

            DeviceCandidate candidate = findForceFeedbackDevice(configuredDevice);
            if (candidate == null || !createDevice(candidate)) {
                closeAndDelayRetry();
                return false;
            }

            WheelboatClient.LOGGER.info("Using Windows force-feedback device {}", candidate.name);
            nextOpenAttempt = 0L;
            return true;
        } catch (RuntimeException exception) {
            WheelboatClient.LOGGER.warn("Could not initialize Windows force feedback", exception);
            closeAndDelayRetry();
            return false;
        }
    }

    private boolean createDirectInput() {
        PointerByReference directInputRef = new PointerByReference();
        HINSTANCE instance = new HINSTANCE();
        instance.setPointer(Kernel32.INSTANCE.GetModuleHandle(null).getPointer());
        int result = DInput8.INSTANCE.DirectInput8Create(instance, DIRECT_INPUT_VERSION, IID_IDIRECT_INPUT_8W, directInputRef, Pointer.NULL);
        if (failed(result)) {
            WheelboatClient.LOGGER.warn("DirectInput8Create failed for force feedback (HRESULT 0x{})", Integer.toHexString(result));
            return false;
        }
        directInput = directInputRef.getValue();
        return directInput != null;
    }

    private DeviceCandidate findForceFeedbackDevice(String configuredDevice) {
        DeviceCandidate[] selected = new DeviceCandidate[1];
        EnumDevicesCallback callback = deviceInstance -> {
            DeviceCandidate candidate = DeviceCandidate.read(deviceInstance);
            if (candidate.matches(configuredDevice)) {
                selected[0] = candidate;
                return false;
            }
            return true;
        };

        int result = invoke(directInput, VTABLE_ENUM_DEVICES, DI8DEVCLASS_GAMECTRL, callback, Pointer.NULL, DIEDFL_ATTACHEDONLY | DIEDFL_FORCEFEEDBACK);
        if (failed(result)) {
            WheelboatClient.LOGGER.warn("DirectInput device enumeration failed (HRESULT 0x{})", Integer.toHexString(result));
            return null;
        }
        if (selected[0] == null) {
            WheelboatClient.LOGGER.warn("Could not find an attached Windows force-feedback controller");
        }
        return selected[0];
    }

    private boolean createDevice(DeviceCandidate candidate) {
        PointerByReference deviceRef = new PointerByReference();
        int result = invoke(directInput, VTABLE_CREATE_DEVICE, candidate.instanceGuid, deviceRef, Pointer.NULL);
        if (failed(result)) {
            WheelboatClient.LOGGER.warn("Could not create DirectInput device {} (HRESULT 0x{})", candidate.name, Integer.toHexString(result));
            return false;
        }
        device = deviceRef.getValue();

        setDataFormatIfAvailable();
        setCooperativeLevel();
        disableDeviceAutocenter();

        result = invoke(device, VTABLE_ACQUIRE);
        if (failed(result)) {
            WheelboatClient.LOGGER.warn("Could not acquire DirectInput device {} (HRESULT 0x{})", candidate.name, Integer.toHexString(result));
            return false;
        }
        return true;
    }

    private void setDataFormatIfAvailable() {
        try {
            Pointer dataFormat = NativeLibrary.getInstance("dinput8").getGlobalVariableAddress("c_dfDIJoystick2");
            int result = invoke(device, VTABLE_SET_DATA_FORMAT, dataFormat);
            if (failed(result)) {
                WheelboatClient.LOGGER.warn("Could not set DirectInput joystick data format (HRESULT 0x{})", Integer.toHexString(result));
            }
        } catch (UnsatisfiedLinkError exception) {
            WheelboatClient.LOGGER.warn("DirectInput joystick data format export was not available");
        }
    }

    private void setCooperativeLevel() {
        HWND window = minecraftWindowHandle();
        if (window == null) {
            return;
        }

        int result = invoke(device, VTABLE_SET_COOPERATIVE_LEVEL, window, DISCL_NONEXCLUSIVE | DISCL_BACKGROUND);
        if (failed(result)) {
            WheelboatClient.LOGGER.warn("Could not set DirectInput cooperative level (HRESULT 0x{})", Integer.toHexString(result));
        }
    }

    private void disableDeviceAutocenter() {
        Memory property = new Memory(24);
        property.clear();
        property.setInt(0, 24);
        property.setInt(4, 16);
        property.setInt(8, 0);
        property.setInt(12, DIPH_DEVICE);
        property.setInt(16, DIPROPAUTOCENTER_OFF);
        int result = invoke(device, VTABLE_SET_PROPERTY, propertyGuid(DIPROP_AUTOCENTER), property);
        if (failed(result)) {
            WheelboatClient.LOGGER.debug("Could not disable DirectInput device autocenter (HRESULT 0x{})", Integer.toHexString(result));
        }
    }

    private boolean updateSpring(int strength) {
        if (springEffect == null && !createSpringEffect(strength)) {
            return false;
        }
        Memory condition = condition(strength);
        Memory effect = effect(condition, condition.size());
        int result = invoke(springEffect, VTABLE_EFFECT_SET_PARAMETERS, effect, DIEP_TYPESPECIFICPARAMS | DIEP_START);
        if (failed(result)) {
            WheelboatClient.LOGGER.warn("Could not update DirectInput spring effect (HRESULT 0x{})", Integer.toHexString(result));
            return false;
        }
        return true;
    }

    private boolean createSpringEffect(int strength) {
        Memory condition = condition(strength);
        Memory effect = effect(condition, condition.size());
        PointerByReference effectRef = new PointerByReference();
        int result = invoke(device, VTABLE_CREATE_EFFECT, GUID_SPRING, effect, effectRef, Pointer.NULL);
        if (failed(result)) {
            WheelboatClient.LOGGER.warn("Could not create DirectInput spring effect (HRESULT 0x{})", Integer.toHexString(result));
            return false;
        }
        springEffect = effectRef.getValue();
        invoke(springEffect, VTABLE_EFFECT_START, 1, 0);
        return true;
    }

    private void updateRoughness(int strength) {
        if (!roughnessSupported) {
            return;
        }
        if (roughnessEffect == null && !createRoughnessEffect(strength)) {
            roughnessSupported = false;
            return;
        }

        Memory periodic = periodic(strength);
        Memory effect = effect(periodic, periodic.size());
        int result = invoke(roughnessEffect, VTABLE_EFFECT_SET_PARAMETERS, effect, DIEP_TYPESPECIFICPARAMS | DIEP_START);
        if (failed(result)) {
            WheelboatClient.LOGGER.warn("Could not update DirectInput roughness effect (HRESULT 0x{})", Integer.toHexString(result));
            roughnessSupported = false;
        }
    }

    private boolean createRoughnessEffect(int strength) {
        Memory periodic = periodic(strength);
        Memory effect = effect(periodic, periodic.size());
        PointerByReference effectRef = new PointerByReference();
        int result = invoke(device, VTABLE_CREATE_EFFECT, GUID_SINE, effect, effectRef, Pointer.NULL);
        if (failed(result)) {
            WheelboatClient.LOGGER.warn("Could not create DirectInput roughness effect (HRESULT 0x{})", Integer.toHexString(result));
            return false;
        }
        roughnessEffect = effectRef.getValue();
        invoke(roughnessEffect, VTABLE_EFFECT_START, 1, 0);
        return true;
    }

    private static Memory effect(Memory typeSpecificParams, long typeSpecificSize) {
        Memory axes = new Memory(4);
        axes.setInt(0, DIJOFS_X);

        Memory direction = new Memory(4);
        direction.setInt(0, 0);

        int structSize = dieffectSize();
        Memory effect = new Memory(structSize);
        effect.clear();
        int offset = 0;
        effect.setInt(offset, structSize);
        offset += 4;
        effect.setInt(offset, DIEFF_OBJECTOFFSETS | DIEFF_CARTESIAN);
        offset += 4;
        effect.setInt(offset, INFINITE_DURATION);
        offset += 4;
        effect.setInt(offset, 0);
        offset += 4;
        effect.setInt(offset, 10_000);
        offset += 4;
        effect.setInt(offset, -1);
        offset += 4;
        effect.setInt(offset, 0);
        offset += 4;
        effect.setInt(offset, 1);
        offset += pointerPadding(offset);
        effect.setPointer(offset, axes);
        offset += Native.POINTER_SIZE;
        effect.setPointer(offset, direction);
        offset += Native.POINTER_SIZE;
        effect.setPointer(offset, Pointer.NULL);
        offset += Native.POINTER_SIZE;
        effect.setInt(offset, (int) typeSpecificSize);
        offset += 4;
        offset += pointerPadding(offset);
        effect.setPointer(offset, typeSpecificParams);
        offset += Native.POINTER_SIZE;
        effect.setInt(offset, 0);
        return effect;
    }

    private static int dieffectSize() {
        int offset = 8 * 4;
        offset += Native.POINTER_SIZE * 3;
        offset += 4;
        offset += pointerPadding(offset);
        offset += Native.POINTER_SIZE;
        offset += 4;
        return align(offset, Native.POINTER_SIZE);
    }

    private static int align(int offset, int alignment) {
        int remainder = offset % alignment;
        return remainder == 0 ? offset : offset + alignment - remainder;
    }

    private static Memory condition(int strength) {
        Memory condition = new Memory(24);
        condition.clear();
        condition.setInt(0, 0);
        condition.setInt(4, strength);
        condition.setInt(8, strength);
        condition.setInt(12, 10_000);
        condition.setInt(16, 10_000);
        condition.setInt(20, 0);
        return condition;
    }

    private static Memory periodic(int strength) {
        Memory periodic = new Memory(16);
        periodic.clear();
        periodic.setInt(0, strength);
        periodic.setInt(4, 0);
        periodic.setInt(8, 0);
        periodic.setInt(12, 35_000);
        return periodic;
    }

    private static int pointerPadding(int offset) {
        int remainder = offset % Native.POINTER_SIZE;
        return remainder == 0 ? 0 : Native.POINTER_SIZE - remainder;
    }

    private static int scale(float strength) {
        return Math.round(Math.clamp(strength, 0.0F, 1.0F) * 10_000.0F);
    }

    private static boolean failed(int result) {
        return result < 0;
    }

    private static int invoke(Pointer object, int methodIndex, Object... args) {
        Object[] callArgs = new Object[args.length + 1];
        callArgs[0] = object;
        System.arraycopy(args, 0, callArgs, 1, args.length);
        Pointer vtable = object.getPointer(0);
        Pointer functionPointer = vtable.getPointer((long) methodIndex * Native.POINTER_SIZE);
        return Function.getFunction(functionPointer, Function.ALT_CONVENTION).invokeInt(callArgs);
    }

    private static void stopAndReleaseEffect(Pointer effect) {
        if (effect == null) {
            return;
        }
        invoke(effect, VTABLE_EFFECT_STOP);
        invoke(effect, VTABLE_RELEASE);
    }

    private void closeAndDelayRetry() {
        close();
        nextOpenAttempt = System.currentTimeMillis() + DEVICE_SCAN_INTERVAL_MILLIS;
    }

    private static HWND minecraftWindowHandle() {
        try {
            long glfwWindow = glfwWindowHandle();
            long nativeWindow = (long) Class.forName("org.lwjgl.glfw.GLFWNativeWin32")
                .getMethod("glfwGetWin32Window", long.class)
                .invoke(null, glfwWindow);
            return nativeWindow == 0L ? null : new HWND(Pointer.createConstant(nativeWindow));
        } catch (ReflectiveOperationException | LinkageError exception) {
            WheelboatClient.LOGGER.warn("Could not get Win32 window handle for DirectInput force feedback", exception);
            return null;
        }
    }

    private static long glfwWindowHandle() throws ReflectiveOperationException {
        Object window = Minecraft.getInstance().getWindow();
        for (String method : new String[]{"getWindow", "getHandle", "handle"}) {
            try {
                Object value = window.getClass().getMethod(method).invoke(window);
                if (value instanceof Number number) {
                    return number.longValue();
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException("Could not find Minecraft Window handle accessor");
    }

    private static GUID guid(String value) {
        return GUID.fromString("{" + value + "}");
    }

    private static GUID propertyGuid(int propertyId) {
        GUID guid = new GUID();
        guid.Data1 = propertyId;
        guid.Data2 = 0;
        guid.Data3 = 0;
        guid.Data4 = new byte[8];
        return guid;
    }

    private interface DInput8 extends StdCallLibrary {
        DInput8 INSTANCE = Native.load("dinput8", DInput8.class);

        int DirectInput8Create(HINSTANCE instance, int version, GUID interfaceId, PointerByReference directInput, Pointer outerUnknown);
    }

    private interface EnumDevicesCallback extends StdCallLibrary.StdCallCallback {
        boolean invoke(Pointer deviceInstance);
    }

    private record DeviceCandidate(Memory instanceGuid, String name) {
        private static final int GUID_INSTANCE_OFFSET = 4;
        private static final int INSTANCE_NAME_OFFSET = 40;
        private static final int PRODUCT_NAME_OFFSET = 560;
        private static final int GUID_SIZE = 16;

        static DeviceCandidate read(Pointer deviceInstance) {
            Memory guid = new Memory(GUID_SIZE);
            guid.write(0, deviceInstance.getByteArray(GUID_INSTANCE_OFFSET, GUID_SIZE), 0, GUID_SIZE);

            String productName = deviceInstance.getWideString(PRODUCT_NAME_OFFSET);
            String instanceName = deviceInstance.getWideString(INSTANCE_NAME_OFFSET);
            String name = productName == null || productName.isBlank() ? instanceName : productName;
            return new DeviceCandidate(guid, name == null ? "Unknown controller" : name);
        }

        boolean matches(String configuredDevice) {
            return configuredDevice == null
                || configuredDevice.isBlank()
                || name.toLowerCase(Locale.ROOT).contains(configuredDevice.toLowerCase(Locale.ROOT));
        }
    }
}
