package morgott.crossbowsavearrow;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * CrossbowSaveArrow v1.0.0 - Per-crossbow ammo storage.
 *
 * Each crossbow remembers its loaded arrows in ItemStack metadata.
 * Features:
 * - Multiple crossbows can have different ammo counts
 * - Ammo persists when switching weapons
 * - Ammo persists when dropping/picking up the crossbow
 * - Ammo persists when storing in chests
 *
 * Uses two Mixins:
 * 1. StatModifiersManagerMixin - Saves/restores ammo on weapon switch
 * 2. ModifyInventoryInteractionMixin - Blocks arrow duplication
 */
public class CrossbowSaveArrow extends JavaPlugin {

    private static CrossbowSaveArrow instance;

    public static CrossbowSaveArrow get() {
        return instance;
    }

    public CrossbowSaveArrow(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        getLogger().at(Level.INFO).log("[CrossbowSaveArrow] v1.0.0 Loaded! Each crossbow now stores its own ammo.");
    }

    @Override
    protected void shutdown() {
        getLogger().at(Level.INFO).log("[CrossbowSaveArrow] Stopped!");
    }
}
