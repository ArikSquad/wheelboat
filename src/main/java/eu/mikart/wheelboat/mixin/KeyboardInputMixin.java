package eu.mikart.wheelboat.mixin;

import eu.mikart.wheelboat.WheelController;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
abstract class KeyboardInputMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void wheelboat$applyPhysicalWheel(CallbackInfo ci) {
        KeyboardInput input = (KeyboardInput) (Object) this;
        input.keyPresses = WheelController.enrichInput(input.keyPresses);
    }
}
