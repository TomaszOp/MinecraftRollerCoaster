package pl.tomek.rollercoaster.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import pl.tomek.rollercoaster.RollerCoasterMod;
import pl.tomek.rollercoaster.entity.CoasterCartEntity;

/**
 * Rysuje wagonik z prostych prostopadłościanów, obrócony zgodnie z torem:
 * lokalna oś X = kierunek jazdy, lokalna oś Y = "góra" wagonika (normalna toru).
 * Dzięki temu na suficie wagonik jest naprawdę odwrócony do góry kołami.
 *
 * <p>Rysujemy zwykłym typem renderowania encji (biała tekstura barwiona kolorem wierzchołków,
 * z oświetleniem i normalnymi), dzięki czemu wagonik jest widoczny także z shaderami (Iris).
 */
public class CoasterCartRenderer extends EntityRenderer<CoasterCartEntity, CoasterCartRenderState> {
	private static final int BODY = 0xFFC0392B;      // czerwony
	private static final int BODY_DARK = 0xFF8E2A1F; // ciemniejszy czerwony
	private static final int FLOOR = 0xFF5D4037;     // brązowa podłoga
	private static final int WHEEL = 0xFF2B2B2B;     // ciemne koła
	private static final int SEAT = 0xFF1F4E79;      // niebieskie siedzenie

	/** Biała tekstura 16x16 - kolor nadaje kolor wierzchołków. */
	private static final Identifier TEXTURE = RollerCoasterMod.id("textures/entity/coaster_cart.png");

	public CoasterCartRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public CoasterCartRenderState createRenderState() {
		return new CoasterCartRenderState();
	}

	@Override
	public void extractRenderState(CoasterCartEntity entity, CoasterCartRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.attach = entity.getAttach();
		state.moveDir = entity.getMoveDir();
	}

	@Override
	public void submit(CoasterCartRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState) {
		poseStack.pushPose();

		// Z pozycji encji (środek bryły) wracamy na powierzchnię toru.
		Vec3 off = CoasterCartEntity.bodyOffset(state.attach);
		poseStack.translate(-off.x, -off.y, -off.z);

		// Obrót do układu toru.
		poseStack.mulPose(frameRotation(state.attach, state.moveDir));

		final int light = state.lightCoords;
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE), (pose, buffer) -> {
			// Koła (pod spodem, na szynach)
			box(pose, buffer, -0.42f, -0.02f, -0.36f, -0.22f, 0.12f, -0.22f, WHEEL, light);
			box(pose, buffer, -0.42f, -0.02f, 0.22f, -0.22f, 0.12f, 0.36f, WHEEL, light);
			box(pose, buffer, 0.22f, -0.02f, -0.36f, 0.42f, 0.12f, -0.22f, WHEEL, light);
			box(pose, buffer, 0.22f, -0.02f, 0.22f, 0.42f, 0.12f, 0.36f, WHEEL, light);
			// Podłoga
			box(pose, buffer, -0.5f, 0.12f, -0.42f, 0.5f, 0.2f, 0.42f, FLOOR, light);
			// Ściany boczne
			box(pose, buffer, -0.5f, 0.2f, -0.42f, 0.5f, 0.62f, -0.34f, BODY, light);
			box(pose, buffer, -0.5f, 0.2f, 0.34f, 0.5f, 0.62f, 0.42f, BODY, light);
			// Przód (wyższy) i tył
			box(pose, buffer, 0.42f, 0.2f, -0.34f, 0.5f, 0.75f, 0.34f, BODY_DARK, light);
			box(pose, buffer, -0.5f, 0.2f, -0.34f, -0.42f, 0.62f, 0.34f, BODY_DARK, light);
			// Siedzenie
			box(pose, buffer, -0.42f, 0.2f, -0.34f, -0.1f, 0.38f, 0.34f, SEAT, light);
		});

		poseStack.popPose();
		super.submit(state, poseStack, collector, cameraState);
	}

	/** Obrót: lokalne X -> kierunek jazdy, lokalne Y -> normalna toru. */
	private static Matrix4f frameRotation(Direction attach, Direction moveDir) {
		float fx = moveDir.getStepX(), fy = moveDir.getStepY(), fz = moveDir.getStepZ();
		float nx = -attach.getStepX(), ny = -attach.getStepY(), nz = -attach.getStepZ();
		// Z = X x Y
		float rx = fy * nz - fz * ny;
		float ry = fz * nx - fx * nz;
		float rz = fx * ny - fy * nx;
		// Matrix3f(m00, m01, m02, ...) przyjmuje kolejne KOLUMNY.
		Matrix3f m = new Matrix3f(fx, fy, fz, nx, ny, nz, rx, ry, rz);
		return new Matrix4f(m);
	}

	private static void box(PoseStack.Pose pose, VertexConsumer vc,
			float x0, float y0, float z0, float x1, float y1, float z1, int color, int light) {
		// Przód (z0)
		quad(pose, vc, color, light, 0, 0, -1, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
		// Tył (z1)
		quad(pose, vc, color, light, 0, 0, 1, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1);
		// Lewo (x0)
		quad(pose, vc, color, light, -1, 0, 0, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1);
		// Prawo (x1)
		quad(pose, vc, color, light, 1, 0, 0, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
		// Góra (y1)
		quad(pose, vc, color, light, 0, 1, 0, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
		// Dół (y0)
		quad(pose, vc, color, light, 0, -1, 0, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0);
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer vc, int color, int light,
			float nx, float ny, float nz,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz) {
		vertex(pose, vc, color, light, nx, ny, nz, ax, ay, az, 0f, 1f);
		vertex(pose, vc, color, light, nx, ny, nz, bx, by, bz, 1f, 1f);
		vertex(pose, vc, color, light, nx, ny, nz, cx, cy, cz, 1f, 0f);
		vertex(pose, vc, color, light, nx, ny, nz, dx, dy, dz, 0f, 0f);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer vc, int color, int light,
			float nx, float ny, float nz, float x, float y, float z, float u, float v) {
		vc.addVertex(pose, x, y, z)
				.setColor(color)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, nx, ny, nz);
	}
}
