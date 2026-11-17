package com.github.darksoulq.auto_transport;

import com.MT.xxxtrigger50xxx.Guide.ItemMenu;
import com.github.darksoulq.auto_transport.device.GarageDoor;
import com.github.darksoulq.mt_multiblocks.core.AddonDeviceRegistry;
import com.github.darksoulq.mt_multiblocks.core.AddonDeviceTemplate;
import org.bukkit.plugin.java.JavaPlugin;

public final class AutoTransport extends JavaPlugin {

    @Override
    public void onEnable() {
        AddonDeviceRegistry deviceRegistry = new AddonDeviceRegistry(this);

        AddonDeviceTemplate garageDoor = new AddonDeviceTemplate(GarageDoor.CLASS_NAME)
            .description("An extendable garage door that can be disguised with solid blocks.")
            .category(ItemMenu.Category.AUTOMATION)
            .deviceClass(GarageDoor.class)
            .prototype(() -> new GarageDoor(null));

        deviceRegistry.register(garageDoor);
        deviceRegistry.apply();
    }
}