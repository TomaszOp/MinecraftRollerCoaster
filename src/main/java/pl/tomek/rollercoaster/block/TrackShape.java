package pl.tomek.rollercoaster.block;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

/**
 * Kształt toru w jego płaszczyźnie.
 *
 * <p>STRAIGHT - tor prosty wzdłuż osi {@link CoasterTrackBlock#AXIS}.
 * Pozostałe wartości to zakręty 90° na tej samej powierzchni. Zakręt łączy
 * dwa kierunki leżące w płaszczyźnie toru: jeden wzdłuż osi "u", drugi wzdłuż
 * osi "v", gdzie u i v to dwie osie różne od osi przyczepienia, w kolejności X, Y, Z.
 * Np. dla podłogi u = X, v = Z, więc POS_POS łączy wschód (+X) z południem (+Z).
 *
 * <p>ASCENDING_POS / ASCENDING_NEG - pochylnia (tylko podłoga i sufit): tor wznosi się
 * o jeden blok (w stronę od bloku podpierającego), jadąc w dodatnim / ujemnym kierunku
 * osi {@link CoasterTrackBlock#AXIS}. Tak jak zwykłe tory Minecrafta na schodkach.
 */
public enum TrackShape implements StringRepresentable {
	STRAIGHT("straight", 0, 0),
	POS_POS("pos_pos", 1, 1),
	POS_NEG("pos_neg", 1, -1),
	NEG_POS("neg_pos", -1, 1),
	NEG_NEG("neg_neg", -1, -1),
	ASCENDING_POS("ascending_pos", 0, 0),
	ASCENDING_NEG("ascending_neg", 0, 0);

	private final String name;
	private final int signU;
	private final int signV;

	TrackShape(String name, int signU, int signV) {
		this.name = name;
		this.signU = signU;
		this.signV = signV;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}

	public boolean isCurve() {
		return signU != 0;
	}

	public boolean isSlope() {
		return this == ASCENDING_POS || this == ASCENDING_NEG;
	}

	/** Pochylnia wznosząca się w kierunku dir (dir leży na osi AXIS). */
	public static TrackShape slope(Direction dir) {
		return dir.getAxisDirection() == Direction.AxisDirection.POSITIVE ? ASCENDING_POS : ASCENDING_NEG;
	}

	/** Oś "u" płaszczyzny toru przyczepionego w stronę attach. */
	public static Direction.Axis axisU(Direction attach) {
		return attach.getAxis() == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
	}

	/** Oś "v" płaszczyzny toru przyczepionego w stronę attach. */
	public static Direction.Axis axisV(Direction attach) {
		return attach.getAxis() == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
	}

	/** Koniec zakrętu leżący na osi u. */
	public Direction endU(Direction attach) {
		return Direction.fromAxisAndDirection(axisU(attach), signU > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
	}

	/** Koniec zakrętu leżący na osi v. */
	public Direction endV(Direction attach) {
		return Direction.fromAxisAndDirection(axisV(attach), signV > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
	}

	/** Zakręt łączący dwa prostopadłe kierunki w płaszczyźnie toru. */
	public static TrackShape curve(Direction attach, Direction a, Direction b) {
		Direction u = a.getAxis() == axisU(attach) ? a : b;
		Direction v = u == a ? b : a;
		boolean pu = u.getAxisDirection() == Direction.AxisDirection.POSITIVE;
		boolean pv = v.getAxisDirection() == Direction.AxisDirection.POSITIVE;
		if (pu) {
			return pv ? POS_POS : POS_NEG;
		}
		return pv ? NEG_POS : NEG_NEG;
	}
}
