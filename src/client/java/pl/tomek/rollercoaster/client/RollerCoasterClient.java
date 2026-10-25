package pl.tomek.rollercoaster.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import pl.tomek.rollercoaster.client.render.CoasterCartRenderer;
import pl.tomek.rollercoaster.registry.ModEntities;

public class RollerCoasterClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.COASTER_CART, CoasterCartRenderer::new);
	}
}
