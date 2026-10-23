package greektrees;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A twig with fruit hanging under a leaf block (olives, figs, arbutus berries). It ripens in three stages on random
 * ticks or with bone meal like cocoa. A right click on a ripe twig, whatever is in the hand, harvests it: 2-3 fruit,
 * and the twig starts over at its first stage, as if planted anew. A right click on an unripe twig does nothing
 * (bone meal still makes it grow). Broken (left click), a ripe twig gives 2-3 fruit and an unripe one a single
 * fruit. The fruit item is the block's item: a right click with it on leaves hangs a new twig under them.
 */
public class HangingFruitBlock extends Block implements BonemealableBlock {
    /**
     * The ripeness, 0 to 2. Deliberately not called "age": right-click harvest mods (Simple Harvesting) treat any
     * block with an "age" property as a crop and, not knowing its last stage, harvest it at every stage.
     */
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 2);
    public static final int MAX_AGE = 2;
    /** The stage a harvested twig or cluster starts over at: freshly planted. */
    public static final int PICKED_AGE = 0;
    private static final VoxelShape[] SHAPES = {
            Block.box(5, 9, 5, 11, 16, 11), Block.box(4, 6, 4, 12, 16, 12), Block.box(3, 3, 3, 13, 16, 13)};

    public HangingFruitBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STAGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(STAGE)];
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.above()).is(BlockTags.LEAVES);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighbourPos, BlockState neighbour,
                                     RandomSource random) {
        if (direction == Direction.UP && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(STAGE) < MAX_AGE;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) == 0) { // the pace of cocoa
            level.setBlock(pos, state.setValue(STAGE, state.getValue(STAGE) + 1), Block.UPDATE_CLIENTS);
        }
    }

    /** Every right click lands here, with or without something in the hand (the server asks the block first). */
    @Override
    protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        return rightClick(held, state, level, pos, player, STAGE);
    }

    /**
     * Right click on a fruit block (shared with the date cluster): ripe, it is harvested whatever is in the hand;
     * unripe, nothing happens. CONSUME ends the click quietly, so the held item is neither used on the block (no
     * twig planted next to it) nor eaten; only bone meal is let through, so it still makes the fruit grow.
     */
    static InteractionResult rightClick(ItemStack held, BlockState state, Level level, BlockPos pos, Player player,
                                        IntegerProperty age) {
        if (state.getValue(age) < MAX_AGE) {
            return held.is(Items.BONE_MEAL) ? InteractionResult.PASS : InteractionResult.CONSUME;
        }
        if (level instanceof ServerLevel server) {
            pick(server, pos, state, age, new ItemStack(state.getBlock().asItem()), player);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Harvests a ripe fruit block: drops 2-3 of the fruit, sets the block back to {@link #PICKED_AGE} and returns how
     * many dropped.
     */
    public static int pick(ServerLevel level, BlockPos pos, BlockState state, IntegerProperty age, ItemStack fruit,
                           @Nullable Entity picker) {
        int count = 2 + level.getRandom().nextInt(2);
        Block.popResource(level, pos, fruit.copyWithCount(count));
        level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0f,
                0.8f + level.getRandom().nextFloat() * 0.4f);
        BlockState picked = state.setValue(age, PICKED_AGE);
        level.setBlock(pos, picked, Block.UPDATE_CLIENTS);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(picker, picked));
        return count;
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
        return state.getValue(STAGE) < MAX_AGE;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state,
                                     BonemealSource source) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state,
                                BonemealSource source) {
        level.setBlock(pos, state.setValue(STAGE, state.getValue(STAGE) + 1), Block.UPDATE_CLIENTS);
    }
}
