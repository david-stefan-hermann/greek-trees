package greektrees;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

/** Client side: a thrown olive pit is drawn as its item, like a snowball. */
public final class GreekTreesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(GreekTrees.OLIVE_PIT_ENTITY, ThrownItemRenderer::new);
    }
}
