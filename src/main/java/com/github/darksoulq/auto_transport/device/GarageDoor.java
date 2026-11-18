package com.github.darksoulq.auto_transport.device;

import com.MT.xxxtrigger50xxx.Devices.Device;
import com.MT.xxxtrigger50xxx.MineMain;
import com.github.darksoulq.auto_transport.choice.AnySolidChoice;
import com.github.darksoulq.mt_multiblocks.BlueprintContext;
import com.github.darksoulq.mt_multiblocks.MultiblockBlueprint;
import com.github.darksoulq.mt_multiblocks.OptionalComponent;
import com.github.darksoulq.mt_multiblocks.core.MultiblockDevice;
import com.github.darksoulq.mt_multiblocks.core.PortProxyDevice;
import com.github.darksoulq.mt_multiblocks.port.BasicStructuralPort;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.io.Serial;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GarageDoor extends MultiblockDevice {

    @Serial
    private static final long serialVersionUID = 1L;
    public static final String CLASS_NAME = "Garage Door";
    public static final int MAX_EXTENSION = 10;
    public static final int MAX_WIDTH = 10;
    private static boolean placeListenerRegistered = false;

    private static final int[][] HORIZONTAL_OFFSETS = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    private static final BlockData YELLOW_CONCRETE_DATA = Bukkit.createBlockData(Material.YELLOW_CONCRETE);
    private static final BlockData LIGHT_BLUE_CONCRETE_DATA = Bukkit.createBlockData(Material.LIGHT_BLUE_CONCRETE);
    private static final Display.Brightness FULL_BRIGHTNESS = new Display.Brightness(15, 15);
    private static final String AIR_DATA_STRING = Material.AIR.createBlockData().getAsString();

    private static final Map<String, Long> rowTopologyVersions = new ConcurrentHashMap<>();

    private int calculatedHeight = -1;
    private String rowUUID = null;
    private List<String> storedBlockData = new ArrayList<>();
    private boolean isOpen = false;
    private boolean showPreview = true;

    private transient boolean isAnimating;
    private transient int openProgress;
    private transient boolean targetState;
    private transient boolean monitorStarted;
    private transient boolean stateInitialized;
    private transient int monitorTaskID;
    private transient int animTaskID;
    private transient boolean wasPowered;
    private transient int tickCounter;

    private transient List<BlockDisplay> rowHighlightDisplays;
    private transient double lastMinX, lastMaxX, lastMinY, lastMaxY, lastMinZ, lastMaxZ;
    private transient int lastOutlineRowHeight = -1;

    private transient List<GarageDoor> cachedConnectedDoors;
    private transient GarageDoor cachedMasterDoor;
    private transient long cachedTopologyVersion = -1L;

    private transient List<BlockData> parsedStoredBlockData;
    private transient MultiblockBlueprint cachedBlueprint;
    private transient int cachedBlueprintHeight = -1;

    public GarageDoor(Location loc) {
        super(loc);
        this.deviceName = CLASS_NAME;
        this.setMaterial("SMOOTH_STONE");
        this.setIdlePower(2);
        this.setActionPower(0);
        this.setUseUI(true);
        this.setOpenable(false);
    }

    private static void bumpRowVersion(String uuid) {
        if (uuid != null) {
            rowTopologyVersions.compute(uuid, (k, v) -> v == null ? 1L : v + 1L);
        }
    }

    @Override
    public MultiblockBlueprint getBlueprint(BlueprintContext context) {
        if (context != null) {
            if (this.calculatedHeight <= 0 && context.getCoreLocation() != null) {
                int h = calculateHeight(context.getCoreLocation());
                if (context.getDevice() == this) {
                    this.calculatedHeight = h;
                }
            }
        }

        int height = Math.max(this.calculatedHeight, 0);
        if (cachedBlueprint != null && cachedBlueprintHeight == height) {
            return cachedBlueprint;
        }

        MultiblockBlueprint blueprint = new MultiblockBlueprint();
        for (int i = 1; i <= height; i++) {
            blueprint.addComponentObj(new Vector(0, -i, 0), new OptionalComponent(new AnySolidChoice(), new BasicStructuralPort()));
        }

        this.cachedBlueprintHeight = height;
        this.cachedBlueprint = blueprint;
        return blueprint;
    }

    private int calculateHeight(Location core) {
        if (core == null || !core.isWorldLoaded()) {
            return storedBlockData.size();
        }
        World world = core.getWorld();
        int bx = core.getBlockX();
        int by = core.getBlockY();
        int bz = core.getBlockZ();

        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
            return storedBlockData.size();
        }

        Location coreReadOnly = this.getLocationREADONLY();
        int storedSize = storedBlockData.size();
        int height = 0;

        for (int i = 1; i <= MAX_EXTENSION; i++) {
            Location check = new Location(world, bx, by - i, bz);

            Device dev = Device.getDevice(check);
            if (dev instanceof PortProxyDevice proxy && proxy.getCoreDevice() != null && proxy.getCoreDevice().getLocationREADONLY().equals(coreReadOnly)) {
                height++;
                continue;
            }

            boolean isStored = (i - 1 < storedSize);
            if (!isStored) {
                Block block = world.getBlockAt(bx, by - i, bz);
                if (!block.getType().isAir() && !block.isReplaceable()) {
                    break;
                }
            }
            height++;
        }
        return Math.max(height, storedSize);
    }

    public void updateOutlineState() {
        if (isRemoved() || !isChunkLoaded()) {
            clearRowHighlights();
            return;
        }

        List<GarageDoor> connected = getAllConnectedDoors();
        updateOutlineState(connected);
    }

    private void updateOutlineState(List<GarageDoor> connected) {
        if (isRemoved() || !isChunkLoaded()) {
            clearRowHighlights();
            return;
        }

        GarageDoor master = getMasterDoor(connected);
        if (master != this) {
            clearRowHighlights();
            return;
        }

        int rowHeight = getRowMaxHeight(connected);
        if (!showPreview || rowHeight <= 0) {
            clearRowHighlights();
            return;
        }

        Location selfLoc = getLocationREADONLY();
        double minX = selfLoc.getX();
        double maxX = minX;
        double minY = selfLoc.getY();
        double maxY = minY;
        double minZ = selfLoc.getZ();
        double maxZ = minZ;

        for (int i = 0, size = connected.size(); i < size; i++) {
            Location loc = connected.get(i).getLocationREADONLY();
            double x = loc.getX();
            double y = loc.getY();
            double z = loc.getZ();
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
            if (z < minZ) minZ = z;
            if (z > maxZ) maxZ = z;
        }

        double t = 0.03;
        double pad = 0.01;

        double x1 = minX - pad;
        double y1 = minY - pad;
        double z1 = minZ - pad;
        double x2 = maxX + 1 + pad;
        double y2 = maxY + 1 + pad;
        double z2 = maxZ + 1 + pad;

        double ey1 = minY - rowHeight - pad;
        double ey2 = minY - pad;

        boolean needsUpdate = rowHighlightDisplays == null || rowHighlightDisplays.isEmpty() ||
            lastOutlineRowHeight != rowHeight ||
            lastMinX != x1 || lastMaxX != x2 ||
            lastMinY != y1 || lastMaxY != y2 ||
            lastMinZ != z1 || lastMaxZ != z2;

        if (needsUpdate) {
            clearRowHighlights();
            rowHighlightDisplays = new ArrayList<>(24);
            World w = selfLoc.getWorld();

            drawBoundingBox(w, x1, y1, z1, x2, y2, z2, YELLOW_CONCRETE_DATA, rowHighlightDisplays, t);
            drawBoundingBox(w, x1, ey1, z1, x2, ey2, z2, LIGHT_BLUE_CONCRETE_DATA, rowHighlightDisplays, t);

            lastMinX = x1; lastMaxX = x2;
            lastMinY = y1; lastMaxY = y2;
            lastMinZ = z1; lastMaxZ = z2;
            lastOutlineRowHeight = rowHeight;
        }
    }

    private void drawBoundingBox(World w, double x1, double y1, double z1, double x2, double y2, double z2, BlockData blockData, List<BlockDisplay> list, double t) {
        list.add(spawnLine(w, x1, y1, z1, (x2 - x1), t, t, blockData));
        list.add(spawnLine(w, x1, y1, z2 - t, (x2 - x1), t, t, blockData));
        list.add(spawnLine(w, x1, y1, z1, t, t, (z2 - z1), blockData));
        list.add(spawnLine(w, x2 - t, y1, z1, t, t, (z2 - z1), blockData));

        list.add(spawnLine(w, x1, y2 - t, z1, (x2 - x1), t, t, blockData));
        list.add(spawnLine(w, x1, y2 - t, z2 - t, (x2 - x1), t, t, blockData));
        list.add(spawnLine(w, x1, y2 - t, z1, t, t, (z2 - z1), blockData));
        list.add(spawnLine(w, x2 - t, y2 - t, z1, t, t, (z2 - z1), blockData));

        list.add(spawnLine(w, x1, y1, z1, t, (y2 - y1), t, blockData));
        list.add(spawnLine(w, x2 - t, y1, z1, t, (y2 - y1), t, blockData));
        list.add(spawnLine(w, x1, y1, z2 - t, t, (y2 - y1), t, blockData));
        list.add(spawnLine(w, x2 - t, y1, z2 - t, t, (y2 - y1), t, blockData));
    }

    private BlockDisplay spawnLine(World world, double x, double y, double z, double sx, double sy, double sz, BlockData blockData) {
        Location loc = new Location(world, x, y, z);
        return world.spawn(loc, BlockDisplay.class, ent -> {
            ent.setBlock(blockData);
            ent.setBrightness(FULL_BRIGHTNESS);
            Transformation tr = ent.getTransformation();
            tr.getScale().set((float) sx, (float) sy, (float) sz);
            ent.setTransformation(tr);
        });
    }

    private void clearRowHighlights() {
        if (rowHighlightDisplays != null) {
            for (int i = 0, size = rowHighlightDisplays.size(); i < size; i++) {
                BlockDisplay bd = rowHighlightDisplays.get(i);
                if (bd != null && bd.isValid()) bd.remove();
            }
            rowHighlightDisplays.clear();
        }
    }

    @Override
    public void postCreate(boolean isPlaced) {
        super.postCreate(isPlaced);

        Location coreLoc = getLocationREADONLY();
        if (coreLoc == null) return;

        if (this.rowUUID == null) {
            String foundUUID = null;
            if (coreLoc.isWorldLoaded()) {
                World world = coreLoc.getWorld();
                int bx = coreLoc.getBlockX();
                int by = coreLoc.getBlockY();
                int bz = coreLoc.getBlockZ();

                for (int[] dir : HORIZONTAL_OFFSETS) {
                    int nx = bx + dir[0];
                    int nz = bz + dir[1];
                    if (world.isChunkLoaded(nx >> 4, nz >> 4)) {
                        Device dev = Device.getDevice(new Location(world, nx, by, nz));
                        if (dev instanceof GarageDoor gd && gd.rowUUID != null) {
                            if (gd.getAllConnectedDoors().size() < MAX_WIDTH) {
                                foundUUID = gd.rowUUID;
                                break;
                            }
                        }
                    }
                }
            }

            if (foundUUID != null) {
                this.rowUUID = foundUUID;
            } else {
                this.rowUUID = UUID.randomUUID().toString();
            }
        }

        bumpRowVersion(this.rowUUID);

        if (!placeListenerRegistered) {
            placeListenerRegistered = true;
            Bukkit.getPluginManager().registerEvents(new Listener() {
                @EventHandler(ignoreCancelled = true)
                public void onBlockPlace(BlockPlaceEvent e) {
                    Block placed = e.getBlock();
                    World world = placed.getWorld();
                    int bx = placed.getX();
                    int by = placed.getY();
                    int bz = placed.getZ();

                    for (int i = 1; i <= MAX_EXTENSION; i++) {
                        Device dev = Device.getDevice(new Location(world, bx, by + i, bz));
                        if (dev instanceof GarageDoor gd) {
                            if (gd.getTargetState() && i <= gd.getRowMaxHeight()) {
                                e.setCancelled(true);
                                e.getPlayer().sendMessage(ChatColor.RED + "You cannot place blocks while the garage door is open!");
                                return;
                            }
                        }
                    }
                }
            }, MineMain.getPlugin());
        }

        if (calculatedHeight <= 0 && isChunkLoaded()) {
            calculatedHeight = calculateHeight(coreLoc);
        }

        if (!stateInitialized) {
            stateInitialized = true;
            if (!storedBlockData.isEmpty()) {
                rebuildParsedBlockDataCache();
                openProgress = storedBlockData.size();
                isOpen = true;
                targetState = true;
            } else {
                openProgress = 0;
                isOpen = false;
                targetState = false;
            }
            wasPowered = targetState;
        }

        List<GarageDoor> initialConnected = getAllConnectedDoors();
        boolean foundSync = false;
        for (int i = 0, size = initialConnected.size(); i < size; i++) {
            GarageDoor gd = initialConnected.get(i);
            if (gd != this && !gd.isRemoved()) {
                this.showPreview = gd.showPreview;
                foundSync = true;
                break;
            }
        }
        if (foundSync) {
            updateUI();
        }

        Bukkit.getScheduler().runTaskLater(MineMain.getPlugin(), () -> {
            if (isRemoved() || !isChunkLoaded()) return;

            if (calculatedHeight <= 0) {
                calculatedHeight = calculateHeight(getLocationREADONLY());
            }

            bumpRowVersion(this.rowUUID);
            updateOutlineState();
        }, 10L);

        if (!monitorStarted) {
            monitorStarted = true;
            monitorTaskID = Bukkit.getScheduler().scheduleSyncRepeatingTask(MineMain.getPlugin(), () -> {
                if (isRemoved()) {
                    Bukkit.getScheduler().cancelTask(monitorTaskID);
                    monitorStarted = false;
                    return;
                }

                if (!isChunkLoaded()) {
                    return;
                }

                if (calculatedHeight <= 0) {
                    calculatedHeight = calculateHeight(getLocationREADONLY());
                    if (calculatedHeight <= 0) return;
                }

                List<GarageDoor> connected = getAllConnectedDoors();
                GarageDoor master = getMasterDoor(connected);

                if (++tickCounter % 10 == 0) {
                    updateOutlineState(connected);
                }

                if (connected.size() > MAX_WIDTH) {
                    this.setFailReason("Row limit exceeded (" + MAX_WIDTH + ")");
                    return;
                } else if (this.getFailReason() != null && this.getFailReason().startsWith("Row limit exceeded")) {
                    this.setFailReason("None");
                }

                // Only the master door for this connected row evaluates redstone & power state
                if (master != this) {
                    return;
                }

                boolean hasGridPower = false;
                boolean hasRedstone = false;

                for (int i = 0, size = connected.size(); i < size; i++) {
                    GarageDoor gd = connected.get(i);
                    if (!hasGridPower && gd.getGrid() != null && gd.getGrid().hasPower(gd.getIdlePower())) {
                        hasGridPower = true;
                    }
                    if (!hasRedstone && gd.isStructurePowered()) {
                        hasRedstone = true;
                    }
                    if (hasGridPower && hasRedstone) {
                        break;
                    }
                }

                boolean shouldBeOpen = hasGridPower && hasRedstone;

                if (shouldBeOpen != wasPowered) {
                    for (int i = 0, size = connected.size(); i < size; i++) {
                        GarageDoor gd = connected.get(i);
                        gd.wasPowered = shouldBeOpen;
                        gd.queueAction(shouldBeOpen);
                    }
                }
            }, 2L, 2L);
        }
    }

    private boolean isStructurePowered() {
        Location loc = getLocationREADONLY();
        if (loc == null || !loc.isWorldLoaded() || !isChunkLoaded()) return false;

        World world = loc.getWorld();
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();

        Block coreBlock = world.getBlockAt(bx, by, bz);
        if (coreBlock.isBlockPowered() || coreBlock.isBlockIndirectlyPowered()) {
            return true;
        }

        int height = Math.max(calculatedHeight, 0);
        for (int i = 1; i <= height; i++) {
            Block targetBlock = world.getBlockAt(bx, by - i, bz);
            if (targetBlock.isBlockPowered() || targetBlock.isBlockIndirectlyPowered()) {
                return true;
            }
        }
        return false;
    }

    private GarageDoor getMasterDoor(List<GarageDoor> connected) {
        if (cachedMasterDoor != null && !cachedMasterDoor.isRemoved() && cachedMasterDoor.isChunkLoaded()) {
            return cachedMasterDoor;
        }
        GarageDoor master = this;
        int bestHash = this.getLocationREADONLY().hashCode();
        for (int i = 0, size = connected.size(); i < size; i++) {
            GarageDoor gd = connected.get(i);
            int h = gd.getLocationREADONLY().hashCode();
            if (h < bestHash) {
                bestHash = h;
                master = gd;
            }
        }
        this.cachedMasterDoor = master;
        return master;
    }

    public List<GarageDoor> getAllConnectedDoors() {
        long currentVersion = rowUUID != null ? rowTopologyVersions.getOrDefault(rowUUID, 0L) : 0L;
        if (cachedConnectedDoors != null && cachedTopologyVersion == currentVersion) {
            boolean valid = true;
            for (int i = 0, size = cachedConnectedDoors.size(); i < size; i++) {
                GarageDoor gd = cachedConnectedDoors.get(i);
                if (gd.isRemoved() || !gd.isChunkLoaded()) {
                    valid = false;
                    break;
                }
            }
            if (valid) {
                return cachedConnectedDoors;
            }
        }

        Location startLoc = getLocationREADONLY();
        if (startLoc == null || !startLoc.isWorldLoaded()) {
            return Collections.singletonList(this);
        }

        World world = startLoc.getWorld();
        List<GarageDoor> group = new ArrayList<>();
        Queue<GarageDoor> queue = new ArrayDeque<>();
        Set<GarageDoor> visited = new HashSet<>();

        queue.add(this);
        visited.add(this);

        GarageDoor master = this;
        int bestHash = startLoc.hashCode();

        while (!queue.isEmpty()) {
            GarageDoor curr = queue.poll();
            group.add(curr);

            Location currLoc = curr.getLocationREADONLY();
            int h = currLoc.hashCode();
            if (h < bestHash) {
                bestHash = h;
                master = curr;
            }

            int cx = currLoc.getBlockX();
            int cy = currLoc.getBlockY();
            int cz = currLoc.getBlockZ();

            for (int[] dir : HORIZONTAL_OFFSETS) {
                int nx = cx + dir[0];
                int nz = cz + dir[1];
                if (world.isChunkLoaded(nx >> 4, nz >> 4)) {
                    Device dev = Device.getDevice(new Location(world, nx, cy, nz));
                    if (dev instanceof GarageDoor gd && !gd.isRemoved() && !visited.contains(gd)) {
                        if (gd.rowUUID != null && gd.rowUUID.equals(this.rowUUID)) {
                            visited.add(gd);
                            queue.add(gd);
                        }
                    }
                }
            }
        }

        for (int i = 0, size = group.size(); i < size; i++) {
            GarageDoor gd = group.get(i);
            gd.cachedConnectedDoors = group;
            gd.cachedMasterDoor = master;
            gd.cachedTopologyVersion = currentVersion;
        }

        return group;
    }

    public int getRowMaxHeight() {
        return getRowMaxHeight(getAllConnectedDoors());
    }

    private int getRowMaxHeight(List<GarageDoor> connected) {
        int max = 0;
        for (int i = 0, size = connected.size(); i < size; i++) {
            GarageDoor gd = connected.get(i);
            int h = Math.max(gd.calculatedHeight, gd.storedBlockData.size());
            if (h > max) max = h;
        }
        return max;
    }

    public boolean getTargetState() {
        return targetState;
    }

    public void open() {
        queueAction(true);
    }

    public void close() {
        queueAction(false);
    }

    public List<Boolean> getQueuedActions() {
        List<Boolean> dummyList = new ArrayList<>(1);
        dummyList.add(targetState);
        return dummyList;
    }

    public void queueAction(boolean wantOpen) {
        if (this.targetState == wantOpen) return;
        this.targetState = wantOpen;

        if (!isAnimating) {
            startAnimation();
        }
    }

    private void rebuildParsedBlockDataCache() {
        int size = storedBlockData.size();
        parsedStoredBlockData = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String saved = storedBlockData.get(i);
            if (saved != null) {
                try {
                    parsedStoredBlockData.add(Bukkit.createBlockData(saved));
                } catch (Exception e) {
                    parsedStoredBlockData.add(Material.AIR.createBlockData());
                }
            } else {
                parsedStoredBlockData.add(null);
            }
        }
    }

    private BlockData getParsedBlockData(int index) {
        if (parsedStoredBlockData == null || parsedStoredBlockData.size() != storedBlockData.size()) {
            rebuildParsedBlockDataCache();
        }
        return index >= 0 && index < parsedStoredBlockData.size() ? parsedStoredBlockData.get(index) : null;
    }

    private void snapshotBlocks() {
        if (!storedBlockData.isEmpty()) return;

        int myH = Math.max(calculatedHeight, 0);
        Location loc = getLocationREADONLY();
        World world = loc.getWorld();
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        boolean chunkLoaded = loc.isWorldLoaded() && isChunkLoaded();

        if (parsedStoredBlockData == null) {
            parsedStoredBlockData = new ArrayList<>(myH);
        } else {
            parsedStoredBlockData.clear();
        }

        for (int i = 1; i <= myH; i++) {
            if (chunkLoaded) {
                Block b = world.getBlockAt(bx, by - i, bz);
                BlockData bd = b.getBlockData();
                storedBlockData.add(bd.getAsString());
                parsedStoredBlockData.add(bd);
            } else {
                storedBlockData.add(AIR_DATA_STRING);
                parsedStoredBlockData.add(Material.AIR.createBlockData());
            }
        }
    }

    private void startAnimation() {
        int rowMaxH = getRowMaxHeight();

        if (!stateInitialized) {
            stateInitialized = true;
            openProgress = storedBlockData.isEmpty() ? 0 : rowMaxH;
        }

        isAnimating = true;
        final boolean[] currentAnimDirection = { targetState };

        Sound initialSound = targetState ? Sound.BLOCK_PISTON_EXTEND : Sound.BLOCK_PISTON_CONTRACT;
        getLocationREADONLY().getWorld().playSound(getLocationREADONLY(), initialSound, 0.2f, 0.8f);

        animTaskID = Bukkit.getScheduler().scheduleSyncRepeatingTask(MineMain.getPlugin(), () -> {
            if (isRemoved()) {
                Bukkit.getScheduler().cancelTask(animTaskID);
                animTaskID = 0;
                return;
            }

            if (currentAnimDirection[0] != targetState) {
                currentAnimDirection[0] = targetState;
                Sound snd = targetState ? Sound.BLOCK_PISTON_EXTEND : Sound.BLOCK_PISTON_CONTRACT;
                getLocationREADONLY().getWorld().playSound(getLocationREADONLY(), snd, 0.2f, 0.8f);
            }

            int targetH = getRowMaxHeight();

            if (targetState) {
                if (openProgress >= targetH) {
                    stopAnimation();
                    return;
                }
                if (openProgress == 0) {
                    snapshotBlocks();
                }
                openProgress++;
                int indexToTarget = targetH - openProgress + 1;
                animateBlock(indexToTarget, true);
            } else {
                if (openProgress <= 0) {
                    stopAnimation();
                    return;
                }
                int indexToTarget = targetH - openProgress + 1;
                animateBlock(indexToTarget, false);
                openProgress--;
            }

        }, 0L, 4L);
    }

    private void stopAnimation() {
        isOpen = targetState;
        isAnimating = false;

        if (!isOpen) {
            storedBlockData.clear();
            if (parsedStoredBlockData != null) {
                parsedStoredBlockData.clear();
            }
            openProgress = 0;
        } else {
            openProgress = getRowMaxHeight();
        }

        if (animTaskID > 0) {
            Bukkit.getScheduler().cancelTask(animTaskID);
            animTaskID = 0;
        }
        updateOutlineState();
    }

    private void animateBlock(int index, boolean opening) {
        if (index > Math.max(calculatedHeight, storedBlockData.size())) return;

        Location coreLoc = getLocationREADONLY();
        if (coreLoc == null || !coreLoc.isWorldLoaded() || !isChunkLoaded()) return;

        Block block = coreLoc.getWorld().getBlockAt(coreLoc.getBlockX(), coreLoc.getBlockY() - index, coreLoc.getBlockZ());

        if (opening) {
            if (!block.getType().isAir()) {
                block.setType(Material.AIR, false);
            }
        } else {
            if (index - 1 < storedBlockData.size()) {
                BlockData bd = getParsedBlockData(index - 1);
                if (bd != null) {
                    if (!bd.getMaterial().isAir()) {
                        if (!block.getType().isAir() && !block.isReplaceable()) {
                            if (block.getType() != bd.getMaterial()) {
                                block.breakNaturally();
                            }
                        }
                        block.setBlockData(bd, false);
                    } else {
                        if (!block.getType().isAir()) {
                            block.breakNaturally();
                            block.setType(Material.AIR, false);
                        }
                    }
                }
            }
        }
    }

    private void dropStoredBlocks() {
        if (calculatedHeight <= 0) return;
        Location coreLoc = getLocationREADONLY();
        if (coreLoc == null || !coreLoc.isWorldLoaded()) {
            storedBlockData.clear();
            if (parsedStoredBlockData != null) parsedStoredBlockData.clear();
            return;
        }
        World world = coreLoc.getWorld();
        int bx = coreLoc.getBlockX();
        int by = coreLoc.getBlockY();
        int bz = coreLoc.getBlockZ();

        for (int i = 1; i <= calculatedHeight; i++) {
            if (i - 1 < storedBlockData.size()) {
                BlockData bd = getParsedBlockData(i - 1);
                if (bd != null && !bd.getMaterial().isAir()) {
                    try {
                        Location target = new Location(world, bx, by - i, bz);
                        world.dropItemNaturally(target, new ItemStack(bd.getMaterial()));
                    } catch (Exception ignored) {}
                }
            }
        }
        storedBlockData.clear();
        if (parsedStoredBlockData != null) {
            parsedStoredBlockData.clear();
        }
    }

    @Override
    public void cleanup() {
        if (isAnimating) {
            int targetH = getRowMaxHeight();
            while (true) {
                if (targetState && openProgress < targetH) {
                    if (openProgress == 0) snapshotBlocks();
                    openProgress++;
                    int indexToTarget = targetH - openProgress + 1;
                    animateBlock(indexToTarget, true);
                } else if (!targetState && openProgress > 0) {
                    int indexToTarget = targetH - openProgress + 1;
                    animateBlock(indexToTarget, false);
                    openProgress--;
                } else {
                    break;
                }
            }
            stopAnimation();
        }

        super.cleanup();

        if (monitorStarted) {
            Bukkit.getScheduler().cancelTask(monitorTaskID);
            monitorStarted = false;
        }
        if (animTaskID > 0) {
            Bukkit.getScheduler().cancelTask(animTaskID);
            animTaskID = 0;
        }
        clearRowHighlights();

        bumpRowVersion(this.rowUUID);

        if (isRemoved()) {
            dropStoredBlocks();
        }
    }

    @Override
    public void updateUI() {
        Inventory inv = this.getInventory();
        if (inv == null) return;

        ItemStack toggleItem = new ItemStack(showPreview ? Material.LIME_CONCRETE : Material.RED_CONCRETE);
        ItemMeta meta = toggleItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.BOLD + "Toggle Preview");
            List<String> lore = new ArrayList<>(1);
            lore.add(ChatColor.GRAY + "Currently: " + (showPreview ? "ON" : "OFF"));
            meta.setLore(lore);
            toggleItem.setItemMeta(meta);
        }

        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        if (paneMeta != null) {
            paneMeta.setDisplayName(" ");
            pane.setItemMeta(paneMeta);
        }

        for (int i = 0, size = inv.getSize(); i < size; i++) {
            if (i == 13) {
                inv.setItem(i, toggleItem);
            } else {
                inv.setItem(i, pane);
            }
        }
    }

    @Override
    public void onUIClick(InventoryClickEvent event) {
        if (event.getSlot() == 13) {
            event.setCancelled(true);
            boolean newState = !showPreview;
            if (event.getWhoClicked() instanceof Player player) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
            List<GarageDoor> connected = getAllConnectedDoors();
            for (int i = 0, size = connected.size(); i < size; i++) {
                GarageDoor gd = connected.get(i);
                gd.showPreview = newState;
                gd.updateUI();
                gd.updateOutlineState(connected);
            }
        }
    }

    @Override
    public void action() {}
}