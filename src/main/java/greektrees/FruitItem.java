package greektrees;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Olive, fig and arbutus berry: food, and planted with a right click on leaves. Whichever face of the leaf block is
 * clicked, the twig goes under it; where nothing can be planted the fruit is eaten instead, like glow berries.
 */
public class FruitItem extends ItemNameBlockItem {
    public FruitItem(Block twig, Properties properties) {
        super(twig, properties);
    }

    @Override
    public BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos clicked = context.replacingClickedOnBlock() ? context.getClickedPos()
                : context.getClickedPos().relative(context.getClickedFace().getOpposite());
        if (level.getBlockState(clicked).is(BlockTags.LEAVES)) {
            return BlockPlaceContext.at(context, clicked.below(), Direction.DOWN);
        }
        return context;
    }
}
