package pl.tomek.rollercoaster.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import pl.tomek.rollercoaster.block.TrackGeometry;
import pl.tomek.rollercoaster.entity.CoasterCartEntity;
import pl.tomek.rollercoaster.registry.ModBlocks;
import pl.tomek.rollercoaster.registry.ModEntities;

/** Przedmiot "Wagonik kolejki" - kliknij nim tor, aby postawić wagonik. */
public class CoasterCartItem extends Item {
	public CoasterCartItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		Level level = ctx.getLevel();
		BlockPos pos = ctx.getClickedPos();
		BlockState state = level.getBlockState(pos);

		if (!state.is(ModBlocks.COASTER_TRACK)) {
			return InteractionResult.FAIL;
		}

		if (level instanceof ServerLevel serverLevel) {
			// Wagonik jedzie w stronę tego końca toru, w który patrzy gracz.
			Direction[] ends = TrackGeometry.ends(state);
			Direction exit = ends[0];
			Player player = ctx.getPlayer();
			if (player != null && dot(player.getLookAngle(), ends[1]) > dot(player.getLookAngle(), ends[0])) {
				exit = ends[1];
			}

			CoasterCartEntity cart = new CoasterCartEntity(ModEntities.COASTER_CART, level);
			cart.placeOnTrack(TrackGeometry.exitSegment(pos, state, exit));
			serverLevel.addFreshEntity(cart);

			if (player == null || !player.getAbilities().instabuild) {
				ctx.getItemInHand().shrink(1);
			}
		}

		return InteractionResult.SUCCESS;
	}

	private static double dot(Vec3 v, Direction d) {
		return v.x * d.getStepX() + v.y * d.getStepY() + v.z * d.getStepZ();
	}
}
