package pl.tomek.rollercoaster.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import pl.tomek.rollercoaster.RollerCoasterMod;
import pl.tomek.rollercoaster.block.CoasterTrackBlock;

public final class ModBlocks {
	public static final ResourceKey<Block> COASTER_TRACK_KEY =
			ResourceKey.create(Registries.BLOCK, RollerCoasterMod.id("coaster_track"));

	public static final Block COASTER_TRACK = Registry.register(
			BuiltInRegistries.BLOCK,
			COASTER_TRACK_KEY,
			new CoasterTrackBlock(BlockBehaviour.Properties.of()
					.setId(COASTER_TRACK_KEY)
					.strength(0.7f)
					.sound(SoundType.METAL)
					.noOcclusion()));

	private ModBlocks() {
	}

	public static void init() {
		// Wywołanie wymusza załadowanie klasy i rejestrację pól statycznych.
	}
}
