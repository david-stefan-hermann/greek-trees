package greektrees;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/**
 * Client side: a thrown olive pit is drawn as its item, like a snowball; saplings and fruit are cut out; the
 * date cluster's model is a {@link FreeModel}.
 */
public final class GreekTreesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(GreekTrees.OLIVE_PIT_ENTITY, ThrownItemRenderer::new);
        DevShots.register();
        ModelLoadingPlugin.register(plugin -> plugin.resolveModel().register(context -> {
            ResourceLocation id = context.id();
            return id.getNamespace().equals(GreekTrees.MOD_ID) && id.getPath().startsWith("block/date_cluster_stage")
                    ? new FreeModel(id.getPath().substring("block/".length())) : null;
        }));
        for (GreekTrees.Species s : GreekTrees.SPECIES) {
            BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.cutout(), s.sapling(), s.potted());
        }
        for (Block fruit : new Block[]{GreekTrees.DATE_CLUSTER, GreekTrees.OLIVE_TWIG, GreekTrees.FIG_TWIG,
                GreekTrees.ARBUTUS_TWIG, GreekTrees.BLACK_MULBERRY_TWIG, GreekTrees.WHITE_MULBERRY_TWIG,
                GreekTrees.RED_MULBERRY_TWIG}) {
            BlockRenderLayerMap.INSTANCE.putBlock(fruit, RenderType.cutout());
        }
    }
}
