package morgott.crossbowsavearrow.mixin;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.bson.BsonDocument;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import javax.annotation.Nullable;

/**
 * Makes isEquivalentType() ignore our metadata keys (LoadedAmmo, CrossbowUUID).
 *
 * Without this, writing metadata to the active hotbar slot triggers
 * cancelOnItemChange in the interaction system, breaking reload chains.
 *
 * Uses @Overwrite because @Inject with CallbackInfoReturnable causes
 * NoClassDefFoundError at runtime (class not available in server classloader).
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Shadow
    @Final
    @Nullable
    private BsonDocument metadata;

    @Shadow
    @Final
    private String itemId;

    @Unique
    private static final String LOADED_AMMO_KEY = "LoadedAmmo";

    @Unique
    private static final String CROSSBOW_UUID_KEY = "CrossbowUUID";

    /**
     * @author CrossbowSaveArrow
     * @reason Ignore LoadedAmmo and CrossbowUUID when comparing metadata
     *         to prevent cancelOnItemChange from breaking reload chains.
     */
    @Overwrite
    public boolean isEquivalentType(@Nullable ItemStack other) {
        if (other == null) {
            return false;
        }
        if (!this.itemId.equals(other.getItemId())) {
            return false;
        }

        BsonDocument thisMeta = this.metadata;
        BsonDocument otherMeta = ((ItemStackMixin) (Object) other).metadata;

        // For non-crossbow items, use original logic
        if (!this.itemId.contains("Crossbow")) {
            if (thisMeta == null) {
                return otherMeta == null;
            }
            return thisMeta.equals(otherMeta);
        }

        // For crossbows, compare metadata ignoring our keys
        BsonDocument cleanA = stripOurKeys(thisMeta);
        BsonDocument cleanB = stripOurKeys(otherMeta);

        if (cleanA == null && cleanB == null) {
            return true;
        }
        if (cleanA == null || cleanB == null) {
            return false;
        }
        return cleanA.equals(cleanB);
    }

    @Unique
    @Nullable
    private static BsonDocument stripOurKeys(@Nullable BsonDocument doc) {
        if (doc == null) {
            return null;
        }
        if (!doc.containsKey(LOADED_AMMO_KEY) && !doc.containsKey(CROSSBOW_UUID_KEY)) {
            return doc;
        }
        BsonDocument clean = doc.clone();
        clean.remove(LOADED_AMMO_KEY);
        clean.remove(CROSSBOW_UUID_KEY);
        if (clean.isEmpty()) {
            return null;
        }
        return clean;
    }
}
