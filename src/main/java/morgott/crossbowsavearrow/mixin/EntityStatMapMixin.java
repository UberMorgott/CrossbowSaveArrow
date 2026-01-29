package morgott.crossbowsavearrow.mixin;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.EntityStatOp;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Overwrites addStatValue() to:
 * 1. Consume arrows AFTER ammo actually increases (prevents loss on interrupted reload).
 * 2. Write ammo to crossbow metadata on ANY ammo change (reload or shot),
 *    so the crossbow always has up-to-date ammo in its ItemStack.
 *
 * Uses @Overwrite because @Inject with CallbackInfoReturnable causes
 * NoClassDefFoundError at runtime (class not available in server classloader).
 *
 * Shared state is stored in System.getProperties() to work across classloaders.
 */
@Mixin(EntityStatMap.class)
public abstract class EntityStatMapMixin {

    @Unique
    private static final Logger LOGGER = Logger.getLogger("CrossbowSaveArrow");

    @Unique
    private static final String LOADED_AMMO_KEY = "LoadedAmmo";

    @Unique
    private static final String PROP_ENTITY_MAP = "crossbowsavearrow.entityMap";
    @Unique
    private static final String PROP_PENDING_ARROW = "crossbowsavearrow.pendingArrow";
    @Unique
    private static final String PROP_PENDING_CONTAINER = "crossbowsavearrow.pendingContainer";

    @Unique
    private static Method setMethod;

    @Shadow
    @Nullable
    public abstract EntityStatValue get(int index);

    @Shadow
    private void addChange(EntityStatMap.Predictable predictable, int index, @Nonnull EntityStatOp op, float previousValue, float value) {
        throw new AssertionError();
    }

    @SuppressWarnings("unchecked")
    @Unique
    private static Map<Object, Object> getEntityMap() {
        Object obj = System.getProperties().get(PROP_ENTITY_MAP);
        if (obj instanceof Map) {
            return (Map<Object, Object>) obj;
        }
        Map<Object, Object> map = new WeakHashMap<>();
        System.getProperties().put(PROP_ENTITY_MAP, map);
        return map;
    }

    @SuppressWarnings("unchecked")
    @Unique
    private static ThreadLocal<Object> getPendingArrow() {
        Object obj = System.getProperties().get(PROP_PENDING_ARROW);
        if (obj instanceof ThreadLocal) {
            return (ThreadLocal<Object>) obj;
        }
        ThreadLocal<Object> tl = new ThreadLocal<>();
        System.getProperties().put(PROP_PENDING_ARROW, tl);
        return tl;
    }

    @SuppressWarnings("unchecked")
    @Unique
    private static ThreadLocal<Object> getPendingContainer() {
        Object obj = System.getProperties().get(PROP_PENDING_CONTAINER);
        if (obj instanceof ThreadLocal) {
            return (ThreadLocal<Object>) obj;
        }
        ThreadLocal<Object> tl = new ThreadLocal<>();
        System.getProperties().put(PROP_PENDING_CONTAINER, tl);
        return tl;
    }

    /**
     * @author CrossbowSaveArrow
     * @reason 1) Consume arrows after ammo increase (not before, preventing loss on interrupted reload).
     *         2) Write ammo to crossbow metadata on any ammo change (reload/shot).
     */
    @Overwrite
    public float addStatValue(EntityStatMap.Predictable predictable, int index, float amount) {
        // --- Original logic (unchanged) ---
        EntityStatValue entityStatValue = this.get(index);
        if (entityStatValue == null) {
            HytaleLogger.getLogger().at(Level.WARNING).log("No EntityStatValue found for index: " + index);
            return 0.0F;
        }

        float currentValue = entityStatValue.get();
        float ret;
        try {
            if (setMethod == null) {
                setMethod = EntityStatValue.class.getDeclaredMethod("set", float.class);
                setMethod.setAccessible(true);
            }
            ret = (float) setMethod.invoke(entityStatValue, currentValue + amount);
        } catch (Exception e) {
            LOGGER.severe("[CrossbowSaveArrow] Failed to invoke EntityStatValue.set(): " + e);
            return currentValue;
        }

        if (predictable != EntityStatMap.Predictable.NONE || ret != currentValue) {
            this.addChange(predictable, index, EntityStatOp.Add, currentValue, amount);
        }

        // --- Our addition: handle ammo changes for crossbows ---
        int ammoIndex = DefaultEntityStatTypes.getAmmo();
        if (index == ammoIndex && ret != currentValue) {
            EntityStatMap self = (EntityStatMap) (Object) this;
            Object entityObj = getEntityMap().get(self);
            if (entityObj instanceof LivingEntity entity) {
                Inventory inventory = entity.getInventory();
                ItemStack itemInHand = inventory.getItemInHand();
                if (itemInHand != null && !itemInHand.isEmpty()) {
                    String itemId = itemInHand.getItemId();
                    if (itemId != null && itemId.contains("Crossbow")) {

                        // 1. On ammo increase (reload): execute deferred arrow removal
                        if (ret > currentValue) {
                            Object pendingArrowObj = getPendingArrow().get();
                            Object pendingContainerObj = getPendingContainer().get();
                            if (pendingArrowObj instanceof ItemStack pendingArrow
                                    && pendingContainerObj instanceof CombinedItemContainer pendingContainer) {
                                pendingContainer.removeItemStack(pendingArrow, true, false);
                                // Arrow consumed after ammo increase
                                getPendingArrow().remove();
                                getPendingContainer().remove();
                            }
                        }

                        // 2. On any ammo change: write to crossbow metadata
                        ItemContainer hotbar = inventory.getHotbar();
                        if (hotbar != null) {
                            byte activeSlot = inventory.getActiveHotbarSlot();
                            writeAmmoToSlot(hotbar, activeSlot, ret);
                        }
                    }
                }
            }
        }

        return ret;
    }

    /**
     * Write LoadedAmmo to the crossbow in the given slot, only if changed.
     */
    @Unique
    private static void writeAmmoToSlot(ItemContainer hotbar, byte slot, float ammo) {
        ItemStack current = hotbar.getItemStack(slot);
        if (current == null || current.isEmpty()) return;

        String itemId = current.getItemId();
        if (itemId == null || !itemId.contains("Crossbow")) return;

        Float savedValue = current.getFromMetadataOrNull(LOADED_AMMO_KEY, Codec.FLOAT);
        float savedFloat = (savedValue != null) ? savedValue : 0f;

        if (Math.abs(savedFloat - ammo) < 0.001f) {
            return; // no change
        }

        ItemStack updated;
        if (ammo > 0) {
            updated = current.withMetadata(LOADED_AMMO_KEY, Codec.FLOAT, ammo);
        } else if (savedValue != null) {
            updated = current.withMetadata(LOADED_AMMO_KEY, Codec.FLOAT, null);
        } else {
            return;
        }

        hotbar.setItemStackForSlot(slot, updated);
        // Ammo metadata updated
    }
}
