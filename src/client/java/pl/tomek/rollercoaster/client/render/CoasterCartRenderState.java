package pl.tomek.rollercoaster.client.render;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.Direction;

public class CoasterCartRenderState extends EntityRenderState {
	public Direction attach = Direction.DOWN;
	public Direction moveDir = Direction.NORTH;
}
