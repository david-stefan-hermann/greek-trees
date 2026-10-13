package greektrees;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * A thrown olive pit. It flies like a snowball and breaks on impact; it hurts for 1.5 hearts, a sharpened pit for
 * 3.5 hearts.
 */
public class ThrownOlivePit extends ThrowableItemProjectile {
    /** Entity event that shows the break particles on the clients (the snowball uses the same). */
    private static final byte BREAK = 3;
    public static final float DAMAGE = 3.0f;
    public static final float SHARPENED_DAMAGE = 7.0f;

    public ThrownOlivePit(EntityType<? extends ThrownOlivePit> type, Level level) {
        super(type, level);
    }

    public ThrownOlivePit(Level level, LivingEntity owner, ItemStack stack) {
        super(GreekTrees.OLIVE_PIT_ENTITY, owner, level);
        setItem(stack);
    }

    public ThrownOlivePit(Level level, double x, double y, double z, ItemStack stack) {
        super(GreekTrees.OLIVE_PIT_ENTITY, x, y, z, level);
        setItem(stack);
    }

    @Override
    protected Item getDefaultItem() {
        return GreekTrees.OLIVE_PIT;
    }

    @Override
    public void handleEntityEvent(byte event) {
        if (event != BREAK) {
            super.handleEntityEvent(event);
            return;
        }
        ItemStack stack = getItem().isEmpty() ? new ItemStack(getDefaultItem()) : getItem();
        ItemParticleOption particle = new ItemParticleOption(ParticleTypes.ITEM, stack);
        for (int i = 0; i < 8; i++) {
            level().addParticle(particle, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        float damage = getItem().is(GreekTrees.SHARPENED_OLIVE_PIT) ? SHARPENED_DAMAGE : DAMAGE;
        hit.getEntity().hurt(damageSources().thrown(this, getOwner()), damage);
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!level().isClientSide()) {
            level().broadcastEntityEvent(this, BREAK);
            discard();
        }
    }
}
