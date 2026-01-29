package morgott.crossbowsavearrow;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Shared state between mixins using System.getProperties() as cross-classloader storage.
 *
 * Mixin classes are loaded by a separate classloader that cannot see non-mixin classes
 * from the same JAR. System.getProperties() (a Hashtable) is accessible from any classloader
 * and can store arbitrary Objects (not just Strings).
 *
 * Each shared object is lazily initialized and stored under a unique key.
 */
@SuppressWarnings("unchecked")
public final class CrossbowSharedState {

    private CrossbowSharedState() {}

    private static final String ENTITY_MAP_KEY = "crossbowsavearrow.entityMap";
    private static final String PENDING_ARROW_KEY = "crossbowsavearrow.pendingArrow";
    private static final String PENDING_CONTAINER_KEY = "crossbowsavearrow.pendingContainer";

    /**
     * Maps EntityStatMap → LivingEntity (WeakHashMap).
     * Populated by StatModifiersManagerMixin, used by EntityStatMapMixin.
     */
    public static Map<Object, Object> getEntityMap() {
        Object obj = System.getProperties().get(ENTITY_MAP_KEY);
        if (obj instanceof Map) {
            return (Map<Object, Object>) obj;
        }
        Map<Object, Object> map = new WeakHashMap<>();
        System.getProperties().put(ENTITY_MAP_KEY, map);
        return map;
    }

    /**
     * Deferred arrow removal — set by ModifyInventoryInteractionMixin,
     * consumed by EntityStatMapMixin after ammo increase.
     */
    public static ThreadLocal<Object> getPendingArrowRemoval() {
        Object obj = System.getProperties().get(PENDING_ARROW_KEY);
        if (obj instanceof ThreadLocal) {
            return (ThreadLocal<Object>) obj;
        }
        ThreadLocal<Object> tl = new ThreadLocal<>();
        System.getProperties().put(PENDING_ARROW_KEY, tl);
        return tl;
    }

    public static ThreadLocal<Object> getPendingArrowContainer() {
        Object obj = System.getProperties().get(PENDING_CONTAINER_KEY);
        if (obj instanceof ThreadLocal) {
            return (ThreadLocal<Object>) obj;
        }
        ThreadLocal<Object> tl = new ThreadLocal<>();
        System.getProperties().put(PENDING_CONTAINER_KEY, tl);
        return tl;
    }

    public static void clearPending() {
        getPendingArrowRemoval().remove();
        getPendingArrowContainer().remove();
    }
}
