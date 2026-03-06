package com.github.darksoulq.auto_transport.device;

import com.MT.xxxtrigger50xxx.Devices.Device;
import org.bukkit.Location;

import java.util.ArrayList;

// TODO:  Make it place a multiblock, handle breaking of any part of the block, and implement "pipe display".
public class FuelPump extends Device {
    public static final String CLASS_SPACED_NAME = "Fuel Pump";
    private String connectedVehicle = null;
    private boolean isConnected = false;
    private double fuelCost = 102;

    public FuelPump(Location location) {
        super(location);
        this.deviceName = CLASS_SPACED_NAME;
        this.setMaterial("BARREL");
        this.setActionPower(5);
        this.setIdlePower(0);
        this.setActionPower(1);
        this.setOpenable(true);
        this.setUseUI(true);
    }

    @Override
    public ArrayList<String> stackDescription() {
        return null;
    }

    @Override
    public void updateUI() {}

    @Override
    public void action() {}

    public String getConnectedVehicle() {
        return connectedVehicle;
    }

    public void setConnectedVehicle(String connectedVehicle) {
        this.connectedVehicle = connectedVehicle;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void setConnected(boolean connected) {
        isConnected = connected;
    }

    public double getFuelCost() {
        return fuelCost;
    }

    public void setFuelCost(double fuelCost) {
        this.fuelCost = fuelCost;
    }
}
