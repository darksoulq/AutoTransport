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
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class GarageDoor extends MultiblockDevice {

    @Serial
    private static final long serialVersionUID = 1L;
    public static final String CLASS_NAME = "Garage Door";
    public static final int MAX_EXTENSION = 10;
    public static final int MAX_WIDTH = 10;
    private static boolean placeListenerRegistered = false;

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

    public GarageDoor(Location loc) {
        super(loc);
        this.deviceName = CLASS_NAME;
        this.setMaterial("SMOOTH_STONE");
        this.setIdlePower(15);
        this.setActionPower(0);
        this.setUseUI(true);
        this.setOpenable(false);
    }

    @Override
    public MultiblockBlueprint getBlueprint(BlueprintContext context) {
        MultiblockBlueprint blueprint = new MultiblockBlueprint();

        if (context != null) {
            if (this.calculatedHeight <= 0 && context.getCoreLocation() != null) {
                int h = calculateHeight(context.getCoreLocation());
                if (context.getDevice() == this) {
                    this.calculatedHeight = h;
                }
            }
        }

        int height = Math.max(this.calculatedHeight, 0);

        for (int i = 1; i <= height; i++) {
            blueprint.addComponentObj(new Vector(0, -i, 0), new OptionalComponent(new AnySolidChoice(), new BasicStructuralPort()));
        }

        return blueprint;
    }

    private int calculateHeight(Location core) {
        int height = 0;
        for (int i = 1; i <= MAX_EXTENSION; i++) {
            Location check = core.clone().add(0, -i, 0);

            if (!check.getWorld().isChunkLoaded(check.getBlockX() >> 4, check.getBlockZ() >> 4)) {
                break;
            }

            Device dev = Device.getDevice(check);
            if (dev instanceof PortProxyDevice proxy && proxy.getCoreDevice() != null && proxy.getCoreDevice().getLocationREADONLY().equals(this.getLocationREADONLY())) {
                height++;
                continue;
            }

            boolean isStored = (i - 1 < storedBlockData.size());
            if (!isStored && !check.getBlock().getType().isAir() && !check.getBlock().isReplaceable()) {
                break;
            }
            height++;
        }
        return Math.max(height, storedBlockData.size());
    }

    public void updateOutlineState() {
        if (isRemoved()) {
            clearRowHighlights();
            return;
        }

        List<GarageDoor> connected = getAllConnectedDoors();
        GarageDoor master = this;
        double minX = getLocationREADONLY().getX();
        double maxX = minX;
        double minY = getLocationREADONLY().getY();
        double maxY = minY;
        double minZ = getLocationREADONLY().getZ();
        double maxZ = minZ;

        int rowHeight = getRowMaxHeight();

        for (GarageDoor gd : connected) {
            if (gd.getLocationREADONLY().hashCode() < master.getLocationREADONLY().hashCode()) {
                master = gd;
            }
            Location loc = gd.getLocationREADONLY();
            minX = Math.min(minX, loc.getX());
            maxX = Math.max(maxX, loc.getX());
            minY = Math.min(minY, loc.getY());
            maxY = Math.max(maxY, loc.getY());
            minZ = Math.min(minZ, loc.getZ());
            maxZ = Math.max(maxZ, loc.getZ());
        }

        if (master != this) {
            clearRowHighlights();
            return;
        }

        if (!showPreview || rowHeight <= 0) {
            clearRowHighlights();
            return;
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
            lastMinX != x1 || lastMaxX != x2 ||
            lastMinY != y1 || lastMaxY != y2 ||
            lastMinZ != z1 || lastMaxZ != z2;

        if (needsUpdate) {
            clearRowHighlights();
            rowHighlightDisplays = new ArrayList<>();
            org.bukkit.World w = getLocationREADONLY().getWorld();

            drawBoundingBox(w, x1, y1, z1, x2, y2, z2, Material.YELLOW_CONCRETE, rowHighlightDisplays, t);
            drawBoundingBox(w, x1, ey1, z1, x2, ey2, z2, Material.LIGHT_BLUE_CONCRETE, rowHighlightDisplays, t);

            lastMinX = x1; lastMaxX = x2;
            lastMinY = y1; lastMaxY = y2;
            lastMinZ = z1; lastMaxZ = z2;
        }
    }

    private void drawBoundingBox(org.bukkit.World w, double x1, double y1, double z1, double x2, double y2, double z2, Material mat, List<BlockDisplay> list, double t) {
        list.add(spawnLine(w, x1, y1, z1, (x2 - x1), t, t, mat));
        list.add(spawnLine(w, x1, y1, z2 - t, (x2 - x1), t, t, mat));
        list.add(spawnLine(w, x1, y1, z1, t, t, (z2 - z1), mat));
        list.add(spawnLine(w, x2 - t, y1, z1, t, t, (z2 - z1), mat));

        list.add(spawnLine(w, x1, y2 - t, z1, (x2 - x1), t, t, mat));
        list.add(spawnLine(w, x1, y2 - t, z2 - t, (x2 - x1), t, t, mat));
        list.add(spawnLine(w, x1, y2 - t, z1, t, t, (z2 - z1), mat));
        list.add(spawnLine(w, x2 - t, y2 - t, z1, t, t, (z2 - z1), mat));

        list.add(spawnLine(w, x1, y1, z1, t, (y2 - y1), t, mat));
        list.add(spawnLine(w, x2 - t, y1, z1, t, (y2 - y1), t, mat));
        list.add(spawnLine(w, x1, y1, z2 - t, t, (y2 - y1), t, mat));
        list.add(spawnLine(w, x2 - t, y1, z2 - t, t, (y2 - y1), t, mat));
    }

    private BlockDisplay spawnLine(org.bukkit.World world, double x, double y, double z, double sx, double sy, double sz, Material mat) {
        Location loc = new Location(world, x, y, z);
        return world.spawn(loc, BlockDisplay.class, ent -> {
            ent.setBlock(Bukkit.createBlockData(mat));
            ent.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            Transformation tr = ent.getTransformation();
            tr.getScale().set((float) sx, (float) sy, (float) sz);
            ent.setTransformation(tr);
        });
    }

    private void clearRowHighlights() {
        if (rowHighlightDisplays != null) {
            for (BlockDisplay bd : rowHighlightDisplays) {
                if (bd != null && bd.isValid()) bd.remove();
            }
            rowHighlightDisplays.clear();
        }
    }

    @Override
    public void postCreate(boolean isPlaced) {
        super.postCreate(isPlaced);

        if (getLocationREADONLY() == null) return;

        if (this.rowUUID == null) {
            String foundUUID = null;
            Vector[] dirs = {new Vector(1,0,0), new Vector(-1,0,0), new Vector(0,0,1), new Vector(0,0,-1)};

            for (Vector dir : dirs) {
                Location adj = getLocationREADONLY().clone().add(dir);
                if (adj.isWorldLoaded() && adj.getWorld().isChunkLoaded(adj.getBlockX() >> 4, adj.getBlockZ() >> 4)) {
                    Device dev = Device.getDevice(adj);
                    if (dev instanceof GarageDoor gd) {
                        if (gd.rowUUID != null) {
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
                this.rowUUID = java.util.UUID.randomUUID().toString();
            }
        }

        if (!placeListenerRegistered) {
            placeListenerRegistered = true;
            Bukkit.getPluginManager().registerEvents(new Listener() {
                @EventHandler(ignoreCancelled = true)
                public void onBlockPlace(BlockPlaceEvent e) {
                    Location loc = e.getBlock().getLocation();
                    for (int i = 1; i <= MAX_EXTENSION; i++) {
                        Location check = loc.clone().add(0, i, 0);
                        Device dev = Device.getDevice(check);
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

        if (calculatedHeight <= 0) {
            calculatedHeight = calculateHeight(getLocationREADONLY());
        }

        if (!stateInitialized) {
            stateInitialized = true;
            if (!storedBlockData.isEmpty()) {
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

        boolean foundSync = false;
        for (GarageDoor gd : getAllConnectedDoors()) {
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
            if (isRemoved()) return;

            if (calculatedHeight <= 0) {
                calculatedHeight = calculateHeight(getLocationREADONLY());
            }

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

                if (calculatedHeight <= 0) {
                    calculatedHeight = calculateHeight(getLocationREADONLY());
                    if (calculatedHeight <= 0) return;
                }

                if (++tickCounter % 10 == 0) {
                    updateOutlineState();
                }

                List<GarageDoor> connected = getAllConnectedDoors();

                if (connected.size() > MAX_WIDTH) {
                    this.setFailReason("Row limit exceeded (" + MAX_WIDTH + ")");
                    return;
                } else if (this.getFailReason() != null && this.getFailReason().startsWith("Row limit exceeded")) {
                    this.setFailReason("None");
                }

                boolean hasGridPower = getGrid() != null && getGrid().hasPower(getIdlePower());
                boolean hasRedstone = false;

                for (GarageDoor gd : connected) {
                    if (gd.isStructurePowered()) {
                        hasRedstone = true;
                        break;
                    }
                }

                boolean shouldBeOpen = hasGridPower && hasRedstone;

                if (shouldBeOpen != wasPowered) {
                    for (GarageDoor gd : connected) {
                        gd.wasPowered = shouldBeOpen;
                        gd.queueAction(shouldBeOpen);
                    }
                }
            }, 2L, 2L);
        }
    }

    private boolean isStructurePowered() {
        if (getLocationREADONLY() == null || !getLocationREADONLY().isWorldLoaded()) return false;

        Block coreBlock = getLocationREADONLY().getBlock();
        if (coreBlock.isBlockPowered() || coreBlock.isBlockIndirectlyPowered()) {
            return true;
        }

        int height = Math.max(calculatedHeight, 0);

        for (int i = 1; i <= height; i++) {
            Location target = getLocationREADONLY().clone().add(0, -i, 0);

            if (!target.isWorldLoaded() || !target.getWorld().isChunkLoaded(target.getBlockX() >> 4, target.getBlockZ() >> 4)) {
                continue;
            }
            if (target.getBlock().isBlockPowered() || target.getBlock().isBlockIndirectlyPowered()) {
                return true;
            }
        }
        return false;
    }

    public List<GarageDoor> getAllConnectedDoors() {
        List<GarageDoor> group = new ArrayList<>();
        Queue<GarageDoor> queue = new LinkedList<>();
        Set<GarageDoor> visited = new HashSet<>();

        queue.add(this);
        visited.add(this);

        Vector[] dirs = {new Vector(1,0,0), new Vector(-1,0,0), new Vector(0,0,1), new Vector(0,0,-1)};

        while (!queue.isEmpty()) {
            GarageDoor curr = queue.poll();
            group.add(curr);

            for (Vector dir : dirs) {
                Location adj = curr.getLocationREADONLY().clone().add(dir);
                if (adj.isWorldLoaded() && adj.getWorld().isChunkLoaded(adj.getBlockX() >> 4, adj.getBlockZ() >> 4)) {
                    Device dev = Device.getDevice(adj);
                    if (dev instanceof GarageDoor gd && !visited.contains(gd)) {
                        if (gd.rowUUID != null && gd.rowUUID.equals(this.rowUUID)) {
                            visited.add(gd);
                            queue.add(gd);
                        }
                    }
                }
            }
        }
        return group;
    }

    public int getRowMaxHeight() {
        int max = 0;
        for (GarageDoor gd : getAllConnectedDoors()) {
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
        List<Boolean> dummyList = new ArrayList<>();
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

    private void snapshotBlocks() {
        if (!storedBlockData.isEmpty()) return;

        int myH = Math.max(calculatedHeight, 0);

        for (int i = 1; i <= myH; i++) {
            Location target = getLocationREADONLY().clone().add(0, -i, 0);
            if (target.isWorldLoaded() && target.getWorld().isChunkLoaded(target.getBlockX() >> 4, target.getBlockZ() >> 4)) {
                Block b = target.getBlock();
                storedBlockData.add(b.getBlockData().getAsString());
            } else {
                storedBlockData.add(Material.AIR.createBlockData().getAsString());
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

        Location target = getLocationREADONLY().clone().add(0, -index, 0);
        if (!target.getWorld().isChunkLoaded(target.getBlockX() >> 4, target.getBlockZ() >> 4)) return;
        Block block = target.getBlock();

        if (opening) {
            if (!block.getType().isAir()) {
                block.setType(Material.AIR, false);
            }
        } else {
            if (index - 1 < storedBlockData.size()) {
                String saved = storedBlockData.get(index - 1);
                if (saved != null) {
                    BlockData bd = Bukkit.createBlockData(saved);
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
        for (int i = 1; i <= calculatedHeight; i++) {
            if (i - 1 < storedBlockData.size()) {
                String saved = storedBlockData.get(i - 1);
                if (saved != null) {
                    try {
                        BlockData bd = Bukkit.createBlockData(saved);
                        if (!bd.getMaterial().isAir()) {
                            Location target = getLocationREADONLY().clone().add(0, -i, 0);
                            if (target.isWorldLoaded()) {
                                target.getWorld().dropItemNaturally(target, new ItemStack(bd.getMaterial()));
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        storedBlockData.clear();
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
            List<String> lore = new ArrayList<>();
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

        for (int i = 0; i < inv.getSize(); i++) {
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
            for (GarageDoor gd : getAllConnectedDoors()) {
                gd.showPreview = newState;
                gd.updateUI();
                gd.updateOutlineState();
            }
        }
    }

    @Override
    public void action() {}
}