package greektrees;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * The Aries oak's sapling. The giant only grows from sixteen of them in a 4x4 square, any of which may start it, the
 * way four dark oak saplings start a dark oak; a single sapling or a smaller group stays a sapling, and bone meal is
 * not used up on it.
 */
public class AriesOakSaplingBlock extends GreekSaplingBlock {
    /** The feature greektrees:aries_oak (data/greektrees/worldgen/feature). */
    public static final ResourceKey<Feature> TREE = ResourceKey.create(Registries.FEATURE,
            GreekTrees.id("aries_oak"));
    /** Grows nothing by itself: the square is handled in {@link #advanceTree}. */
    private static final TreeGrower SQUARE_ONLY = new TreeGrower(TREE.identifier().toString(), WeightedList.of(),
            WeightedList.of(), WeightedList.of(), null);
    private static final int SIZE = 4;

    public AriesOakSaplingBlock(Properties properties) {
        super(SQUARE_ONLY, false, properties);
    }

    @Override
    public void advanceTree(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (state.getValue(STAGE) == 0) {
            level.setBlock(pos, state.cycle(STAGE), Block.UPDATE_NONE);
            return;
        }
        BlockPos corner = square(level, pos);
        Holder<Feature> tree = level.registryAccess().lookupOrThrow(Registries.FEATURE)
                .get(TREE).orElse(null);
        if (corner == null || tree == null) {
            return;
        }
        // like vanilla's 2x2 trees: the saplings make way, and come back if the tree has no room
        BlockState[] saplings = new BlockState[SIZE * SIZE];
        for (int i = 0; i < saplings.length; i++) {
            BlockPos p = corner.offset(i % SIZE, 0, i / SIZE);
            saplings[i] = level.getBlockState(p);
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_NONE);
        }
        if (!tree.value().place(level, level.getChunkSource().getGenerator(), random, corner)) {
            for (int i = 0; i < saplings.length; i++) {
                level.setBlock(corner.offset(i % SIZE, 0, i / SIZE), saplings[i], Block.UPDATE_NONE);
            }
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
        return square(level, pos) != null;
    }

    /** The north-west corner of a 4x4 square of these saplings that pos is part of, or null. */
    public BlockPos square(BlockGetter level, BlockPos pos) {
        for (int ox = 0; ox < SIZE; ox++) {
            for (int oz = 0; oz < SIZE; oz++) {
                BlockPos corner = pos.offset(-ox, 0, -oz);
                if (filled(level, corner)) {
                    return corner;
                }
            }
        }
        return null;
    }

    private boolean filled(BlockGetter level, BlockPos corner) {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                if (!level.getBlockState(corner.offset(x, 0, z)).is(this)) {
                    return false;
                }
            }
        }
        return true;
    }
}
