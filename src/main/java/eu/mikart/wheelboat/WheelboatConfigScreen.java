package eu.mikart.wheelboat;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class WheelboatConfigScreen {
    private WheelboatConfigScreen() {
    }

    static Screen create(Screen parent) {
        WheelboatConfig config = WheelboatConfig.current();
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.literal("Wheelboat"))
            .setSavingRunnable(config::applyChanges);
        ConfigEntryBuilder entries = builder.entryBuilder();

        addGeneral(builder.getOrCreateCategory(Component.literal("General")), entries, config);
        addAxes(builder.getOrCreateCategory(Component.literal("Axes")), entries, config);
        addPedals(builder.getOrCreateCategory(Component.literal("Pedals")), entries, config);
        addForceFeedback(builder.getOrCreateCategory(Component.literal("Force Feedback")), entries, config);

        return builder.build();
    }

    private static void addGeneral(ConfigCategory category, ConfigEntryBuilder entries, WheelboatConfig config) {
        category.addEntry(entries.startBooleanToggle(Component.literal("Enabled"), config.enabled)
            .setDefaultValue(true)
            .setSaveConsumer(value -> config.enabled = value)
            .build());
        category.addEntry(entries.startIntField(Component.literal("Joystick ID"), config.joystickId)
            .setDefaultValue(-1)
            .setMin(-1)
            .setSaveConsumer(value -> config.joystickId = value)
            .build());
        category.addEntry(entries.startStrField(Component.literal("Device name contains"), config.deviceNameContains)
            .setDefaultValue("")
            .setSaveConsumer(value -> config.deviceNameContains = value)
            .build());
        category.addEntry(entries.startBooleanToggle(Component.literal("Analog input"), config.analogInput)
            .setDefaultValue(true)
            .setSaveConsumer(value -> config.analogInput = value)
            .build());
        category.addEntry(entries.startBooleanToggle(Component.literal("Log axes"), config.logAxes)
            .setDefaultValue(false)
            .setSaveConsumer(value -> config.logAxes = value)
            .build());
    }

    private static void addAxes(ConfigCategory category, ConfigEntryBuilder entries, WheelboatConfig config) {
        category.addEntry(entries.startIntField(Component.literal("Steering axis"), config.steeringAxis)
            .setDefaultValue(0)
            .setMin(0)
            .setSaveConsumer(value -> config.steeringAxis = value)
            .build());
        category.addEntry(entries.startBooleanToggle(Component.literal("Auto-center steering"), config.autoCenterSteering)
            .setDefaultValue(true)
            .setSaveConsumer(value -> config.autoCenterSteering = value)
            .build());
        category.addEntry(floatEntry(entries, "Steering minimum", config.steeringMinimum, -1.0F, -1.0F, 1.0F, value -> config.steeringMinimum = value));
        category.addEntry(floatEntry(entries, "Steering center", config.steeringCenter, 0.0F, -1.0F, 1.0F, value -> config.steeringCenter = value));
        category.addEntry(floatEntry(entries, "Steering maximum", config.steeringMaximum, 1.0F, -1.0F, 1.0F, value -> config.steeringMaximum = value));
        category.addEntry(entries.startBooleanToggle(Component.literal("Invert steering"), config.invertSteering)
            .setDefaultValue(false)
            .setSaveConsumer(value -> config.invertSteering = value)
            .build());
        category.addEntry(floatEntry(entries, "Steering deadzone", config.steeringDeadzone, 0.02F, 0.0F, 1.0F, value -> config.steeringDeadzone = value));
    }

    private static void addPedals(ConfigCategory category, ConfigEntryBuilder entries, WheelboatConfig config) {
        category.addEntry(entries.startBooleanToggle(Component.literal("Combined pedals"), config.combinedPedals)
            .setDefaultValue(false)
            .setSaveConsumer(value -> config.combinedPedals = value)
            .build());
        category.addEntry(entries.startBooleanToggle(Component.literal("Invert combined pedals"), config.invertCombinedPedals)
            .setDefaultValue(false)
            .setSaveConsumer(value -> config.invertCombinedPedals = value)
            .build());
        category.addEntry(entries.startBooleanToggle(Component.literal("Invert accelerator"), config.invertAccelerator)
            .setDefaultValue(true)
            .setSaveConsumer(value -> config.invertAccelerator = value)
            .build());
        category.addEntry(entries.startBooleanToggle(Component.literal("Invert brake"), config.invertBrake)
            .setDefaultValue(false)
            .setSaveConsumer(value -> config.invertBrake = value)
            .build());
        category.addEntry(floatEntry(entries, "Combined accelerator value", config.combinedPedalsAcceleratorValue, -1.0F, -1.0F, 1.0F, value -> config.combinedPedalsAcceleratorValue = value));
        category.addEntry(floatEntry(entries, "Combined resting value", config.combinedPedalsRestingValue, 0.0F, -1.0F, 1.0F, value -> config.combinedPedalsRestingValue = value));
        category.addEntry(floatEntry(entries, "Combined brake value", config.combinedPedalsBrakeValue, 1.0F, -1.0F, 1.0F, value -> config.combinedPedalsBrakeValue = value));
        category.addEntry(entries.startIntField(Component.literal("Accelerator axis"), config.acceleratorAxis)
            .setDefaultValue(2)
            .setMin(0)
            .setSaveConsumer(value -> config.acceleratorAxis = value)
            .build());
        category.addEntry(entries.startIntField(Component.literal("Brake axis"), config.brakeAxis)
            .setDefaultValue(3)
            .setMin(0)
            .setSaveConsumer(value -> config.brakeAxis = value)
            .build());
        category.addEntry(floatEntry(entries, "Accelerator threshold", config.acceleratorThreshold, 0.1F, 0.0F, 1.0F, value -> config.acceleratorThreshold = value));
        category.addEntry(floatEntry(entries, "Brake threshold", config.brakeThreshold, 0.1F, 0.0F, 1.0F, value -> config.brakeThreshold = value));
    }

    private static void addForceFeedback(ConfigCategory category, ConfigEntryBuilder entries, WheelboatConfig config) {
        category.addEntry(entries.startBooleanToggle(Component.literal("Enabled"), config.forceFeedback)
            .setDefaultValue(true)
            .setSaveConsumer(value -> config.forceFeedback = value)
            .build());
        category.addEntry(entries.startStrField(Component.literal("Force-feedback device"), config.forceFeedbackDevice)
            .setDefaultValue("")
            .setSaveConsumer(value -> config.forceFeedbackDevice = value)
            .build());
        category.addEntry(floatEntry(entries, "Global strength", config.forceFeedbackStrength, 0.25F, 0.0F, 1.0F, value -> config.forceFeedbackStrength = value));

        addMaterialProfile(category, entries, "Water", config.waterForceFeedback, 1.0F, 0.04F);
        addMaterialProfile(category, entries, "Ice", config.iceForceFeedback, 0.55F, 0.01F);
        addMaterialProfile(category, entries, "Sand", config.sandForceFeedback, 1.2F, 0.45F);
        addMaterialProfile(category, entries, "Dirt", config.dirtForceFeedback, 1.0F, 0.5F);
        addMaterialProfile(category, entries, "Stone", config.stoneForceFeedback, 1.35F, 0.35F);
        addMaterialProfile(category, entries, "Wood", config.woodForceFeedback, 0.9F, 0.18F);
        addMaterialProfile(category, entries, "Default", config.defaultForceFeedback, 1.0F, 0.15F);
    }

    private static void addMaterialProfile(ConfigCategory category, ConfigEntryBuilder entries, String name, MaterialForceFeedback profile, float defaultSpring, float defaultRoughness) {
        SubCategoryBuilder subCategory = entries.startSubCategory(Component.literal(name)).setExpanded(false);
        subCategory.add(floatEntry(entries, "Spring multiplier", profile.springMultiplier, defaultSpring, 0.0F, 2.0F, value -> profile.springMultiplier = value));
        subCategory.add(floatEntry(entries, "Roughness", profile.roughness, defaultRoughness, 0.0F, 1.0F, value -> profile.roughness = value));
        category.addEntry(subCategory.build());
    }

    private static AbstractConfigListEntry floatEntry(ConfigEntryBuilder entries, String title, float value, float defaultValue, float min, float max, java.util.function.Consumer<Float> save) {
        return entries.startFloatField(Component.literal(title), value)
            .setDefaultValue(defaultValue)
            .setMin(min)
            .setMax(max)
            .setSaveConsumer(save)
            .build();
    }

}
