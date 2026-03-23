package com.github.darksoulq.auto_transport.device;

import com.MT.xxxtrigger50xxx.Devices.Device;
import com.MT.xxxtrigger50xxx.Devices.Device.LiquidType;
import java.util.ArrayList;
import java.util.List;

public class DeviceSettings {

    private final Device device;

    private DeviceSettings(Device device) {
        this.device = device;
    }

    public static DeviceSettings configure(Device device) {
        return new DeviceSettings(device);
    }

    public DeviceSettings power(int idle, int action, int gridRange) {
        device.setIdlePower(idle);
        device.setActionPower(action);
        device.setGridRange(gridRange);
        return this;
    }

    public DeviceSettings processing(int actionTimer, int pollutionGen, int moduleSlots) {
        device.setActionTimer(actionTimer);
        device.setPollutionGen(pollutionGen);
        device.setModuleSlots(moduleSlots);
        return this;
    }

    public DeviceSettings capacity(LiquidType type, int amount) {
        device.addLiquidType(type, amount);
        return this;
    }

    public DeviceSettings uiAuto(boolean openable) {
        device.setUseUI(true);
        device.setOpenable(openable);
        device.useAutoSlots();
        device.useAutoUI();
        return this;
    }

    public DeviceSettings uiCustom(int[] inputs, int[] outputs, int[] interact, boolean openable) {
        device.setUseUI(true);
        device.setOpenable(openable);
        if (inputs != null) {
            List<Integer> list = new ArrayList<>();
            for (int i : inputs) list.add(i);
            device.setInputSlots(new ArrayList<>(list));
        }
        if (outputs != null) {
            List<Integer> list = new ArrayList<>();
            for (int i : outputs) list.add(i);
            device.setOutputSlots(new ArrayList<>(list));
        }
        if (interact != null) {
            List<Integer> list = new ArrayList<>();
            for (int i : interact) list.add(i);
            device.setClickableSlots(new ArrayList<>(list));
        }
        return this;
    }

    public DeviceSettings access(boolean isPublic) {
        device.setPublic(isPublic);
        return this;
    }

    public DeviceSettings inputs(boolean requireSpecific) {
        device.useSpecificInputs = requireSpecific;
        return this;
    }

    public void apply() {}
}