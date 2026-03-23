package com.github.darksoulq.auto_transport.device.display;

import org.bukkit.entity.Display;
import java.util.List;

public interface ComplexDisplay {
    DisplayModel getDisplayModel();
    String getActiveState();
    void setSpawnedDisplays(List<Display> displays);
    List<Display> getSpawnedDisplays();
    default void tickAnimations() {}
}