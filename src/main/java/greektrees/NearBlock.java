package greektrees;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraft.world.phys.Vec3;

/**
 * Loot condition {@code greektrees:near_block}: true when the given block is within five blocks. The Greek trees use
 * wood blocks (bark on all sides) where vanilla trees use logs, so this tells their leaves apart: jungle leaves near
 * jungle wood are palm leaves, azalea leaves near oak wood are olive leaves, mangrove leaves near stripped acacia
 * wood are strawberry tree leaves. Fig and cypress are both acacia wood with azalea leaves, so fig leaves are told
 * apart by the fig twigs hanging under them instead.
 */
public record NearBlock(Block block) implements LootItemCondition {
    public static final MapCodec<NearBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BuiltInRegistries.BLOCK.byNameCodec().fieldOf("block").forGetter(NearBlock::block)).apply(i, NearBlock::new));
    private static final int RADIUS = 5;

    @Override
    public LootItemConditionType getType() {
        return GreekTrees.NEAR_BLOCK;
    }

    @Override
    public boolean test(LootContext context) {
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (origin == null) {
            return false;
        }
        ServerLevel level = context.getLevel();
        BlockPos center = BlockPos.containing(origin);
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-RADIUS, -RADIUS, -RADIUS), center.offset(RADIUS, RADIUS, RADIUS))) {
            if (level.getBlockState(p).is(block)) {
                return true;
            }
        }
        return false;
    }
}
