package com.github.darksoulq.auto_transport;

import com.github.darksoulq.auto_transport.device.DeviceEvents;
import com.github.darksoulq.auto_transport.device.FuelPump;
import com.github.darksoulq.auto_transport.device.display.ComplexDisplayManager;
import com.github.darksoulq.auto_transport.device.display.DisplayCullingManager;
import com.github.darksoulq.auto_transport.device.items.FuelNozzleItem;
import com.github.darksoulq.auto_transport.item.CustomItemListener;
import com.github.darksoulq.auto_transport.item.CustomItemRegistry;
import com.github.darksoulq.auto_transport.multiblock.MultiblockPreviewManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class AutoTransport extends JavaPlugin {

    @Override
    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(new DeviceEvents(), this);
        Bukkit.getServer().getPluginManager().registerEvents(new CustomItemListener(), this);

        CustomItemRegistry.register(new FuelNozzleItem());

        MultiblockPreviewManager.start();
        DisplayCullingManager.start();
        ComplexDisplayManager.start();

        FuelPump.addDevice();
    }

    @Override
    public void onDisable() {
        MultiblockPreviewManager.stop();
        DisplayCullingManager.stop();
        ComplexDisplayManager.stop();
    }
}