package pl.tomek.rollercoaster;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pl.tomek.rollercoaster.entity.CoasterCartEntity;
import pl.tomek.rollercoaster.registry.ModBlocks;
import pl.tomek.rollercoaster.registry.ModEntities;
import pl.tomek.rollercoaster.registry.ModItems;

/**
 * Mod "Roller Coaster": uniwersalne tory (podłoga, ściana, sufit)
 * i wagonik, który jeździ pionowo oraz do góry nogami.
 */
public class RollerCoasterMod implements ModInitializer {
	public static final String MOD_ID = "rollercoaster";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModBlocks.init();
		ModItems.init();
		ModEntities.init();

		// Wsiadanie do wagonika prawym przyciskiem myszy.
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (entity instanceof CoasterCartEntity cart) {
				if (player.isSecondaryUseActive() || cart.isVehicle()) {
					return InteractionResult.PASS;
				}
				if (!level.isClientSide()) {
					player.startRiding(cart, false, true);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});

		LOGGER.info("Roller Coaster: tory i wagonik zaladowane");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
