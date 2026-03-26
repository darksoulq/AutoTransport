package com.github.darksoulq.auto_transport.device;

import com.MT.triggersUtility.TUMaths;
import com.MT.xxxtrigger50xxx.Devices.Device;
import com.MT.xxxtrigger50xxx.Guide.ItemMenu;
import com.MT.xxxtrigger50xxx.MineItems;
import com.MT.xxxtrigger50xxx.MineMain;
import com.MT.xxxtrigger50xxx.MineUtil;
import com.MT.xxxtrigger50xxx.Recipes.MTRecipe.RecipeType;
import com.github.darksoulq.auto_transport.device.traits.InteractiveDevice;
import com.github.darksoulq.auto_transport.multiblock.CustomDeviceRegistry;
import com.github.darksoulq.auto_transport.multiblock.MultiblockDevice;
import com.github.darksoulq.auto_transport.multiblock.PlacementContext;
import com.github.darksoulq.auto_transport.multiblock.RotationUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GarageDoor extends MultiblockDevice implements InteractiveDevice {

    private static final long serialVersionUID = 1L;
    public static final String CLASS_NAME = "Garage Door";
    public static final int MAX_EXTENSION = 10;

    private int width = 0;
    private String disguiseMaterial = Material.IRON_BLOCK.name();
    private boolean isOpen = false;
    protected transient boolean wasPowered = false;
    private transient int redstoneTaskId = -1;

    public GarageDoor(Location loc) {
        super(loc);
        this.deviceName = CLASS_NAME;
        this.setMaterial("SMOOTH_STONE");

        DeviceSettings.configure(this)
            .power(0, 15, 0)
            .processing(0, 0, 0)
            .useUi(false)
            .apply();
    }

    @Override
    public Map<Vector, BlockData> getStructureMap(PlacementContext context) {
        Map<Vector, BlockData> map = new HashMap<>();

        if (context != null) {
            this.width = calculateWidth(context);
        }

        Material mat = Material.getMaterial(disguiseMaterial);
        if (mat == null || !mat.isBlock()) {
            mat = Material.IRON_BLOCK;
        }

        BlockData disguiseData = mat.createBlockData();
        BlockData airData = Material.AIR.createBlockData();

        for (int i = 1; i <= width; i++) {
            map.put(new Vector(i, 0, 0), isOpen ? airData : disguiseData);
        }

        return map;
    }

    private int calculateWidth(PlacementContext ctx) {
        Location core = ctx.coreLocation;
        BlockFace facing = ctx.facing;
        int calculatedWidth = 0;

        for (int i = 1; i <= MAX_EXTENSION; i++) {
            Vector rotated = RotationUtil.rotateVector(new Vector(i, 0, 0), facing);
            Location check = core.clone().add(rotated);
            if (!check.getBlock().getType().isAir() && !check.getBlock().isReplaceable()) {
                break;
            }
            calculatedWidth++;
        }
        return calculatedWidth;
    }

    @Override
    public void postCreate(boolean isPlaced) {
        super.postCreate(isPlaced);
        if (redstoneTaskId == -1) {
            redstoneTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(MineMain.getPlugin(), () -> {
                if (isRemoved()) {
                    Bukkit.getScheduler().cancelTask(redstoneTaskId);
                    redstoneTaskId = -1;
                    return;
                }

                if (isTopInChain()) {
                    boolean anyPowered = false;
                    for (GarageDoor gd : getChainedDoors()) {
                        if (gd.isStructurePowered()) {
                            anyPowered = true;
                            break;
                        }
                    }

                    if (anyPowered != wasPowered) {
                        wasPowered = anyPowered;
                        toggleChain(anyPowered);
                        for (GarageDoor gd : getChainedDoors()) {
                            gd.wasPowered = anyPowered;
                        }
                    }
                }
            }, 2L, 2L);
        }
    }

    private boolean isStructurePowered() {
        if (!getLocationREADONLY().isWorldLoaded()) return false;

        if (getLocationREADONLY().getBlock().isBlockPowered() || getLocationREADONLY().getBlock().isBlockIndirectlyPowered()) {
            return true;
        }

        BlockFace facing = getFacing();
        for (int i = 1; i <= width; i++) {
            Vector rotated = RotationUtil.rotateVector(new Vector(i, 0, 0), facing);
            Location target = getLocationREADONLY().clone().add(rotated);
            if (target.getBlock().isBlockPowered() || target.getBlock().isBlockIndirectlyPowered()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (player.isSneaking() && isAuthorized(player)) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            Material type = hand.getType();

            if (type.isBlock() && type != Material.AIR) {
                if (!type.isSolid() || type.hasGravity()) {
                    MineUtil.sendMessage(player, ChatColor.RED + "The garage door can only be disguised as a solid, non-falling block!");
                    return true;
                }

                disguiseMaterial = type.name();
                MineUtil.sendMessage(player, ChatColor.GREEN + "Garage door disguised as " + type.name());
                updatePhysicalState();
                player.playSound(player.getLocation(), Sound.BLOCK_STONE_PLACE, 1.0f, 1.0f);
                return true;
            }
        }
        return false;
    }

    public void setOpen(boolean open) {
        if (this.isOpen == open) return;

        int powerNeeded = getActionPower();
        if (powerNeeded > 0) {
            if (getGrid() == null || !getGrid().hasPower(powerNeeded)) {
                setFailReason("Not Enough Power");
                return;
            }
            getGrid().consumePower(this, powerNeeded);
            setConsumingPower(true);
        }

        this.isOpen = open;
        setFailReason("None");
        updatePhysicalState();
        Sound sound = open ? Sound.BLOCK_PISTON_EXTEND : Sound.BLOCK_PISTON_CONTRACT;
        getLocationREADONLY().getWorld().playSound(getLocationREADONLY(), sound, 1.0f, 0.8f);
    }

    public boolean isOpen() {
        return isOpen;
    }

    public void updatePhysicalState() {
        Material mat = Material.getMaterial(disguiseMaterial);
        if (mat == null) mat = Material.IRON_BLOCK;

        BlockFace facing = getFacing();
        Location core = getLocationREADONLY();

        BlockData disguiseData = mat.createBlockData();
        BlockData airData = Material.AIR.createBlockData();

        for (int i = 1; i <= width; i++) {
            Vector rotated = RotationUtil.rotateVector(new Vector(i, 0, 0), facing);
            Location target = core.clone().add(rotated);
            target.getBlock().setBlockData(isOpen ? airData : disguiseData, false);
        }
    }

    public void toggleChain(boolean openState) {
        List<GarageDoor> chain = getChainedDoors();

        if (openState) {
            chain.sort((a, b) -> Integer.compare(a.getLocationREADONLY().getBlockY(), b.getLocationREADONLY().getBlockY()));
        } else {
            chain.sort((a, b) -> Integer.compare(b.getLocationREADONLY().getBlockY(), a.getLocationREADONLY().getBlockY()));
        }

        new BukkitRunnable() {
            int index = 0;
            @Override
            public void run() {
                if (index >= chain.size()) {
                    this.cancel();
                    return;
                }
                chain.get(index).setOpen(openState);
                index++;
            }
        }.runTaskTimer(MineMain.getPlugin(), 0L, 4L);
    }

    private boolean isTopInChain() {
        Location above = getLocationREADONLY().clone().add(0, 1, 0);
        Device dev = Device.getDevice(above);
        if (dev instanceof GarageDoor gd) {
            return !gd.getFacing().equals(this.getFacing());
        }
        return true;
    }

    public List<GarageDoor> getChainedDoors() {
        List<GarageDoor> chain = new ArrayList<>();
        Location current = getLocationREADONLY().clone();
        BlockFace myFace = getFacing();

        while (true) {
            current.add(0, 1, 0);
            Device dev = Device.getDevice(current);
            if (dev instanceof GarageDoor gd && gd.getFacing().equals(myFace)) {
                chain.add(gd);
            } else {
                break;
            }
        }

        current = getLocationREADONLY().clone();
        chain.add(this);

        while (true) {
            current.add(0, -1, 0);
            Device dev = Device.getDevice(current);
            if (dev instanceof GarageDoor gd && gd.getFacing().equals(myFace)) {
                chain.add(gd);
            } else {
                break;
            }
        }

        return chain;
    }

    @Override
    public void cleanup() {
        if (redstoneTaskId != -1) {
            Bukkit.getScheduler().cancelTask(redstoneTaskId);
            redstoneTaskId = -1;
        }
        super.cleanup();
    }

    @Override
    public ArrayList<String> stackDescription() {
        ArrayList<String> desc = new ArrayList<>();
        desc.add("- Auto-extends until blocked.");
        desc.add("- Shift-Right-Click with a block to disguise.");
        desc.add("- Responds to Redstone.");
        desc.add("- Chains vertically.");
        return desc;
    }

    @Override
    public void updateUI() {
    }

    @Override
    public void action() {
    }

    public static void addDevice() {
        List<ItemStack> recipe = new ArrayList<>();
        recipe.add(MineItems.getIndustrialItem("Iron Plate", 15));
        recipe.add(MineItems.getIndustrialItem("Electronic Circuit", 2));

        CustomDeviceRegistry.builder(CLASS_NAME, GarageDoor.class, Material.SMOOTH_STONE, () -> new GarageDoor(null))
            .withDescription("A sequential garage door that blends into walls.")
            .withItemMenu(ItemMenu.Category.LOGISTICS)
            .withRecipe(recipe, RecipeType.ADVANCED)
            .register();
    }
}