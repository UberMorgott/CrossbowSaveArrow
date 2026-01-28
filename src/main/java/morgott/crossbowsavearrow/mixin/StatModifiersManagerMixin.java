package morgott.crossbowsavearrow.mixin;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.StatModifiersManager;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Logger;

/**
 * Mixin for per-crossbow ammo storage.
 * - SAVE: Only when switching FROM crossbow (HEAD inject)
 * - RESTORE: When switching TO crossbow with ammo=0 (RETURN inject)
 */
@Mixin(StatModifiersManager.class)
public abstract class StatModifiersManagerMixin {

    @Unique
    private static final Logger LOGGER = Logger.getLogger("CrossbowSaveArrow");

    @Unique
    private static final String LOADED_AMMO_KEY = "LoadedAmmo";

    // Track slot and ammo for detecting crossbow switches
    @Unique
    private static final Map<EntityStatMap, Byte> lastCrossbowSlot = new WeakHashMap<>();

    @Unique
    private static final Map<EntityStatMap, Float> lastCrossbowAmmo = new WeakHashMap<>();

    // Track if we already restored for this crossbow session
    @Unique
    private static final Map<EntityStatMap, Boolean> hasRestored = new WeakHashMap<>();

    @Inject(method = "recalculateEntityStatModifiers", at = @At("HEAD"))
    private void onBeforeRecalculate(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull EntityStatMap statMap,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor,
            CallbackInfo ci
    ) {
        if (!(EntityUtils.getEntity(ref, componentAccessor) instanceof LivingEntity livingEntity)) {
            return;
        }

        Inventory inventory = livingEntity.getInventory();
        ItemContainer hotbar = inventory.getHotbar();

        if (hotbar == null) {
            return;
        }

        byte currentSlot = inventory.getActiveHotbarSlot();
        Byte oldSlot = lastCrossbowSlot.get(statMap);

        // Check if slot changed and we had a crossbow before
        if (oldSlot != null && oldSlot.byteValue() != currentSlot) {
            // Switching away from previous slot - save ammo to the OLD crossbow
            ItemStack oldItem = hotbar.getItemStack(oldSlot);
            if (oldItem != null && !oldItem.isEmpty()) {
                String oldItemId = oldItem.getItemId();
                if (oldItemId != null && oldItemId.contains("Crossbow")) {
                    // Get the tracked ammo (what we last saw)
                    Float ammoToSave = lastCrossbowAmmo.get(statMap);

                    if (ammoToSave != null && ammoToSave > 0) {
                        ItemStack updatedItem = oldItem.withMetadata(LOADED_AMMO_KEY, Codec.FLOAT, ammoToSave);
                        hotbar.setItemStackForSlot(oldSlot, updatedItem);
                        LOGGER.info("[CrossbowSaveArrow] SAVED on switch: slot=" + oldSlot + " ammo=" + ammoToSave);
                    } else {
                        // Clear metadata if no ammo
                        ItemStack clearedItem = oldItem.withMetadata(LOADED_AMMO_KEY, Codec.FLOAT, null);
                        hotbar.setItemStackForSlot(oldSlot, clearedItem);
                        LOGGER.info("[CrossbowSaveArrow] CLEARED on switch: slot=" + oldSlot);
                    }
                }
            }

            // Clear tracking for old crossbow
            lastCrossbowSlot.remove(statMap);
            lastCrossbowAmmo.remove(statMap);
            hasRestored.remove(statMap);
        }
    }

    @Inject(method = "recalculateEntityStatModifiers", at = @At("RETURN"))
    private void onAfterRecalculate(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull EntityStatMap statMap,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor,
            CallbackInfo ci
    ) {
        if (!(EntityUtils.getEntity(ref, componentAccessor) instanceof LivingEntity livingEntity)) {
            return;
        }

        Inventory inventory = livingEntity.getInventory();
        ItemContainer hotbar = inventory.getHotbar();
        byte currentSlot = inventory.getActiveHotbarSlot();

        if (hotbar == null) {
            return;
        }

        ItemStack itemInHand = inventory.getItemInHand();
        if (itemInHand == null || itemInHand.isEmpty()) {
            lastCrossbowSlot.remove(statMap);
            lastCrossbowAmmo.remove(statMap);
            hasRestored.remove(statMap);
            return;
        }

        String itemId = itemInHand.getItemId();
        if (itemId == null || !itemId.contains("Crossbow")) {
            lastCrossbowSlot.remove(statMap);
            lastCrossbowAmmo.remove(statMap);
            hasRestored.remove(statMap);
            return;
        }

        // We have a crossbow in hand
        int ammoIndex = DefaultEntityStatTypes.getAmmo();
        EntityStatValue ammoStat = statMap.get(ammoIndex);

        if (ammoStat == null) {
            return;
        }

        float currentAmmo = ammoStat.get();
        float maxAmmo = ammoStat.getMax();

        // Detect if this is a new crossbow (slot changed or first time)
        Byte prevSlot = lastCrossbowSlot.get(statMap);
        boolean isNewCrossbow = prevSlot == null || prevSlot.byteValue() != currentSlot;

        if (isNewCrossbow) {
            LOGGER.info("[CrossbowSaveArrow] New crossbow in slot " + currentSlot + ", currentAmmo=" + currentAmmo);
            hasRestored.remove(statMap);
        }

        // RESTORE: Only if ammo is 0, we haven't restored yet, and metadata has value
        if (currentAmmo == 0 && maxAmmo > 0 && !Boolean.TRUE.equals(hasRestored.get(statMap))) {
            Float savedAmmo = itemInHand.getFromMetadataOrNull(LOADED_AMMO_KEY, Codec.FLOAT);

            if (savedAmmo != null && savedAmmo > 0) {
                float restoreValue = Math.min(savedAmmo, maxAmmo);
                statMap.setStatValue(EntityStatMap.Predictable.SELF, ammoIndex, restoreValue);
                currentAmmo = restoreValue;
                LOGGER.info("[CrossbowSaveArrow] RESTORED ammo=" + restoreValue);
            }

            hasRestored.put(statMap, Boolean.TRUE);
        }

        // Track current state for future saves (but don't write to metadata now)
        lastCrossbowSlot.put(statMap, currentSlot);
        lastCrossbowAmmo.put(statMap, currentAmmo);
    }
}
