package pl.tomek.rollercoaster.item;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import pl.tomek.rollercoaster.block.CoasterTrackBlock;
import pl.tomek.rollercoaster.block.TrackGeometry;

/**
 * Przedmiot "Tor kolejki".
 *
 * <p>Zwykłe kliknięcie w blok kładzie tor na klikniętej ścianie bloku.
 * Kliknięcie w istniejący tor przedłuża go: nowy tor pojawia się za tym końcem
 * toru, w który patrzysz - także w górę ściany, przez krawędź bloku albo na
 * ścianę stojącą przed torem. Z wciśniętym Shiftem przedłużanie jest wyłączone.
 */
public class CoasterTrackItem extends BlockItem {
	public CoasterTrackItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public @Nullable BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
		if (context.replacingClickedOnBlock() || context.isSecondaryUseActive()) {
			return context;
		}

		Level level = context.getLevel();
		BlockPos hitPos = context.getClickedPos().relative(context.getClickedFace().getOpposite());
		BlockState hit = level.getBlockState(hitPos);
		if (!TrackGeometry.isTrack(hit) || TrackGeometry.isSlope(hit)) {
			return context;
		}

		Direction attach = hit.getValue(CoasterTrackBlock.ATTACH);
		Direction end = pickEnd(level, hitPos, hit, context.getPlayer());
		if (end == null) {
			return null; // oba końce toru są już połączone
		}

		BlockPos ahead = hitPos.relative(end);
		if (level.getBlockState(ahead).canBeReplaced()) {
			// Prosto, na tej samej powierzchni.
			if (fits(level, ahead, attach)) {
				return BlockPlaceContext.at(context, ahead, attach.getOpposite());
			}
			// Zakręt zewnętrzny: tor zawija się przez krawędź bloku.
			BlockPos outer = ahead.relative(attach);
			if (fits(level, outer, end.getOpposite())) {
				return BlockPlaceContext.at(context, outer, end);
			}
		} else {
			// Zakręt wewnętrzny: przed torem stoi ściana - tor wchodzi na nią.
			BlockPos inner = hitPos.relative(attach.getOpposite());
			if (fits(level, inner, end)) {
				return BlockPlaceContext.at(context, inner, end.getOpposite());
			}
		}
		return null;
	}

	/** Czy w bloku pos można położyć tor przyczepiony w stronę attach. */
	private boolean fits(Level level, BlockPos pos, Direction attach) {
		BlockState state = this.getBlock().defaultBlockState().setValue(CoasterTrackBlock.ATTACH, attach);
		return level.getBlockState(pos).canBeReplaced() && state.canSurvive(level, pos);
	}

	/** Wolny koniec toru najbliższy kierunkowi patrzenia gracza. */
	private static @Nullable Direction pickEnd(Level level, BlockPos pos, BlockState state, @Nullable Player player) {
		Direction attach = state.getValue(CoasterTrackBlock.ATTACH);
		Vec3 look = player != null ? player.getLookAngle() : Vec3.ZERO;
		Direction best = null;
		double bestDot = Double.NEGATIVE_INFINITY;
		for (Direction end : TrackGeometry.ends(state)) {
			if (TrackGeometry.connects(level, pos, attach, end)) {
				continue;
			}
			double dot = look.x * end.getStepX() + look.y * end.getStepY() + look.z * end.getStepZ();
			if (dot > bestDot) {
				bestDot = dot;
				best = end;
			}
		}
		return best;
	}
}
