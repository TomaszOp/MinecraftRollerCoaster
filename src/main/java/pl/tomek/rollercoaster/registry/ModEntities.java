package pl.tomek.rollercoaster.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import pl.tomek.rollercoaster.RollerCoasterMod;
import pl.tomek.rollercoaster.entity.CoasterCartEntity;

public final class ModEntities {
	public static final ResourceKey<EntityType<?>> COASTER_CART_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, RollerCoasterMod.id("coaster_cart"));

	public static final EntityType<CoasterCartEntity> COASTER_CART = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			COASTER_CART_KEY,
			EntityType.Builder.<CoasterCartEntity>of(CoasterCartEntity::new, MobCategory.MISC)
					.sized(0.98f, 0.7f)
					.clientTrackingRange(10)
					.updateInterval(1) // pozycja wysyłana co tick -> płynna jazda
					.build(COASTER_CART_KEY));

	private ModEntities() {
	}

	public static void init() {
	}
}
