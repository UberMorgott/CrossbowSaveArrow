package morgott.crossbowsavearrow.mixin;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.ModifyInventoryInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.logging.Logger;

/**
 * Blocks arrow return to inventory when switching away from crossbow.
 * This prevents arrow duplication.
 */
@Mixin(ModifyInventoryInteraction.class)
public abstract class ModifyInventoryInteractionMixin {

    @Unique
    private static final Logger LOGGER = Logger.getLogger("CrossbowSaveArrow");

    @Redirect(
            method = "firstRun",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/hypixel/hytale/server/core/inventory/container/SimpleItemContainer;addOrDropItemStack(Lcom/hypixel/hytale/component/ComponentAccessor;Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/server/core/inventory/container/ItemContainer;Lcom/hypixel/hytale/server/core/inventory/ItemStack;)Z"
            ),
            require = 0
    )
    private boolean redirectAddItemToInventory(
            ComponentAccessor<EntityStore> accessor,
            Ref<EntityStore> ref,
            ItemContainer container,
            ItemStack itemStack
    ) {
        // Check if this is an arrow being added back to inventory
        if (itemStack != null) {
            String itemId = itemStack.getItemId();
            if (itemId != null && itemId.contains("Arrow")) {
                // Check if player is holding a crossbow (switching FROM)
                if (accessor != null && EntityUtils.getEntity(ref, accessor) instanceof LivingEntity livingEntity) {
                    ItemStack itemInHand = livingEntity.getInventory().getItemInHand();
                    if (itemInHand != null && !itemInHand.isEmpty()) {
                        String heldItemId = itemInHand.getItemId();
                        if (heldItemId != null && heldItemId.contains("Crossbow")) {
                            // BLOCK arrow return - ammo is saved to metadata by StatModifiersManagerMixin
                            LOGGER.info("[CrossbowSaveArrow] BLOCKED arrow return: " + itemId + " x" + itemStack.getQuantity());
                            return false;
                        }
                    }
                }
            }
        }

        // Normal operation
        return SimpleItemContainer.addOrDropItemStack(accessor, ref, container, itemStack);
    }
}
