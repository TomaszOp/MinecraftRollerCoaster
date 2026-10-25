package pl.tomek.rollercoaster.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.client.Camera;

import pl.tomek.rollercoaster.client.CameraRoll;

/**
 * Dodaje obrót (roll) do kamery. Kamera Minecrafta liczy orientację przez
 * Quaternionf.rotationYXZ(yaw, pitch, roll) - podmieniamy trzeci argument.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@ModifyArg(
			method = "setRotation",
			at = @At(value = "INVOKE", target = "Lorg/joml/Quaternionf;rotationYXZ(FFF)Lorg/joml/Quaternionf;"),
			index = 2,
			require = 0)
	private float rollercoaster$addRoll(float roll) {
		return roll + CameraRoll.currentRollRadians();
	}
}
