package pl.tomek.rollercoaster.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import pl.tomek.rollercoaster.block.TrackGeometry;
import pl.tomek.rollercoaster.block.TrackGeometry.Segment;
import pl.tomek.rollercoaster.registry.ModItems;

/**
 * Wagonik kolejki górskiej.
 *
 * <p>Wagonik nie używa zwykłej fizyki Minecrafta. Serwer prowadzi go dokładnie
 * po linii toru (blok po bloku) i co tick ustawia jego pozycję. Dzięki temu
 * może jechać po ścianie pionowo i po spodzie bloków do góry nogami.
 */
public class CoasterCartEntity extends Entity {
	// --- Parametry jazdy (bloki / tick) ---
	/** Przyspieszenie grawitacji wzdłuż toru (pełne na torze pionowym). */
	private static final double GRAVITY = 0.02;
	/** Napęd wagonika, gdy ktoś w nim siedzi (jak "silnik" kolejki). */
	private static final double MOTOR = 0.012;
	/** Prędkość, do której rozpędza napęd. */
	private static final double CRUISE_SPEED = 0.6;
	private static final double MAX_SPEED = 0.95;
	private static final double FRICTION = 0.997;

	/** Odległość oka pasażera od powierzchni toru (w stronę "góry" wagonika). */
	private static final double RIDER_EYE = 1.25;
	/** Środek bryły wagonika nad torem (hitbox ma 0.7 wysokości). */
	private static final double BODY_LIFT = 0.35;

	private static final EntityDataAccessor<Direction> DATA_ATTACH =
			SynchedEntityData.defineId(CoasterCartEntity.class, EntityDataSerializers.DIRECTION);
	private static final EntityDataAccessor<Direction> DATA_DIR =
			SynchedEntityData.defineId(CoasterCartEntity.class, EntityDataSerializers.DIRECTION);

	// --- Stan na torze (tylko serwer) ---
	private BlockPos cell = BlockPos.ZERO;
	/** Kierunek jazdy przy wjeździe do bieżącego bloku i przy wyjeździe (różne na zakręcie). */
	private Direction entryDir = Direction.NORTH;
	private Direction exitDir = Direction.NORTH;
	/** +1 = w górę pochylni, -1 = w dół, 0 = tor płaski. */
	private int climb = 0;
	private double progress = 0.5;
	private double speed = 0.0;
	private boolean onTrack = false;

	// Krótki łącznik między końcem jednego toru a początkiem następnego (zakręty 90° między powierzchniami).
	private Vec3 transFrom = Vec3.ZERO;
	private double transLen = 0.0;
	private double transPos = 0.0;

	public CoasterCartEntity(EntityType<? extends CoasterCartEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_ATTACH, Direction.DOWN);
		builder.define(DATA_DIR, Direction.NORTH);
	}

	// ------------------------------------------------------------------
	// Dostęp dla renderera i kamery
	// ------------------------------------------------------------------

	/** Strona, do której przyczepiony jest tor (DOWN = podłoga, UP = sufit). */
	public Direction getAttach() {
		return this.entityData.get(DATA_ATTACH);
	}

	/** Kierunek jazdy (na zakręcie: ten, który przeważa w danej chwili). */
	public Direction getMoveDir() {
		return this.entityData.get(DATA_DIR);
	}

	/** Czy wagonik jedzie do góry nogami (po spodzie bloku). */
	public boolean isUpsideDown() {
		return getAttach() == Direction.UP;
	}

	/** Przesunięcie środka encji względem punktu na torze. */
	public static Vec3 bodyOffset(Direction attach) {
		// Normalna toru = przeciwnie do bloku podpierającego.
		return new Vec3(
				-attach.getStepX() * BODY_LIFT,
				-attach.getStepY() * BODY_LIFT - BODY_LIFT,
				-attach.getStepZ() * BODY_LIFT);
	}

	/** Punkt na torze (powierzchnia szyn) wyliczony z pozycji encji. */
	public Vec3 trackPoint() {
		return this.position().subtract(bodyOffset(getAttach()));
	}

	private Segment segment() {
		return new Segment(cell, getAttach(), entryDir, exitDir, climb);
	}

	private void setSegment(Segment seg) {
		this.cell = seg.cell();
		this.entryDir = seg.entry();
		this.exitDir = seg.exit();
		this.climb = seg.climb();
		this.entityData.set(DATA_ATTACH, seg.attach());
		syncHeading();
	}

	/** Kierunek widoczny dla klienta: na zakręcie zmienia się w połowie łuku. */
	private void syncHeading() {
		Direction heading = progress < 0.5 ? entryDir : exitDir;
		if (getMoveDir() != heading) {
			this.entityData.set(DATA_DIR, heading);
		}
	}

	// ------------------------------------------------------------------
	// Stawianie na torze
	// ------------------------------------------------------------------

	public void placeOnTrack(Segment seg) {
		this.progress = 0.5;
		this.speed = 0.0;
		this.transLen = 0.0;
		this.transPos = 0.0;
		this.onTrack = true;
		setSegment(seg);
		Vec3 p = TrackGeometry.railPoint(segment(), progress).add(bodyOffset(seg.attach()));
		this.setPos(p.x, p.y, p.z);
		this.setOldPosAndRot();
	}

	// ------------------------------------------------------------------
	// Ruch
	// ------------------------------------------------------------------

	@Override
	public void tick() {
		super.tick();

		if (this.level().isClientSide()) {
			return; // klient tylko wyświetla pozycję przysłaną przez serwer
		}

		if (!onTrack) {
			return;
		}

		// Tor pod wagonikiem zniknął -> wagonik wypada jako przedmiot.
		if (!TrackGeometry.isValid(this.level(), segment())) {
			if (this.level() instanceof ServerLevel serverLevel) {
				breakCart(serverLevel);
			}
			return;
		}

		// Grawitacja wzdłuż toru: pod górę hamuje, w dół przyspiesza.
		speed -= GRAVITY * TrackGeometry.tangent(segment(), progress).y;

		// Napęd, gdy ktoś siedzi w wagoniku.
		if (this.isVehicle() && speed < CRUISE_SPEED) {
			speed += MOTOR;
		}

		speed *= FRICTION;
		speed = Math.min(speed, MAX_SPEED);

		// Prędkość ujemna = wagonik cofa się (np. nie wjechał na pionową ścianę).
		if (speed < 0) {
			speed = -speed;
			progress = 1.0 - progress;
			transPos = transLen; // przerwij ewentualny łącznik
			setSegment(segment().reversed());
		}

		advance(speed);
		syncHeading();

		Vec3 p = currentTrackPoint().add(bodyOffset(getAttach()));
		this.setPos(p.x, p.y, p.z);
	}

	private void advance(double distance) {
		int guard = 0;
		while (distance > 1.0e-6 && guard++ < 16) {
			// Najpierw dokończ łącznik (zakręt między powierzchniami).
			if (transPos < transLen) {
				double step = Math.min(distance, transLen - transPos);
				transPos += step;
				distance -= step;
				continue;
			}

			double length = segment().length();
			double left = (1.0 - progress) * length;
			if (distance < left) {
				progress += distance / length;
				return;
			}

			distance -= left;
			progress = 1.0;

			Segment cur = segment();
			Segment next = TrackGeometry.findNext(this.level(), cur);
			if (next == null) {
				// Koniec toru - stop (jak odbojnik).
				speed = 0.0;
				return;
			}

			Vec3 endPoint = TrackGeometry.railPoint(cur, 1.0);
			progress = 0.0;
			setSegment(next);

			Vec3 startPoint = TrackGeometry.railPoint(next, 0.0);
			transFrom = endPoint;
			transLen = endPoint.distanceTo(startPoint);
			transPos = 0.0;
		}
	}

	private Vec3 currentTrackPoint() {
		if (transPos < transLen && transLen > 1.0e-6) {
			Vec3 start = TrackGeometry.railPoint(segment(), 0.0);
			return transFrom.lerp(start, transPos / transLen);
		}
		return TrackGeometry.railPoint(segment(), progress);
	}

	// ------------------------------------------------------------------
	// Pasażer
	// ------------------------------------------------------------------

	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
		if (!this.hasPassenger(passenger)) {
			return;
		}
		Direction attach = getAttach();
		Vec3 eye = trackPoint().add(
				-attach.getStepX() * RIDER_EYE,
				-attach.getStepY() * RIDER_EYE,
				-attach.getStepZ() * RIDER_EYE);
		moveFunction.accept(passenger, eye.x, eye.y - passenger.getEyeHeight(), eye.z);
	}

	// ------------------------------------------------------------------
	// Niszczenie
	// ------------------------------------------------------------------

	@Override
	public boolean isPickable() {
		return !this.isRemoved();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (this.isRemoved()) {
			return false;
		}
		breakCart(level);
		return true;
	}

	private void breakCart(ServerLevel level) {
		this.ejectPassengers();
		this.spawnAtLocation(level, new ItemStack(ModItems.COASTER_CART));
		this.discard();
	}

	// ------------------------------------------------------------------
	// Zapis / odczyt świata
	// ------------------------------------------------------------------

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.onTrack = input.getBooleanOr("OnTrack", false);
		this.cell = new BlockPos(input.getIntOr("CellX", 0), input.getIntOr("CellY", 0), input.getIntOr("CellZ", 0));
		this.progress = input.getDoubleOr("Progress", 0.5);
		this.speed = input.getDoubleOr("Speed", 0.0);
		this.entityData.set(DATA_ATTACH, Direction.from3DDataValue(input.getIntOr("Attach", Direction.DOWN.get3DDataValue())));
		// "Dir" to kierunek wjazdu; "Exit" dodano w wersji 0.2 (starsze zapisy: tor prosty).
		this.entryDir = Direction.from3DDataValue(input.getIntOr("Dir", Direction.NORTH.get3DDataValue()));
		this.exitDir = Direction.from3DDataValue(input.getIntOr("Exit", entryDir.get3DDataValue()));
		this.climb = input.getIntOr("Climb", 0);
		syncHeading();
		this.transLen = 0.0;
		this.transPos = 0.0;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putBoolean("OnTrack", onTrack);
		output.putInt("CellX", cell.getX());
		output.putInt("CellY", cell.getY());
		output.putInt("CellZ", cell.getZ());
		output.putDouble("Progress", progress);
		output.putDouble("Speed", speed);
		output.putInt("Attach", getAttach().get3DDataValue());
		output.putInt("Dir", entryDir.get3DDataValue());
		output.putInt("Exit", exitDir.get3DDataValue());
		output.putInt("Climb", climb);
	}
}
