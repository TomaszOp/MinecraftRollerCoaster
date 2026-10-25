package pl.tomek.rollercoaster.block;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Uniwersalny tor kolejki.
 *
 * <p>Tor jest cienką płytą przyklejoną do jednej ściany sąsiedniego bloku:
 * <ul>
 *   <li>{@link #ATTACH} - kierunek OD toru DO bloku, na którym tor leży
 *       (DOWN = podłoga, UP = sufit / spód bloku, N/S/E/W = ściana),</li>
 *   <li>{@link #AXIS} - oś, wzdłuż której biegną szyny toru prostego (zawsze prostopadła do ATTACH),</li>
 *   <li>{@link #SHAPE} - tor prosty, zakręt 90° na tej samej powierzchni albo pochylnia.</li>
 * </ul>
 *
 * <p>Tor sam dopasowuje kształt do sąsiadów, jak zwykłe tory Minecrafta:
 * gdy obok końca linii postawisz tor w bok, koniec linii zamieni się w zakręt,
 * a gdy postawisz tor o jeden blok wyżej i dalej (schodek), tor niżej zamieni się w pochylnię.
 */
public class CoasterTrackBlock extends Block {
	public static final EnumProperty<Direction> ATTACH = BlockStateProperties.FACING;
	public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
	public static final EnumProperty<TrackShape> SHAPE = EnumProperty.create("curve", TrackShape.class);

	/** Grubość płyty toru w pikselach (1/16 bloku) - kształt zaznaczenia. */
	private static final double T = 3.0;

	private static final VoxelShape SHAPE_DOWN = Block.box(0, 0, 0, 16, T, 16);
	private static final VoxelShape SHAPE_UP = Block.box(0, 16 - T, 0, 16, 16, 16);
	private static final VoxelShape SHAPE_NORTH = Block.box(0, 0, 0, 16, 16, T);
	private static final VoxelShape SHAPE_SOUTH = Block.box(0, 0, 16 - T, 16, 16, 16);
	private static final VoxelShape SHAPE_WEST = Block.box(0, 0, 0, T, 16, 16);
	private static final VoxelShape SHAPE_EAST = Block.box(16 - T, 0, 0, 16, 16, 16);
	private static final VoxelShape SLOPE_DOWN = Block.box(0, 0, 0, 16, 8, 16);
	private static final VoxelShape SLOPE_UP = Block.box(0, 8, 0, 16, 16, 16);

	public CoasterTrackBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any()
				.setValue(ATTACH, Direction.DOWN)
				.setValue(AXIS, Direction.Axis.Z)
				.setValue(SHAPE, TrackShape.STRAIGHT));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(ATTACH, AXIS, SHAPE);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
		// Klikamy w ścianę bloku -> tor przykleja się do tej ściany.
		Direction attach = ctx.getClickedFace().getOpposite();
		Direction.Axis axis;

		if (attach.getAxis() == Direction.Axis.Y) {
			// Podłoga lub sufit: szyny wzdłuż kierunku, w którym patrzy gracz.
			axis = ctx.getHorizontalDirection().getAxis();
		} else if (ctx.isSecondaryUseActive()) {
			// Ściana + Shift: tor poziomy wzdłuż ściany.
			axis = attach.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
		} else {
			// Ściana: domyślnie tor pionowy (jazda w górę / w dół).
			axis = Direction.Axis.Y;
		}

		BlockState state = this.defaultBlockState().setValue(ATTACH, attach).setValue(AXIS, axis);
		if (!state.canSurvive(ctx.getLevel(), ctx.getClickedPos())) {
			return null;
		}

		// Jeśli obok są już tory, dopasuj się do nich (prosto albo zakręt).
		BlockState fitted = TrackGeometry.shapeFor(state,
				TrackGeometry.connections(ctx.getLevel(), ctx.getClickedPos(), attach));
		return fitted != null ? fitted : state;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.isClientSide()) {
			return;
		}

		Direction attach = state.getValue(ATTACH);
		if (attach.getAxis() == Direction.Axis.Y) {
			linkSlopes(level, pos, attach);
			slopeIntoWall(level, pos);
		} else {
			// Tor na ścianie: tor podłogowy pod nim (lub sufitowy nad nim) staje się pochylnią.
			slopeIntoWall(level, pos.below());
			slopeIntoWall(level, pos.above());
		}

		// Sąsiednie tory, których koniec jest "wolny", skręcają w stronę nowego toru.
		for (Direction side : TrackGeometry.inPlane(attach)) {
			BlockState self = level.getBlockState(pos);
			if (!TrackGeometry.isTrack(self) || TrackGeometry.isSlope(self)
					|| TrackGeometry.connectedEnds(level, pos, self).size() >= 2) {
				return;
			}

			BlockPos npos = pos.relative(side);
			BlockState neighbor = level.getBlockState(npos);
			if (!TrackGeometry.isTrack(neighbor)
					|| TrackGeometry.isSlope(neighbor)
					|| neighbor.getValue(ATTACH) != attach
					|| TrackGeometry.hasEnd(neighbor, side.getOpposite())) {
				continue;
			}

			List<Direction> nConn = TrackGeometry.connectedEnds(level, npos, neighbor);
			if (nConn.size() >= 2) {
				continue; // sąsiad jest częścią gotowej linii - nie ruszamy go
			}
			nConn.add(side.getOpposite());
			BlockState reshaped = TrackGeometry.shapeFor(neighbor, nConn);
			if (reshaped == null || reshaped == neighbor) {
				continue;
			}
			level.setBlock(npos, reshaped, Block.UPDATE_ALL);

			// Teraz sąsiad wskazuje na nas - dopasuj też nowy tor.
			BlockState refitted = TrackGeometry.shapeFor(self, TrackGeometry.connections(level, pos, attach));
			if (refitted != null && refitted != self) {
				level.setBlock(pos, refitted, Block.UPDATE_ALL);
			}
		}
	}

	/**
	 * Schodki: tor o jeden blok niżej i dalej staje się pochylnią prowadzącą do toru wyżej.
	 * Działa w obie strony - niezależnie od tego, który tor położono pierwszy.
	 */
	private static void linkSlopes(Level level, BlockPos pos, Direction attach) {
		Direction rise = attach.getOpposite();
		for (Direction side : TrackGeometry.inPlane(attach)) {
			// a) Nowy tor jest wyżej: tor za nami, o stopień niżej, wznosi się w naszą stronę.
			BlockPos lowPos = pos.relative(side).relative(attach);
			if (canBecomeSlope(level, lowPos, attach, side.getOpposite())) {
				makeSlope(level, lowPos, side.getOpposite());
				continue;
			}

			// b) Nowy tor jest niżej: przed nami, o stopień wyżej, leży tor - stajemy się pochylnią.
			if (TrackGeometry.isTrack(level.getBlockState(pos.relative(side).relative(rise)))
					&& canBecomeSlope(level, pos, attach, side)) {
				makeSlope(level, pos, side);
				return;
			}
		}
	}

	/**
	 * Wejście na ścianę: tor na podłodze (lub suficie), nad którym (pod którym) zaczyna się
	 * pionowy tor na ścianie, zamienia się w pochylnię prowadzącą prosto na ścianę.
	 */
	private static void slopeIntoWall(Level level, BlockPos pos) {
		BlockState floor = level.getBlockState(pos);
		if (!TrackGeometry.isTrack(floor) || floor.getValue(SHAPE) != TrackShape.STRAIGHT) {
			return;
		}
		Direction attach = floor.getValue(ATTACH);
		if (attach.getAxis() != Direction.Axis.Y) {
			return;
		}
		BlockState wall = level.getBlockState(pos.relative(attach.getOpposite()));
		if (!TrackGeometry.isTrack(wall) || wall.getValue(SHAPE) != TrackShape.STRAIGHT
				|| wall.getValue(AXIS) != Direction.Axis.Y) {
			return;
		}
		Direction toWall = wall.getValue(ATTACH);
		if (toWall.getAxis() == Direction.Axis.Y) {
			return;
		}
		// Tor nie może już być połączony w poprzek kierunku do ściany.
		for (Direction end : TrackGeometry.connectedEnds(level, pos, floor)) {
			if (end.getAxis() != toWall.getAxis()) {
				return;
			}
		}
		makeSlope(level, pos, toWall);
	}

	/** Czy tor w pos może zostać pochylnią wznoszącą się w kierunku up. */
	private static boolean canBecomeSlope(Level level, BlockPos pos, Direction attach, Direction up) {
		BlockState state = level.getBlockState(pos);
		if (!TrackGeometry.isTrack(state) || state.getValue(ATTACH) != attach || state.getValue(SHAPE).isCurve()) {
			return false;
		}
		if (TrackGeometry.isSlope(state)) {
			return false;
		}
		// Tor nie może już być połączony w innym kierunku niż od dołu pochylni.
		for (Direction end : TrackGeometry.connectedEnds(level, pos, state)) {
			if (end != up.getOpposite()) {
				return false;
			}
		}
		// Na górze pochylni musi leżeć tor, na który da się wjechać.
		BlockPos top = pos.relative(up).relative(attach.getOpposite());
		BlockState topState = level.getBlockState(top);
		if (!TrackGeometry.isTrack(topState) || topState.getValue(ATTACH) != attach) {
			return false;
		}
		if (topState.getValue(SHAPE) == TrackShape.STRAIGHT && topState.getValue(AXIS) != up.getAxis()
				&& TrackGeometry.connectedEnds(level, top, topState).isEmpty()) {
			// Samotny tor na górze obróć w stronę pochylni.
			level.setBlock(top, topState.setValue(AXIS, up.getAxis()), Block.UPDATE_ALL);
		}
		return TrackGeometry.enter(level, top, attach, up) != null;
	}

	private static void makeSlope(Level level, BlockPos pos, Direction up) {
		BlockState state = level.getBlockState(pos);
		level.setBlock(pos, state.setValue(AXIS, up.getAxis()).setValue(SHAPE, TrackShape.slope(up)), Block.UPDATE_ALL);
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		Direction attach = state.getValue(ATTACH);
		BlockPos supportPos = pos.relative(attach);
		return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, attach.getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		boolean slope = state.getValue(SHAPE).isSlope();
		return switch (state.getValue(ATTACH)) {
			case DOWN -> slope ? SLOPE_DOWN : SHAPE_DOWN;
			case UP -> slope ? SLOPE_UP : SHAPE_UP;
			case NORTH -> SHAPE_NORTH;
			case SOUTH -> SHAPE_SOUTH;
			case WEST -> SHAPE_WEST;
			case EAST -> SHAPE_EAST;
		};
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		// Przez tor można przechodzić (jak przez zwykłe tory).
		return Shapes.empty();
	}
}
