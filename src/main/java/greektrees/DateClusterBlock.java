package greektrees;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Dates hanging from the side of a palm trunk: cocoa behaviour (sits on jungle wood or logs, ripens in three stages
 * on random ticks or with bone meal, drops by its loot table, planted with a date on the side of jungle wood), with
 * the larger shape of the date model and the right click of the other fruit: a ripe cluster is harvested whatever
 * is in the hand and starts over, an unripe one ignores the click.
 */
public class DateClusterBlock extends CocoaBlock {
    /** Typed as cocoa because CocoaBlock fixes the return type of codec(); it still builds date clusters. */
    public static final MapCodec<CocoaBlock> CODEC = simpleCodec(DateClusterBlock::new);
    /**
     * Bounding boxes of the three model stages for facing south, trunk at +z (printed by tools/date_cluster.py,
     * rounded outwards). The half ripe and ripe clusters hang below their block.
     */
    private static final double[][] BOUNDS = {
            {3, 3, 7.5, 12.5, 15, 16}, {1, -4.5, 1.5, 14.5, 15.5, 16}, {1.5, -7.5, -1.5, 15, 16, 16}};

    public DateClusterBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<CocoaBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        double[] b = BOUNDS[state.getValue(AGE)];
        // the blockstate turns the south model by y rotations; turn the box the same way
        return switch (state.getValue(FACING)) {
            case NORTH -> Block.box(16 - b[3], b[1], 16 - b[5], 16 - b[0], b[4], 16 - b[2]);
            case WEST -> Block.box(16 - b[5], b[1], b[0], 16 - b[2], b[4], b[3]);
            case EAST -> Block.box(b[2], b[1], 16 - b[3], b[5], b[4], 16 - b[0]);
            default -> Block.box(b[0], b[1], b[2], b[3], b[4], b[5]);
        };
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        return HangingFruitBlock.rightClick(held, state, level, pos, player, AGE);
    }
}
