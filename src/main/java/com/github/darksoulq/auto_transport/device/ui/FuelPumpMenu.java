package com.github.darksoulq.auto_transport.device.ui;

import com.github.darksoulq.auto_transport.device.AbstractPump;
import com.github.darksoulq.auto_transport.util.VaultHook;
import com.MT.xxxtrigger50xxx.MineUtil;
import com.MT.triggersUtility.TUItems;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

@SuppressWarnings("deprecation")
public class FuelPumpMenu extends DeviceMenu {
    private final AbstractPump pump;

    public FuelPumpMenu(AbstractPump pump) {
        super(pump);
        this.pump = pump;
    }

    @Override
    public void setup() {
        bind(11, (event, player) -> {
            if (!pump.isAuthorized(player)) {
                MineUtil.sendMessage(player, ChatColor.RED + "You do not have permission to modify this pump.");
                return;
            }
            pump.setFuelCost(Math.max(0.0, pump.getFuelCost() - (event.isShiftClick() ? 1.0 : 0.1)));
            render();
            pump.visualUpdate(true);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        });

        bind(15, (event, player) -> {
            if (!pump.isAuthorized(player)) {
                MineUtil.sendMessage(player, ChatColor.RED + "You do not have permission to modify this pump.");
                return;
            }
            pump.setFuelCost(pump.getFuelCost() + (event.isShiftClick() ? 1.0 : 0.1));
            render();
            pump.visualUpdate(true);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        });

        bind(22, (event, player) -> {
            if (!pump.isAuthorized(player)) {
                MineUtil.sendMessage(player, ChatColor.RED + "You do not have permission to modify this pump.");
                return;
            }
            double stored = pump.getStoredMoney();
            if (stored > 0) {
                Economy econ = VaultHook.getEconomy();
                if (econ != null) {
                    econ.depositPlayer(player, stored);
                    MineUtil.sendMessage(player, ChatColor.GREEN + "Withdrew $" + String.format("%.2f", stored));
                    pump.setStoredMoney(0.0);
                    render();
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                }
            }
        });
    }

    @Override
    public void render() {
        Inventory inv = device.getInventory();
        if (inv == null) return;

        ItemStack bg = TUItems.createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, bg);
        }

        List<String> lore = List.of(
            ChatColor.WHITE + "Stored Fuel: " + pump.getStoredLiquid(pump.getLiquidType()) + " / " + pump.getMaxLiquid(pump.getLiquidType()) + " L",
            ChatColor.WHITE + "Price Per Liter: $" + String.format("%.2f", pump.getFuelCost()),
            ChatColor.WHITE + "Stored Money: $" + String.format("%.2f", pump.getStoredMoney())
        );

        inv.setItem(13, TUItems.createItem(Material.LAVA_BUCKET, ChatColor.GOLD + "Pump Status", lore));

        inv.setItem(11, TUItems.createItem(Material.RED_STAINED_GLASS_PANE, ChatColor.RED + "Decrease Price",
            List.of(ChatColor.GRAY + "Click: -$0.10", ChatColor.GRAY + "Shift-Click: -$1.00")));

        inv.setItem(15, TUItems.createItem(Material.GREEN_STAINED_GLASS_PANE, ChatColor.GREEN + "Increase Price",
            List.of(ChatColor.GRAY + "Click: +$0.10", ChatColor.GRAY + "Shift-Click: +$1.00")));

        inv.setItem(22, TUItems.createItem(Material.GOLD_INGOT, ChatColor.GOLD + "Withdraw Money",
            List.of(ChatColor.GRAY + "Click to withdraw $" + String.format("%.2f", pump.getStoredMoney()))));
    }
}