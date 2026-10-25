package pl.tomek.rollercoaster.client;

import net.minecraft.client.Minecraft;

import pl.tomek.rollercoaster.entity.CoasterCartEntity;

/**
 * Płynny obrót kamery (roll) dla pasażera wagonika.
 * Na suficie kamera obraca się o 180° - widzisz świat do góry nogami.
 */
public final class CameraRoll {
	private static float rollDeg = 0f;
	private static long lastNanos = 0L;

	private CameraRoll() {
	}

	/** Zwraca aktualny dodatkowy obrót kamery w radianach. */
	public static float currentRollRadians() {
		long now = System.nanoTime();
		float dt = lastNanos == 0L ? 0f : Math.min((now - lastNanos) / 1.0e9f, 0.25f);
		lastNanos = now;

		float target = targetRollDeg();
		float k = 1f - (float) Math.exp(-dt * 6.0);
		rollDeg += (target - rollDeg) * k;
		if (Math.abs(rollDeg) < 0.01f && target == 0f) {
			rollDeg = 0f;
		}
		return (float) Math.toRadians(rollDeg);
	}

	private static float targetRollDeg() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && mc.player.getVehicle() instanceof CoasterCartEntity cart && cart.isUpsideDown()) {
			return 180f;
		}
		return 0f;
	}
}
