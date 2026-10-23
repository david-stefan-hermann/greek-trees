package greektrees.tree;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.TreeFeature;

/**
 * Places one Greek tree. The sapling's {@code TreeGrower} calls this with the level's random source, so the shape is
 * rolled anew on every growth. Like vanilla trees, the tree only grows when every log fits (air, leaves or
 * replaceable plants), except the logs of a soft foot, which give way to the ground; leaves and fruit simply skip
 * blocked spots, and fruit goes last so it finds its leaf or trunk. Each tree is its own feature type without
 * settings (data/greektrees/worldgen/feature only names the type).
 */
public final class GreekTreeFeature implements Feature {
    /** The flags vanilla's TreeFeature uses: update clients and neighbours, but skip shape updates. */
    private static final int FLAGS = Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE;

    private final Function<RandomSource, Shape> shapes;
    private final MapCodec<GreekTreeFeature> codec = MapCodec.unit(this);

    public GreekTreeFeature(Function<RandomSource, Shape> shapes) {
        this.shapes = shapes;
    }

    @Override
    public MapCodec<GreekTreeFeature> codec() {
        return codec;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        Shape shape = shapes.apply(random);
        Map<BlockPos, Shape.Cell> cells = shape.cells();
        Set<BlockPos> giveWay = new HashSet<>(); // logs of a soft foot where the ground is in the way
        for (Map.Entry<BlockPos, Shape.Cell> e : cells.entrySet()) {
            if (e.getValue().kind() == Shape.Kind.LOG && !fits(level, origin.offset(e.getKey()))) {
                if (e.getKey().getY() >= shape.softFoot()) {
                    return false;
                }
                giveWay.add(e.getKey());
            }
        }
        for (Shape.Kind kind : Shape.Kind.values()) {
            for (Map.Entry<BlockPos, Shape.Cell> e : cells.entrySet()) {
                Shape.Cell cell = e.getValue();
                if (cell.kind() != kind) {
                    continue;
                }
                BlockPos pos = origin.offset(e.getKey());
                if (giveWay.contains(e.getKey()) || (kind != Shape.Kind.LOG && !fits(level, pos))) {
                    continue;
                }
                BlockState state = cell.state();
                if (kind == Shape.Kind.OTHER && !state.canSurvive(level, pos)) {
                    continue; // fruit whose leaf or trunk spot was blocked
                }
                if (kind == Shape.Kind.LEAF && state.hasProperty(LeavesBlock.DISTANCE)) {
                    state = state.setValue(LeavesBlock.DISTANCE, shape.leafDistance(e.getKey()))
                            .setValue(LeavesBlock.PERSISTENT, shape.persistentLeaves());
                }
                level.setBlock(pos, state, FLAGS);
            }
        }
        return true;
    }

    private static boolean fits(WorldGenLevel level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && TreeFeature.validTreePos(level, pos);
    }
}
