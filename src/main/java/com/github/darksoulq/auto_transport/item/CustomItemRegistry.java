package com.github.darksoulq.auto_transport.item;

import com.MT.xxxtrigger50xxx.MineMain;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;

public class CustomItemRegistry {

    public static final NamespacedKey CUSTOM_ITEM_KEY = new NamespacedKey(MineMain.getPlugin(), "custom_item_id");
    private static final Map<String, CustomItem> registry = new HashMap<>();

    public static void register(CustomItem item) {
        registry.put(item.getId(), item);
    }

    public static CustomItem get(String id) {
        return registry.get(id);
    }

    public static CustomItem getFromStack(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        String id = stack.getItemMeta().getPersistentDataContainer().get(CUSTOM_ITEM_KEY, PersistentDataType.STRING);
        return id != null ? registry.get(id) : null;
    }

    public static boolean isCustomItem(ItemStack stack, String id) {
        CustomItem item = getFromStack(stack);
        return item != null && item.getId().equals(id);
    }
}