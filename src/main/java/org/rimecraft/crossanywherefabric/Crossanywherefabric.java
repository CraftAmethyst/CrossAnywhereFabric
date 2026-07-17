package org.rimecraft.crossanywherefabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.rimecraft.crossanywherefabric.command.CrossAnywhereCommands;
import org.rimecraft.crossanywherefabric.config.PluginConfig;
import org.rimecraft.crossanywherefabric.i18n.MessageService;
import org.rimecraft.crossanywherefabric.manager.BackManager;
import org.rimecraft.crossanywherefabric.manager.ConfirmManager;
import org.rimecraft.crossanywherefabric.manager.CooldownManager;
import org.rimecraft.crossanywherefabric.manager.CostManager;
import org.rimecraft.crossanywherefabric.manager.TpaManager;
import org.rimecraft.crossanywherefabric.model.TeleportPosition;
import org.rimecraft.crossanywherefabric.repository.TpaAllowlistRepository;
import org.rimecraft.crossanywherefabric.repository.WaypointRepository;
import org.rimecraft.crossanywherefabric.safety.SafetyChecker;
import org.rimecraft.crossanywherefabric.teleport.TeleportService;
import org.rimecraft.crossanywherefabric.teleport.RandomTeleportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Crossanywherefabric implements ModInitializer {
    public static final String MOD_ID = "crossanywherefabric";
    public static final Logger LOGGER = LoggerFactory.getLogger("CrossAnywhere");

    private final BackManager backs = new BackManager();
    private final CooldownManager cooldowns = new CooldownManager();
    private final ConfirmManager confirms = new ConfirmManager();
    private final TpaManager tpa = new TpaManager();
    private Path dataDirectory;
    private MinecraftServer server;
    private PluginConfig config;
    private MessageService messages;
    private WaypointRepository waypoints;
    private TpaAllowlistRepository allowlist;
    private TeleportService teleports;
    private RandomTeleportService randomTeleports;
    private long ticks;

    @Override
    public void onInitialize() {
        dataDirectory = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
        try {
            Files.createDirectories(dataDirectory);
            config = PluginConfig.load(dataDirectory);
            messages = new MessageService(dataDirectory, config.defaultLocale);
            messages.load();
            waypoints = new WaypointRepository(dataDirectory, LOGGER);
            waypoints.load();
            allowlist = new TpaAllowlistRepository(dataDirectory, LOGGER);
            allowlist.load();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize CrossAnywhere", exception);
        }

        CrossAnywhereCommands commands = new CrossAnywhereCommands(this);
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> commands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player && config.backOnDeath) {
                backs.set(player.getUUID(), TeleportPosition.from(player));
                messages.send(player, "back.saved", MessageService.vars("button", messages.component(player, "back.button")));
            }
        });
        LOGGER.info("CrossAnywhere Fabric initialized");
    }

    private void onServerStarting(MinecraftServer server) {
        this.server = server;
        rebuildTeleportPipeline();
    }

    private void onServerStopping(MinecraftServer server) {
        waypoints.save();
        allowlist.save();
        this.server = null;
        teleports = null;
        randomTeleports = null;
    }

    private void onServerTick(MinecraftServer server) {
        ticks++;
        if (ticks % 20 == 0) {
            confirms.tick(server, messages);
            tpa.tick(server, messages);
        }
        long saveInterval = Math.max(0, config.saveIntervalSeconds) * 20L;
        if (saveInterval > 0 && ticks % saveInterval == 0) {
            waypoints.saveIfDirty();
            allowlist.saveIfDirty();
        }
    }

    public boolean reloadAll() {
        try {
            config = PluginConfig.load(dataDirectory);
            messages = new MessageService(dataDirectory, config.defaultLocale);
            messages.load();
            rebuildTeleportPipeline();
            return true;
        } catch (Exception exception) {
            LOGGER.error("Failed to reload CrossAnywhere", exception);
            return false;
        }
    }

    private void rebuildTeleportPipeline() {
        if (server == null) return;
        SafetyChecker safety = new SafetyChecker(config.safety);
        teleports = new TeleportService(server, config, messages, cooldowns,
                new CostManager(config.cost), safety, confirms, backs);
        randomTeleports = new RandomTeleportService(server, config, messages, cooldowns, safety, teleports);
    }

    public Path dataDirectory() { return dataDirectory; }
    public MinecraftServer server() { return server; }
    public PluginConfig config() { return config; }
    public MessageService messages() { return messages; }
    public WaypointRepository waypoints() { return waypoints; }
    public TpaAllowlistRepository allowlist() { return allowlist; }
    public TeleportService teleports() { return teleports; }
    public RandomTeleportService randomTeleports() { return randomTeleports; }
    public BackManager backs() { return backs; }
    public ConfirmManager confirms() { return confirms; }
    public TpaManager tpa() { return tpa; }
}
