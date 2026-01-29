package morgott.crossbowsavearrow.mixin;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.ModifyInventoryInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import javax.annotation.Nonnull;

/**
 * Defers vanilla arrow removal and blocks arrow return for crossbows.
 *
 * - Removal deferred: vanilla removes arrows BEFORE ammo increases (risky on interrupt).
 *   We save the ItemStack to System.getProperties() ThreadLocal and let EntityStatMapMixin
 *   perform the actual removal AFTER ammo increases.
 * - Return blocked: vanilla returns arrows to inventory on SwapFrom.
 *   We keep them in crossbow metadata instead.
 *
 * Shared state is stored in System.getProperties() to work across classloaders.
 */
@Mixin(ModifyInventoryInteraction.class)
public abstract class ModifyInventoryInteractionMixin {

    @Unique
    private static final String PROP_PENDING_ARROW = "crossbowsavearrow.pendingArrow";
    @Unique
    private static final String PROP_PENDING_CONTAINER = "crossbowsavearrow.pendingContainer";

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
     * Defer arrow removal from inventory during crossbow loading.
     * Arrow consumption is handled by EntityStatMapMixin after ammo actually increases.
     */
    @Redirect(
            method = "firstRun",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/hypixel/hytale/server/core/inventory/container/CombinedItemContainer;removeItemStack(Lcom/hypixel/hytale/server/core/inventory/ItemStack;ZZ)Lcom/hypixel/hytale/server/core/inventory/transaction/ItemStackTransaction;"
            ),
            require = 0
    )
    private ItemStackTransaction redirectRemoveItemStack(
            CombinedItemContainer instance,
            ItemStack itemToRemove,
            boolean allOrNothing,
            boolean filter,
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nonnull CooldownHandler cooldownHandler
    ) {
        if (itemToRemove != null) {
            String removeId = itemToRemove.getItemId();
            if (removeId != null && removeId.contains("Arrow")) {
                Ref<EntityStore> ref = context.getEntity();
                ComponentAccessor<EntityStore> accessor = context.getCommandBuffer();
                if (accessor != null && EntityUtils.getEntity(ref, accessor) instanceof LivingEntity livingEntity) {
                    ItemStack itemInHand = livingEntity.getInventory().getItemInHand();
                    if (itemInHand != null && !itemInHand.isEmpty()) {
                        String heldId = itemInHand.getItemId();
                        if (heldId != null && heldId.contains("Crossbow")) {
                            getPendingArrow().set(itemToRemove);
                            getPendingContainer().set(instance);
                            // Arrow removal deferred to EntityStatMapMixin
                            return new ItemStackTransaction(true, null, itemToRemove, null, allOrNothing, filter, java.util.Collections.emptyList());
                        }
                    }
                }
            }
        }

        return instance.removeItemStack(itemToRemove, allOrNothing, filter);
    }

    /**
     * Block arrow return to inventory during SwapFrom.
     */
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
        if (itemStack != null) {
            String itemId = itemStack.getItemId();
            if (itemId != null && itemId.contains("Arrow")) {
                if (accessor != null && EntityUtils.getEntity(ref, accessor) instanceof LivingEntity livingEntity) {
                    ItemStack itemInHand = livingEntity.getInventory().getItemInHand();
                    if (itemInHand != null && !itemInHand.isEmpty()) {
                        String heldItemId = itemInHand.getItemId();
                        if (heldItemId != null && heldItemId.contains("Crossbow")) {
                            // Arrow return blocked, kept in crossbow metadata
                            return false;
                        }
                    }
                }
            }
        }

        return SimpleItemContainer.addOrDropItemStack(accessor, ref, container, itemStack);
    }
}
