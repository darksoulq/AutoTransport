package com.github.darksoulq.auto_transport.device.items;

import com.MT.triggersUtility.TUMaths;
import com.MT.xxxtrigger50xxx.Devices.Device;
import com.MT.xxxtrigger50xxx.MineMain;
import com.github.darksoulq.auto_transport.device.FuelPump;
import com.github.darksoulq.auto_transport.item.CustomItem;
import com.github.darksoulq.auto_transport.item.CustomItemRegistry;
import nl.mtvehicles.core.events.VehicleEnterEvent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class FuelNozzleItem implements CustomItem {

    public static final String ID = "fuel_nozzle";
    public static final NamespacedKey PUMP_LOC_KEY = new NamespacedKey(MineMain.getPlugin(), "pump_location");

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public ItemStack create(Object... context) {
        Location pumpLoc = (Location) context[0];
        ItemStack item = new ItemStack(Material.LEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Fuel Nozzle");
            meta.getPersistentDataContainer().set(CustomItemRegistry.CUSTOM_ITEM_KEY, PersistentDataType.STRING, ID);
            meta.getPersistentDataContainer().set(PUMP_LOC_KEY, PersistentDataType.STRING, TUMaths.locationString(pumpLoc));
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItem(event.getHand());
        FuelPump pump = getPumpFromItem(hand);

        if (pump != null) {
            event.setCancelled(true);
            pump.handleVehicleConnection(player, event.getRightClicked());
        }
    }

    @Override
    public void onDrop(PlayerDropItemEvent event) {
        FuelPump pump = getPumpFromItem(event.getItemDrop().getItemStack());
        if (pump != null) {
            event.setCancelled(true);
            event.getItemDrop().remove();
            pump.dropNozzle(event.getPlayer(), true);
        }
    }

    @Override
    public void onHold(PlayerItemHeldEvent event) {
        ItemStack prev = event.getPlayer().getInventory().getItem(event.getPreviousSlot());
        FuelPump pump = getPumpFromItem(prev);
        if (pump != null) {
            pump.dropNozzle(event.getPlayer(), false);
            event.getPlayer().getInventory().setItem(event.getPreviousSlot(), new ItemStack(Material.AIR));
        }
    }

    @Override
    public void onVehicleEnter(VehicleEnterEvent event) {
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        FuelPump pump = getPumpFromItem(hand);

        if (pump != null) {
            event.setCancelled(true);
            pump.handleVehicleConnection(player, event.getLicensePlate());
        }
    }

    private FuelPump getPumpFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String locStr = item.getItemMeta().getPersistentDataContainer().get(PUMP_LOC_KEY, PersistentDataType.STRING);
        if (locStr == null) return null;

        Device device = Device.getDevice(TUMaths.getStringLocation(locStr));
        return device instanceof FuelPump ? (FuelPump) device : null;
    }
}