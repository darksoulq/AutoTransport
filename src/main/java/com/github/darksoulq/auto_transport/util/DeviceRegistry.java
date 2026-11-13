package com.github.darksoulq.auto_transport.util;

import com.MT.xxxtrigger50xxx.API.MTAPI;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DeviceRegistry {

    private final JavaPlugin plugin;
    private final List<DeviceRegistration> registrations = new ArrayList<>();

    public DeviceRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(DeviceRegistration registration) {
        registrations.add(registration);
    }

    public void initialize() {
        File devicesFile = new File(plugin.getDataFolder(), "devices.yml");
        if (!devicesFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                devicesFile.createNewFile();
            } catch (Exception ignored) {
            }
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(devicesFile);
        boolean needsSave = false;

        for (DeviceRegistration reg : registrations) {
            String basePath = "Devices." + reg.getId();
            
            if (!config.contains(basePath)) {
                config.set(basePath + ".Name", reg.getName());
                config.set(basePath + ".Description", reg.getDescription());
                config.set(basePath + ".Model Data", reg.getModelData());
                needsSave = true;
            }
        }

        if (needsSave) {
            try {
                config.save(devicesFile);
            } catch (Exception ignored) {
            }
        }

        MTAPI.loadCustomDevices(devicesFile);

        for (DeviceRegistration reg : registrations) {
            if (reg.getDeviceClass() != null && reg.getPrototypeStack() != null) {
                MTAPI.addCustomDeviceClass(
                    reg.getName(),
                    reg.getDeviceClass(),
                    reg.getCategory(),
                    reg.getPrototypeStack()
                );
            }
        }
    }
}