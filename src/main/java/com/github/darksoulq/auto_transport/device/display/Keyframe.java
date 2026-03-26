package com.github.darksoulq.auto_transport.device.display;

import org.bukkit.util.Vector;

public record Keyframe(Vector translation, Vector rotation, Vector scale, int durationTicks) {}