package com.github.darksoulq.auto_transport.device.display;

import com.MT.xxxtrigger50xxx.MineMain;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DisplayCullingManager {

    private static final Map<UUID, Set<Integer>> playerViews = new ConcurrentHashMap<>();
    private static final Set<Display> trackedDisplays = ConcurrentHashMap.newKeySet();
    private static int taskId = -1;
    private static final double MAX_DIST_SQ = 6400.0;

    public static void start() {
        if (taskId != -1) return;
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(MineMain.getPlugin(), () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                processPlayer(p);
            }
        }, 0L, 10L);
    }

    public static void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        trackedDisplays.clear();
        playerViews.clear();
    }

    public static void track(Display display) {
        display.setVisibleByDefault(false);
        trackedDisplays.add(display);
    }

    public static void untrack(Display display) {
        trackedDisplays.remove(display);
        int id = display.getEntityId();
        for (Set<Integer> view : playerViews.values()) {
            view.remove(id);
        }
    }

    public static void clearPlayer(UUID uuid) {
        playerViews.remove(uuid);
    }

    private static void processPlayer(Player p) {
        UUID pid = p.getUniqueId();
        Set<Integer> currentView = playerViews.computeIfAbsent(pid, k -> new HashSet<>());
        Location pLoc = p.getLocation();

        for (Display d : trackedDisplays) {
            if (!d.isValid()) {
                trackedDisplays.remove(d);
                continue;
            }

            Location dLoc = d.getLocation();
            if (!dLoc.getWorld().equals(pLoc.getWorld())) {
                hide(p, d, currentView);
                continue;
            }

            if (pLoc.distanceSquared(dLoc) > MAX_DIST_SQ) {
                hide(p, d, currentView);
            } else {
                show(p, d, currentView);
            }
        }
    }

    private static void show(Player p, Display d, Set<Integer> view) {
        if (view.add(d.getEntityId())) {
            p.showEntity(MineMain.getPlugin(), d);
        }
    }

    private static void hide(Player p, Display d, Set<Integer> view) {
        if (view.remove(d.getEntityId())) {
            p.hideEntity(MineMain.getPlugin(), d);
        }
    }
}