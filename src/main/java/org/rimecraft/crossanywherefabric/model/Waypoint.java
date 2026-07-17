package org.rimecraft.crossanywherefabric.model;

import java.util.UUID;

public final class Waypoint {
    private String name;
    private String world;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private String description;
    private UUID owner;
    private long createdAt;
    private long updatedAt;

    public Waypoint() {
    }

    public Waypoint(String name, String world, double x, double y, double z, float yaw, float pitch,
                    String description, UUID owner, long createdAt, long updatedAt) {
        this.name = name;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.description = description;
        this.owner = owner;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getName() { return name; }
    public String getWorld() { return world; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public String getDescription() { return description; }
    public UUID getOwner() { return owner; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setDescription(String description) { this.description = description; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public TeleportPosition position() {
        return new TeleportPosition(world, x, y, z, yaw, pitch);
    }
}
