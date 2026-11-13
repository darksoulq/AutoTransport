package com.github.darksoulq.auto_transport.util;

import com.MT.xxxtrigger50xxx.Devices.Device;
import com.MT.xxxtrigger50xxx.Guide.ItemMenu;
import org.bukkit.inventory.ItemStack;

import java.util.function.Supplier;

public class DeviceRegistration {

    private final String id;
    private String name;
    private String description = "";
    private String modelData = "none";
    private Class<? extends Device> deviceClass;
    private ItemMenu.Category category = ItemMenu.Category.LOGISTICS;
    private Supplier<Device> prototypeSupplier;

    public DeviceRegistration(String id) {
        this.id = id;
        this.name = id;
    }

    public DeviceRegistration name(String name) {
        this.name = name;
        return this;
    }

    public DeviceRegistration description(String description) {
        this.description = description;
        return this;
    }

    public DeviceRegistration modelData(String modelData) {
        this.modelData = modelData;
        return this;
    }

    public DeviceRegistration deviceClass(Class<? extends Device> deviceClass) {
        this.deviceClass = deviceClass;
        return this;
    }

    public DeviceRegistration category(ItemMenu.Category category) {
        this.category = category;
        return this;
    }

    public DeviceRegistration prototype(Supplier<Device> prototypeSupplier) {
        this.prototypeSupplier = prototypeSupplier;
        return this;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getModelData() {
        return modelData;
    }

    public Class<? extends Device> getDeviceClass() {
        return deviceClass;
    }

    public ItemMenu.Category getCategory() {
        return category;
    }

    public Supplier<Device> getPrototypeSupplier() {
        return prototypeSupplier;
    }

    public ItemStack getPrototypeStack() {
        if (prototypeSupplier == null) return null;
        Device proto = prototypeSupplier.get();
        return proto != null ? proto.getDeviceStack() : null;
    }
}