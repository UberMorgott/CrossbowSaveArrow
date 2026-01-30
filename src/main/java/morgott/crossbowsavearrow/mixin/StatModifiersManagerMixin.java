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
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Restores ammo from crossbow metadata when player switches to a crossbow.
 * Also populates entityMap so EntityStatMapMixin can find the entity.
 *
 * Metadata writing is handled entirely by EntityStatMapMixin (on every ammo change).
 * This mixin only needs to:
 * 1. Register the EntityStatMap → LivingEntity mapping for EntityStatMapMixin.
 * 2. Restore ammo from crossbow metadata when a crossbow is taken in hand.
 * 3. Assign UUID to crossbows that don't have one.
 *
 * Shared state is stored in System.getProperties() to work across classloaders.
 */
@Mixin(StatModifiersManager.class)
public abstract class StatModifiersManagerMixin {

    @Unique
    private static final String LOADED_AMMO_KEY = "LoadedAmmo";

    @Unique
    private static final String CROSSBOW_UUID_KEY = "CrossbowUUID";

    @Unique
    private static final String PROP_ENTITY_MAP = "crossbowsavearrow.entityMap";

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

    /**
     * After recalculation (weapon switch): populate entityMap, restore ammo, assign UUID.
     */
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

        // Populate entityMap for EntityStatMapMixin (arrow consumption + metadata writes)
        getEntityMap().put(statMap, livingEntity);

        Inventory inventory = livingEntity.getInventory();
        ItemContainer hotbar = inventory.getHotbar();
        byte currentSlot = inventory.getActiveHotbarSlot();

        if (hotbar == null) {
            return;
        }

        ItemStack itemInHand = inventory.getItemInHand();
        if (itemInHand == null || itemInHand.isEmpty()) {
            return;
        }

        // Only process weapons that have our metadata (previously loaded ammo)
        Float savedAmmo = itemInHand.getFromMetadataOrNull(LOADED_AMMO_KEY, Codec.FLOAT);
        if (savedAmmo == null) {
            return;
        }

        // Ensure weapon has UUID
        String uuid = itemInHand.getFromMetadataOrNull(CROSSBOW_UUID_KEY, Codec.STRING);
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
            ItemStack withUUID = itemInHand.withMetadata(CROSSBOW_UUID_KEY, Codec.STRING, uuid);
            hotbar.setItemStackForSlot(currentSlot, withUUID);
            itemInHand = withUUID;
        }

        // Restore ammo from metadata
        int ammoIndex = DefaultEntityStatTypes.getAmmo();
        EntityStatValue ammoStat = statMap.get(ammoIndex);
        if (ammoStat == null) {
            return;
        }

        float currentAmmo = ammoStat.get();
        float maxAmmo = ammoStat.getMax();

        if (maxAmmo > 0 && savedAmmo > 0) {
            float restoreValue = Math.min(savedAmmo, maxAmmo);
            if (Math.abs(currentAmmo - restoreValue) > 0.001f) {
                statMap.setStatValue(EntityStatMap.Predictable.SELF, ammoIndex, restoreValue);
            }
        }
    }
}
