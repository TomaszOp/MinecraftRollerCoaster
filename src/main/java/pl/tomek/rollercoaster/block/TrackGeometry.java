package pl.tomek.rollercoaster.block;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import pl.tomek.rollercoaster.registry.ModBlocks;

/**
 * Geometria toru: gdzie w bloku leży linia jazdy i jak przechodzić
 * na sąsiedni tor (prosto, zakrętem w lewo/prawo, na ścianę, na sufit itd.).
 *
 * <p>Tor prosty to odcinek o długości 1 biegnący przez środek bloku, a zakręt
 * to ćwiartka okręgu o promieniu 0.5 łącząca środki dwóch sąsiednich krawędzi.
 * Pochylnia to odcinek prosty, który na długości bloku wznosi się o jeden blok.
 * Linia jazdy jest przesunięta w stronę bloku podpierającego, tak aby leżała
 * na powierzchni szyn.
 */
public final class TrackGeometry {
	/** Odległość powierzchni szyn od bloku podpierającego (2.5 piksela). */
	public static final double SURFACE = 2.5 / 16.0;

	/** Długość zakrętu: ćwiartka okręgu o promieniu 0.5. */
	public static final double CURVE_LENGTH = Math.PI / 4.0;

	/** Długość pochylni: przekątna 1 x 1. */
	public static final double SLOPE_LENGTH = Math.sqrt(2.0);

	private TrackGeometry() {
	}

	/**
	 * Stan kolejki na torze: blok, strona przyczepienia, kierunek jazdy przy
	 * wjeździe do bloku (entry) i przy wyjeździe z niego (exit).
	 * Na torze prostym entry == exit. climb: +1 = jazda w górę pochylni,
	 * -1 = w dół pochylni, 0 = tor płaski.
	 */
	public record Segment(BlockPos cell, Direction attach, Direction entry, Direction exit, int climb) {
		public Segment(BlockPos cell, Direction attach, Direction entry, Direction exit) {
			this(cell, attach, entry, exit, 0);
		}

		public boolean isCurve() {
			return entry != exit;
		}

		public double length() {
			if (climb != 0) {
				return SLOPE_LENGTH;
			}
			return isCurve() ? CURVE_LENGTH : 1.0;
		}

		/** Ten sam odcinek przejechany w przeciwną stronę. */
		public Segment reversed() {
			return new Segment(cell, attach, exit.getOpposite(), entry.getOpposite(), -climb);
		}
	}

	/** Pozycja kolejki na odcinku dla postępu t (0..1). */
	public static Vec3 railPoint(Segment seg, double t) {
		BlockPos cell = seg.cell();
		Direction attach = seg.attach();
		double off = 0.5 - SURFACE;
		Vec3 center = new Vec3(
				cell.getX() + 0.5 + attach.getStepX() * off,
				cell.getY() + 0.5 + attach.getStepY() * off,
				cell.getZ() + 0.5 + attach.getStepZ() * off);

		if (!seg.isCurve()) {
			Direction dir = seg.entry();
			double along = t - 0.5;
			Vec3 p = center.add(dir.getStepX() * along, dir.getStepY() * along, dir.getStepZ() * along);
			if (seg.climb() != 0) {
				// Pochylnia: wysokość rośnie od 0 do 1 bloku (w stronę od bloku podpierającego).
				double h = seg.climb() > 0 ? t : 1.0 - t;
				p = p.add(step(attach.getOpposite()).scale(h));
			}
			return p;
		}

		// A = krawędź, przez którą wjeżdżamy; B = krawędź, przez którą wyjeżdżamy.
		// Środek łuku leży w narożniku między nimi.
		Vec3 a = step(seg.entry().getOpposite());
		Vec3 b = step(seg.exit());
		Vec3 corner = center.add(a.scale(0.5)).add(b.scale(0.5));
		double phi = t * Math.PI / 2.0;
		return corner.subtract(b.scale(0.5 * Math.cos(phi)).add(a.scale(0.5 * Math.sin(phi))));
	}

	/** Kierunek ruchu (wektor jednostkowy) na odcinku dla postępu t (0..1). */
	public static Vec3 tangent(Segment seg, double t) {
		if (!seg.isCurve()) {
			if (seg.climb() != 0) {
				return step(seg.entry()).add(step(seg.attach().getOpposite()).scale(seg.climb())).scale(1.0 / SLOPE_LENGTH);
			}
			return step(seg.entry());
		}
		double phi = t * Math.PI / 2.0;
		return step(seg.entry()).scale(Math.cos(phi)).add(step(seg.exit()).scale(Math.sin(phi)));
	}

	private static Vec3 step(Direction d) {
		return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ());
	}

	// ------------------------------------------------------------------
	// Końce toru i łączenie z sąsiadami
	// ------------------------------------------------------------------

	/** Cztery kierunki leżące w płaszczyźnie toru przyczepionego w stronę attach. */
	public static List<Direction> inPlane(Direction attach) {
		List<Direction> dirs = new ArrayList<>(4);
		for (Direction d : Direction.values()) {
			if (d.getAxis() != attach.getAxis()) {
				dirs.add(d);
			}
		}
		return dirs;
	}

	/** Dwa końce toru (kierunki od środka bloku do krawędzi, przez które biegną szyny). */
	public static Direction[] ends(BlockState state) {
		Direction attach = state.getValue(CoasterTrackBlock.ATTACH);
		TrackShape shape = state.getValue(CoasterTrackBlock.SHAPE);
		if (shape.isCurve()) {
			return new Direction[] {shape.endU(attach), shape.endV(attach)};
		}
		Direction.Axis axis = state.getValue(CoasterTrackBlock.AXIS);
		return new Direction[] {
				Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE),
				Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE)};
	}

	public static boolean hasEnd(BlockState state, Direction end) {
		Direction[] e = ends(state);
		return e[0] == end || e[1] == end;
	}

	public static boolean isTrack(BlockState state) {
		return state.is(ModBlocks.COASTER_TRACK);
	}

	public static boolean isSlope(BlockState state) {
		return isTrack(state) && state.getValue(CoasterTrackBlock.SHAPE).isSlope();
	}

	/** Kierunek, w którym pochylnia się wznosi. */
	public static Direction slopeDir(BlockState state) {
		Direction.AxisDirection ad = state.getValue(CoasterTrackBlock.SHAPE) == TrackShape.ASCENDING_POS
				? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE;
		return Direction.fromAxisAndDirection(state.getValue(CoasterTrackBlock.AXIS), ad);
	}

	/** Odcinek, który kończy się wyjazdem z toru w bloku pos przez koniec end. */
	public static Segment exitSegment(BlockPos pos, BlockState state, Direction end) {
		Direction attach = state.getValue(CoasterTrackBlock.ATTACH);
		if (isSlope(state)) {
			return new Segment(pos.immutable(), attach, end, end, end == slopeDir(state) ? 1 : -1);
		}
		Direction[] e = ends(state);
		Direction other = e[0] == end ? e[1] : e[0];
		return new Segment(pos.immutable(), attach, other.getOpposite(), end);
	}

	/**
	 * Próba wjazdu na tor w bloku pos (przyczepiony w stronę attach), jadąc w kierunku moveDir.
	 * Zwraca odcinek, jeśli tor ma koniec skierowany w naszą stronę.
	 */
	public static @Nullable Segment enter(BlockGetter level, BlockPos pos, Direction attach, Direction moveDir) {
		BlockState state = level.getBlockState(pos);
		if (!isTrack(state) || state.getValue(CoasterTrackBlock.ATTACH) != attach) {
			// Górny koniec pochylni leżącej o jeden blok niżej (bliżej bloku podpierającego).
			BlockPos low = pos.relative(attach);
			BlockState ls = level.getBlockState(low);
			if (isSlope(ls) && ls.getValue(CoasterTrackBlock.ATTACH) == attach && slopeDir(ls) == moveDir.getOpposite()) {
				return new Segment(low.immutable(), attach, moveDir, moveDir, -1);
			}
			return null;
		}
		if (state.getValue(CoasterTrackBlock.SHAPE).isSlope()) {
			// Na pochylnię wjeżdża się na tym poziomie tylko od dołu.
			return slopeDir(state) == moveDir ? new Segment(pos.immutable(), attach, moveDir, moveDir, 1) : null;
		}
		Direction back = moveDir.getOpposite();
		Direction[] e = ends(state);
		if (e[0] == back) {
			return new Segment(pos.immutable(), attach, moveDir, e[1]);
		}
		if (e[1] == back) {
			return new Segment(pos.immutable(), attach, moveDir, e[0]);
		}
		return null;
	}

	/** Czy tor w bloku nadal ma kształt pasujący do odcinka (np. nie został zniszczony). */
	public static boolean isValid(BlockGetter level, Segment seg) {
		BlockState state = level.getBlockState(seg.cell());
		if (seg.climb() != 0) {
			Direction up = seg.climb() > 0 ? seg.entry() : seg.entry().getOpposite();
			return isSlope(state) && state.getValue(CoasterTrackBlock.ATTACH) == seg.attach() && slopeDir(state) == up;
		}
		if (isSlope(state)) {
			return false;
		}
		Segment s = enter(level, seg.cell(), seg.attach(), seg.entry());
		return s != null && s.exit() == seg.exit();
	}

	/**
	 * Szuka kolejnego toru po dojechaniu do końca bieżącego bloku.
	 * Kolejność: ta sama powierzchnia (prosto lub zakręt), zakręt wewnętrzny
	 * (np. podłoga -> ściana przed nami), zakręt zewnętrzny
	 * (np. podłoga -> zjazd w dół po ścianie bloku).
	 */
	public static @Nullable Segment findNext(BlockGetter level, Segment cur) {
		Direction a = cur.attach();
		Direction d = cur.exit();
		// Po wjeździe na pochylnię wyjeżdżamy o jeden blok wyżej.
		BlockPos cell = cur.climb() > 0 ? cur.cell().relative(a.getOpposite()) : cur.cell();

		// 1. Ta sama powierzchnia - tor prosty albo zakręt w lewo/prawo.
		Segment next = enter(level, cell.relative(d), a, d);
		if (next != null) {
			return next;
		}

		// 1b. Szczyt pochylni wchodzi prosto na ścianę: tor pionowy tuż nad pochylnią.
		if (cur.climb() > 0) {
			next = enter(level, cell, d, a.getOpposite());
			if (next != null) {
				return next;
			}
		}

		// 1c. Zjazd ze ściany na szczyt pochylni pod nami (odwrotność 1b).
		BlockPos below = cell.relative(d);
		BlockState belowState = level.getBlockState(below);
		if (isSlope(belowState) && belowState.getValue(CoasterTrackBlock.ATTACH) == d && slopeDir(belowState) == a) {
			return new Segment(below.immutable(), d, a.getOpposite(), a.getOpposite(), -1);
		}

		// 2. Zakręt wewnętrzny: przed nami ściana, wjeżdżamy na nią.
		//    Nowy tor jest "nad" nami (po stronie normalnej), przyklejony do ściany przed nami.
		next = enter(level, cell.relative(a.getOpposite()), d, a.getOpposite());
		if (next != null) {
			return next;
		}

		// 2b. Zakręt wewnętrzny "od drugiej strony": np. zjeżdżając pionowo w dół
		//     trafiamy na tor podłogowy leżący w narożniku pod nami.
		next = enter(level, cell.relative(d), d, a.getOpposite());
		if (next != null) {
			return next;
		}

		// 3. Zakręt zewnętrzny: koniec bloku, tor zawija się na jego drugą stronę.
		return enter(level, cell.relative(d).relative(a), d.getOpposite(), a);
	}

	/** Czy wyjeżdżając z bloku pos w kierunku dir trafimy na jakiś tor. */
	public static boolean connects(BlockGetter level, BlockPos pos, Direction attach, Direction dir) {
		return findNext(level, new Segment(pos, attach, dir, dir)) != null;
	}

	/** Kierunki w płaszczyźnie toru, w których z bloku pos da się wjechać na sąsiedni tor. */
	public static List<Direction> connections(BlockGetter level, BlockPos pos, Direction attach) {
		List<Direction> result = new ArrayList<>(4);
		for (Direction d : inPlane(attach)) {
			if (connects(level, pos, attach, d)) {
				result.add(d);
			}
		}
		return result;
	}

	/** Końce toru, które już łączą się z sąsiednim torem. */
	public static List<Direction> connectedEnds(BlockGetter level, BlockPos pos, BlockState state) {
		Direction attach = state.getValue(CoasterTrackBlock.ATTACH);
		List<Direction> result = new ArrayList<>(2);
		for (Direction e : ends(state)) {
			if (findNext(level, exitSegment(pos, state, e)) != null) {
				result.add(e);
			}
		}
		return result;
	}

	/**
	 * Kształt toru dopasowany do kierunków, w których są sąsiednie tory:
	 * dwa prostopadłe kierunki dają zakręt, jeden (lub dwa przeciwne) - tor prosty.
	 * Zwraca null, gdy lista jest pusta.
	 */
	public static @Nullable BlockState shapeFor(BlockState base, List<Direction> dirs) {
		Direction attach = base.getValue(CoasterTrackBlock.ATTACH);
		for (int i = 0; i < dirs.size(); i++) {
			for (int j = i + 1; j < dirs.size(); j++) {
				Direction p = dirs.get(i);
				Direction q = dirs.get(j);
				if (p.getAxis() != q.getAxis()) {
					return base.setValue(CoasterTrackBlock.SHAPE, TrackShape.curve(attach, p, q));
				}
			}
		}
		if (dirs.isEmpty()) {
			return null;
		}
		return base.setValue(CoasterTrackBlock.SHAPE, TrackShape.STRAIGHT)
				.setValue(CoasterTrackBlock.AXIS, dirs.get(0).getAxis());
	}
}
