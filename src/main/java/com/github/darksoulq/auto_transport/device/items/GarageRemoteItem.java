package com.github.darksoulq.auto_transport.device.items;

import com.MT.triggersUtility.TUMaths;
import com.MT.xxxtrigger50xxx.Devices.Device;
import com.MT.xxxtrigger50xxx.MineMain;
import com.MT.xxxtrigger50xxx.MineUtil;
import com.github.darksoulq.auto_transport.device.GarageDoor;
import com.github.darksoulq.auto_transport.item.CustomItem;
import com.github.darksoulq.auto_transport.item.CustomItemRegistry;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class GarageRemoteItem implements CustomItem {

    public static final String ID = "garage_remote";
    public static final NamespacedKey TARGET_LOC_KEY = new NamespacedKey(MineMain.getPlugin(), "garage_target");

    @Override
    public String getId() {
        return ID;
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public ItemStack create(Object... context) {
        ItemStack item = new ItemStack(Material.IRON_NUGGET);

        item.setData(DataComponentTypes.ITEM_NAME, Component.text("Garage Remote").color(NamedTextColor.AQUA));
        item.setData(DataComponentTypes.ITEM_MODEL, new NamespacedKey("auto_transport", "garage_remote"));

        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(CustomItemRegistry.CUSTOM_ITEM_KEY, PersistentDataType.STRING, ID);
        });

        return item;
    }

    @Override
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && player.isSneaking()) {
            Device dev = Device.getDevice(event.getClickedBlock().getLocation());
            if (dev instanceof GarageDoor gd) {
                if (!gd.isAuthorized(player)) {
                    MineUtil.sendMessage(player, ChatColor.RED + "You do not have permission to link this door.");
                    return;
                }
                
                hand.editMeta(meta -> {
                    meta.getPersistentDataContainer().set(TARGET_LOC_KEY, PersistentDataType.STRING, TUMaths.locationString(gd.getLocationREADONLY()));
                });
                
                MineUtil.sendMessage(player, ChatColor.GREEN + "Remote linked to Garage Door.");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 2.0f);
                event.setCancelled(true);
            }
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            String locStr = null;
            if (hand.hasItemMeta()) {
                locStr = hand.getItemMeta().getPersistentDataContainer().get(TARGET_LOC_KEY, PersistentDataType.STRING);
            }

            if (locStr == null) {
                MineUtil.sendMessage(player, ChatColor.RED + "This remote is not linked. Sneak-Right-Click a Garage Door core to link it.");
                return;
            }

            Location targetLoc = TUMaths.getStringLocation(locStr);
            Device dev = Device.getDevice(targetLoc);
            
            if (dev instanceof GarageDoor gd) {
                if (!gd.isAuthorized(player)) {
                    MineUtil.sendMessage(player, ChatColor.RED + "You do not have permission to operate this door.");
                    return;
                }
                
                boolean newState = !gd.isOpen();
                gd.toggleChain(newState);
                
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.5f);
            } else {
                MineUtil.sendMessage(player, ChatColor.RED + "The linked Garage Door no longer exists.");
            }
        }
    }
}