package pl.tomek.rollercoaster.registry;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

import pl.tomek.rollercoaster.RollerCoasterMod;
import pl.tomek.rollercoaster.item.CoasterCartItem;
import pl.tomek.rollercoaster.item.CoasterTrackItem;

public final class ModItems {
	public static final ResourceKey<Item> COASTER_TRACK_KEY =
			ResourceKey.create(Registries.ITEM, RollerCoasterMod.id("coaster_track"));
	public static final ResourceKey<Item> COASTER_CART_KEY =
			ResourceKey.create(Registries.ITEM, RollerCoasterMod.id("coaster_cart"));

	public static final Item COASTER_TRACK = Registry.register(
			BuiltInRegistries.ITEM,
			COASTER_TRACK_KEY,
			new CoasterTrackItem(ModBlocks.COASTER_TRACK, new Item.Properties().setId(COASTER_TRACK_KEY).useBlockDescriptionPrefix()));

	public static final Item COASTER_CART = Registry.register(
			BuiltInRegistries.ITEM,
			COASTER_CART_KEY,
			new CoasterCartItem(new Item.Properties().setId(COASTER_CART_KEY).stacksTo(1)));

	private ModItems() {
	}

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(content -> {
			content.accept(COASTER_TRACK);
			content.accept(COASTER_CART);
		});
	}
}
