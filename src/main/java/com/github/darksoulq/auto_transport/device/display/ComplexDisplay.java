package com.github.darksoulq.auto_transport.device.display;

import com.github.darksoulq.auto_transport.multiblock.PlacementContext;
import org.bukkit.entity.Display;
import java.util.List;

public interface ComplexDisplay {
    DisplayModel getDisplayModel(PlacementContext context);
    String getActiveState();
    void setSpawnedDisplays(List<Display> displays);
    List<Display> getSpawnedDisplays();
    default void tickAnimations() {}
}