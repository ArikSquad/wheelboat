package eu.mikart.wheelboat.mixin;

import eu.mikart.wheelboat.WheelController;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(AbstractBoat.class)
abstract class AbstractBoatMixin {
    @ModifyConstant(method = "controlBoat", constant = @Constant(floatValue = 1.0F))
    private float wheelboat$scaleTurning(float original) {
        return original * WheelController.steeringAmount();
    }

    @ModifyConstant(method = "controlBoat", constant = @Constant(floatValue = 0.04F))
    private float wheelboat$scaleAcceleration(float original) {
        return original * WheelController.acceleratorAmount();
    }

    @ModifyConstant(method = "controlBoat", constant = @Constant(floatValue = 0.005F, ordinal = 0))
    private float wheelboat$scaleTurningDrift(float original) {
        return original * WheelController.steeringAmount();
    }

    @ModifyConstant(method = "controlBoat", constant = @Constant(floatValue = 0.005F, ordinal = 1))
    private float wheelboat$scaleReverse(float original) {
        return original * WheelController.brakeAmount();
    }
}
