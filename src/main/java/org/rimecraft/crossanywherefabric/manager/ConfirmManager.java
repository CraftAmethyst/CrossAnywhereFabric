package org.rimecraft.crossanywherefabric.manager;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.rimecraft.crossanywherefabric.i18n.MessageService;
import org.rimecraft.crossanywherefabric.model.TeleportPosition;
import org.rimecraft.crossanywherefabric.teleport.TeleportType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ConfirmManager {
    private final Map<UUID, PendingTeleport> pending = new ConcurrentHashMap<>();

    public void register(PendingTeleport teleport) {
        pending.put(teleport.playerId(), teleport);
    }

    public PendingTeleport get(UUID uuid) {
        PendingTeleport value = pending.get(uuid);
        if (value != null && value.expiresAt() <= Instant.now().getEpochSecond()) {
            pending.remove(uuid);
            return null;
        }
        return value;
    }

    public void clear(UUID uuid) {
        pending.remove(uuid);
    }

    public void tick(MinecraftServer server, MessageService messages) {
        long now = Instant.now().getEpochSecond();
        pending.entrySet().removeIf(entry -> {
            if (entry.getValue().expiresAt() > now) return false;
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) messages.send(player, "confirm.timeout");
            return true;
        });
    }

    public record PendingTeleport(UUID playerId, TeleportPosition destination, TeleportType type, long expiresAt) { }
}
