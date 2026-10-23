package greektrees;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockState;

/** A vanilla sapling with its own tree; the date palm may also be planted on sand. */
public class GreekSaplingBlock extends SaplingBlock {
    private final boolean growsOnSand;

    public GreekSaplingBlock(TreeGrower treeGrower, boolean growsOnSand, Properties properties) {
        super(treeGrower, properties);
        this.growsOnSand = growsOnSand;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return super.mayPlaceOn(state, level, pos) || (growsOnSand && state.is(BlockTags.SAND));
    }

    @Override
    public void advanceTree(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        super.advanceTree(level, pos, state, random);
        // Vanilla clears the four saplings of a 2x2 tree without telling the clients, because its 2x2 trunks cover
        // all four. Ours may not (the palm clump has one trunk on the corner), so the square is sent again.
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 0, 1))) {
            BlockState now = level.getBlockState(p);
            if (!now.is(this)) {
                level.sendBlockUpdated(p.immutable(), state, now, Block.UPDATE_CLIENTS);
            }
        }
    }
}
