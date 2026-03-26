package com.github.darksoulq.auto_transport.device.display;

import com.github.darksoulq.auto_transport.multiblock.RotationUtil;
import com.MT.xxxtrigger50xxx.Devices.Device;
import com.MT.xxxtrigger50xxx.MineMain;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ComplexDisplayManager {

    private static final Map<ComplexDisplay, DisplayInstance> instances = new ConcurrentHashMap<>();
    private static int taskId = -1;

    public static void start() {
        if (taskId != -1) return;
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(MineMain.getPlugin(), ComplexDisplayManager::tick, 0L, 1L);
    }

    public static void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        for (DisplayInstance instance : instances.values()) {
            for (Display d : instance.displays.values()) {
                DisplayCullingManager.untrack(d);
                if (d.isValid()) d.remove();
            }
        }
        instances.clear();
    }

    public static void sync(ComplexDisplay cd, Device device, boolean isNearby) {
        if (!isNearby) {
            clear(cd);
            return;
        }

        DisplayModel model = cd.getDisplayModel(null);
        if (model == null) return;

        ModelState targetState = model.getState(cd.getActiveState());
        if (targetState == null) {
            clear(cd);
            return;
        }

        DisplayInstance instance = instances.computeIfAbsent(cd, k -> new DisplayInstance());

        if (instance.currentState != null && instance.currentState.getName().equals(targetState.getName())) {
            return;
        }

        instance.currentState = targetState;
        BlockFace buildFacing = device.getFacing();
        Location core = device.getLocationREADONLY();

        List<String> currentIds = new ArrayList<>(instance.displays.keySet());

        for (ModelPart part : targetState.getParts()) {
            currentIds.remove(part.getId());

            Display display = instance.displays.get(part.getId());
            if (display == null || !display.isValid()) {
                display = spawnDisplay(part.getVisual(), core, buildFacing);
                instance.displays.put(part.getId(), display);
            }

            if (part.getAnimation() != null && !part.getAnimation().getFrames().isEmpty()) {
                AnimationState animState = new AnimationState(part.getAnimation());
                instance.animations.put(part.getId(), animState);
                applyTransform(display, part.getVisual(), animState.getCurrentFrame(), buildFacing, animState.getCurrentFrame().getDurationTicks(), core);
            } else {
                instance.animations.remove(part.getId());
                applyTransform(display, part.getVisual(), null, buildFacing, 0, core);
            }
        }

        for (String oldId : currentIds) {
            Display oldDisplay = instance.displays.remove(oldId);
            instance.animations.remove(oldId);
            if (oldDisplay != null && oldDisplay.isValid()) {
                DisplayCullingManager.untrack(oldDisplay);
                oldDisplay.remove();
            }
        }
        
        cd.setSpawnedDisplays(new ArrayList<>(instance.displays.values()));
    }

    public static void clear(ComplexDisplay cd) {
        DisplayInstance instance = instances.remove(cd);
        if (instance != null) {
            for (Display display : instance.displays.values()) {
                DisplayCullingManager.untrack(display);
                if (display.isValid()) display.remove();
            }
        }
    }

    private static void tick() {
        for (Map.Entry<ComplexDisplay, DisplayInstance> entry : instances.entrySet()) {
            ComplexDisplay cd = entry.getKey();
            Device device = (Device) cd;
            DisplayInstance instance = entry.getValue();

            if (device.isRemoved()) {
                clear(cd);
                continue;
            }

            cd.tickAnimations();

            BlockFace buildFacing = device.getFacing();
            Location core = device.getLocationREADONLY();

            for (Map.Entry<String, AnimationState> animEntry : instance.animations.entrySet()) {
                String partId = animEntry.getKey();
                AnimationState state = animEntry.getValue();

                if (!state.animation.isPlaying()) continue;

                state.ticksInFrame++;
                Keyframe currentFrame = state.animation.getFrames().get(state.currentFrameIndex);

                if (state.ticksInFrame >= currentFrame.getDurationTicks()) {
                    state.currentFrameIndex++;

                    if (state.currentFrameIndex >= state.animation.getFrames().size()) {
                        if (state.animation.isLoop()) {
                            state.currentFrameIndex = 0;

                            Display display = instance.displays.get(partId);
                            if (display != null) {
                                Keyframe loopFrame = state.animation.getFrames().get(0);
                                applyTransform(display, instance.currentState.getPart(partId).getVisual(), loopFrame, buildFacing, 0, core);
                            }
                        } else {
                            continue;
                        }
                    }

                    state.ticksInFrame = 0;
                    Keyframe nextFrame = state.animation.getFrames().get(state.currentFrameIndex);
                    Display display = instance.displays.get(partId);

                    if (display != null) {
                        applyTransform(display, instance.currentState.getPart(partId).getVisual(), nextFrame, buildFacing, nextFrame.getDurationTicks(), core);
                    }
                }
            }
        }
    }

    private static Display spawnDisplay(VisualPart visual, Location core, BlockFace buildFacing) {
        Vector rotatedOffset = RotationUtil.rotateVector(visual.getOffset(), buildFacing);
        Location target = core.clone().add(0.5, 0.0, 0.5).add(rotatedOffset);
        target.setYaw(RotationUtil.getYawFromFace(buildFacing));

        Display display = null;
        if (visual.getItem() != null) {
            ItemDisplay itemDisplay = (ItemDisplay) target.getWorld().spawnEntity(target, EntityType.ITEM_DISPLAY);
            itemDisplay.setItemStack(visual.getItem());
            display = itemDisplay;
        } else if (visual.getBlock() != null) {
            BlockDisplay blockDisplay = (BlockDisplay) target.getWorld().spawnEntity(target, EntityType.BLOCK_DISPLAY);
            blockDisplay.setBlock(RotationUtil.rotateBlockData(visual.getBlock(), buildFacing));
            display = blockDisplay;
        }

        if (display != null) {
            display.setBrightness(new Display.Brightness(15, 15));
            DisplayCullingManager.track(display);
        }
        return display;
    }

    public static void applyTransform(Display display, VisualPart part, Keyframe frame, BlockFace buildFacing, int interpolationDuration, Location core) {
        Vector tOffset = part.getOffset().clone();
        Vector tRot = part.getRotationDegrees().clone();
        Vector tScale = part.getScale().clone();

        if (frame != null) {
            tOffset.add(frame.getTranslation());
            tRot.add(frame.getRotation());
            tScale.multiply(frame.getScale());
        }

        Vector rotatedOffset = RotationUtil.rotateVector(tOffset, buildFacing);
        Location target = core.clone().add(0.5, 0.0, 0.5).add(rotatedOffset);
        target.setYaw(RotationUtil.getYawFromFace(buildFacing));
        display.teleport(target);

        Transformation transform = display.getTransformation();
        
        if (display instanceof BlockDisplay) {
            transform.getTranslation().set(-0.5f * (float)tScale.getX(), 0f, -0.5f * (float)tScale.getZ());
        } else {
            transform.getTranslation().set(0f, 0f, 0f);
        }

        transform.getScale().set(tScale.getX(), tScale.getY(), tScale.getZ());

        float rx = (float) Math.toRadians(tRot.getX());
        float ry = (float) Math.toRadians(tRot.getY());
        float rz = (float) Math.toRadians(tRot.getZ());
        transform.getLeftRotation().identity().mul(new Quaternionf().rotationXYZ(rx, ry, rz));

        if (interpolationDuration > 0) {
            display.setInterpolationDuration(interpolationDuration);
            display.setInterpolationDelay(0);
        }

        display.setTransformation(transform);
    }

    private static class DisplayInstance {
        ModelState currentState;
        final Map<String, Display> displays = new HashMap<>();
        final Map<String, AnimationState> animations = new HashMap<>();
    }

    private static class AnimationState {
        final Animation animation;
        int currentFrameIndex = 0;
        int ticksInFrame = 0;

        AnimationState(Animation animation) {
            this.animation = animation;
        }

        Keyframe getCurrentFrame() {
            return animation.getFrames().get(currentFrameIndex);
        }
    }
}