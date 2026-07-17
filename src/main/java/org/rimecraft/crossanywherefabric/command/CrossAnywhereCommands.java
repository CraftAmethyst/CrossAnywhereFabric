package org.rimecraft.crossanywherefabric.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import org.rimecraft.crossanywherefabric.Crossanywherefabric;
import org.rimecraft.crossanywherefabric.config.PluginConfig;
import org.rimecraft.crossanywherefabric.i18n.MessageService;
import org.rimecraft.crossanywherefabric.importer.StpImporter;
import org.rimecraft.crossanywherefabric.manager.ConfirmManager;
import org.rimecraft.crossanywherefabric.manager.TpaManager;
import org.rimecraft.crossanywherefabric.model.TeleportPosition;
import org.rimecraft.crossanywherefabric.model.Waypoint;
import org.rimecraft.crossanywherefabric.teleport.TeleportService;
import org.rimecraft.crossanywherefabric.teleport.TeleportType;
import org.rimecraft.crossanywherefabric.util.NameValidator;
import org.rimecraft.crossanywherefabric.util.Permissions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CrossAnywhereCommands {
    private static final List<String> SUB_COMMANDS = List.of(
            "help", "setp", "setg", "tpp", "tpg", "delp", "delg", "list", "listp", "listg",
            "descp", "descg", "tp", "tphere", "tpa", "tpahere", "accept", "deny", "cancel",
            "tpaallow", "tpadisallow", "tpaallowlist", "back", "rtp", "confirm", "cancelconfirm", "reload", "importstp"
    );

    private final Crossanywherefabric mod;

    public CrossAnywhereCommands(Crossanywherefabric mod) {
        this.mod = mod;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = dispatcher.register(Commands.literal("ca")
                .executes(context -> execute(context.getSource(), ""))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .suggests(this::suggest)
                        .executes(context -> execute(context.getSource(), StringArgumentType.getString(context, "args")))));
        dispatcher.register(Commands.literal("stp").redirect(root));

        registerShortcut(dispatcher, "setp", "setp");
        registerShortcut(dispatcher, "tpp", "tpp");
        registerShortcut(dispatcher, "delp", "delp");
        registerShortcut(dispatcher, "listp", "listp");
        registerShortcut(dispatcher, "descp", "descp");
        registerShortcut(dispatcher, "setg", "setg");
        registerShortcut(dispatcher, "tpg", "tpg");
        registerShortcut(dispatcher, "delg", "delg");
        registerShortcut(dispatcher, "listg", "listg");
        registerShortcut(dispatcher, "descg", "descg");
        registerShortcut(dispatcher, "tplist", "list");
        registerShortcut(dispatcher, "back", "back");
        registerShortcut(dispatcher, "tpa", "tpa");
        registerShortcut(dispatcher, "tpahere", "tpahere");
        registerShortcut(dispatcher, "tphere", "tphere");
        registerShortcut(dispatcher, "tpaccept", "accept");
        registerShortcut(dispatcher, "tpdeny", "deny");
        registerShortcut(dispatcher, "tpcancel", "cancel");
        registerShortcut(dispatcher, "tpconfirm", "confirm");
        registerShortcut(dispatcher, "tpcancelconfirm", "cancelconfirm");
        registerShortcut(dispatcher, "rtp", "rtp");
        registerShortcut(dispatcher, "tpr", "rtp");
        registerShortcut(dispatcher, "r", "rtp");
        if (mod.config().commands.overrideTp()) registerShortcut(dispatcher, "tp", "tp");
    }

    private void registerShortcut(CommandDispatcher<CommandSourceStack> dispatcher, String literal, String prepend) {
        dispatcher.register(Commands.literal(literal)
                .executes(context -> execute(context.getSource(), prepend))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .suggests((context, builder) -> suggestShortcut(prepend, context, builder))
                        .executes(context -> execute(context.getSource(), prepend + " " + StringArgumentType.getString(context, "args")))));
    }

    private int execute(CommandSourceStack source, String raw) {
        String[] args = split(raw);
        if (args.length == 0) {
            mod.messages().send(source, "help.header");
            return 1;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        ServerPlayer player = source.getPlayer();
        if (!"help".equals(sub) && player != null && !mod.config().isWorldAllowed(TeleportPosition.worldName(player.level()))) {
            mod.messages().send(source, "world.not_allowed");
            return 0;
        }

        return switch (sub) {
            case "help" -> message(source, "help.header");
            case "setp", "setpersonal" -> setWaypoint(source, args, true);
            case "setg", "setglobal" -> setWaypoint(source, args, false);
            case "tpp", "tpersonal" -> teleportWaypoint(source, args, true);
            case "tpg", "tglobal" -> teleportWaypoint(source, args, false);
            case "delp", "delpersonal" -> deleteWaypoint(source, args, true);
            case "delg", "delglobal" -> deleteWaypoint(source, args, false);
            case "listp", "listpersonal" -> listWaypoints(source, true, false);
            case "listg", "listglobal" -> listWaypoints(source, false, true);
            case "list" -> listWaypoints(source, true, true);
            case "descp" -> describeWaypoint(source, args, true);
            case "descg" -> describeWaypoint(source, args, false);
            case "tp" -> directTeleport(source, args, true);
            case "tphere" -> directTeleport(source, args, false);
            case "tpa" -> requestTpa(source, args, true);
            case "tpahere" -> requestTpa(source, args, false);
            case "accept", "allow" -> acceptTpa(source, args);
            case "deny", "reject" -> denyTpa(source, args);
            case "cancel" -> cancelTpa(source);
            case "tpaallow" -> updateAllowlist(source, args, true);
            case "tpadisallow" -> updateAllowlist(source, args, false);
            case "tpaallowlist" -> showAllowlist(source);
            case "back" -> back(source);
            case "rtp" -> randomTeleport(source);
            case "confirm" -> confirm(source);
            case "cancelconfirm" -> cancelConfirm(source);
            case "reload" -> reload(source);
            case "importstp" -> importStp(source, args);
            default -> mod.config().easyTp && args.length == 1 ? easyTeleport(source, args[0]) : message(source, "help.header");
        };
    }

    private int setWaypoint(CommandSourceStack source, String[] args, boolean personal) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        if (!Permissions.has(player, personal ? "personal" : "global", personal)) return denied(source);
        int index = 1;
        boolean force = index < args.length && "-f".equalsIgnoreCase(args[index]);
        if (force) index++;
        if (index >= args.length) return message(source, "usage.set");
        String name = NameValidator.normalize(args[index]);
        if (!NameValidator.isValid(name, mod.config().waypointNameMaxLength, mod.config().allowUnicodeNames)) {
            return message(source, "waypoint.invalid_name", "max", mod.config().waypointNameMaxLength);
        }
        String description = index + 1 < args.length ? String.join(" ", Arrays.copyOfRange(args, index + 1, args.length)) : "";
        Waypoint existing = personal ? mod.waypoints().getPersonal(player.getUUID(), name) : mod.waypoints().getGlobal(name);
        int count = personal ? mod.waypoints().countPersonal(player.getUUID()) : mod.waypoints().countGlobal();
        int limit = personal ? mod.config().personalMaxWaypoints : mod.config().globalMaxWaypoints;
        if (existing == null && count >= limit) return message(source, "waypoint.limit", "max", limit);
        if (existing != null && !force) return message(source, "waypoint.exists");

        long now = Instant.now().getEpochSecond();
        TeleportPosition position = TeleportPosition.from(player);
        Waypoint waypoint = new Waypoint(name, position.world(), position.x(), position.y(), position.z(),
                position.yaw(), position.pitch(), description, personal ? player.getUUID() : null,
                existing == null ? now : existing.getCreatedAt(), now);
        if (personal) mod.waypoints().setPersonal(player.getUUID(), waypoint);
        else mod.waypoints().setGlobal(waypoint);
        return message(source, existing == null ? "waypoint.created" : "waypoint.updated", "name", name);
    }

    private int teleportWaypoint(CommandSourceStack source, String[] args, boolean personal) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        if (!Permissions.has(player, personal ? "personal.tp" : "global.tp", true)) return denied(source);
        if (args.length < 2) return message(source, "usage.tp_waypoint");
        Waypoint waypoint = personal ? mod.waypoints().getPersonal(player.getUUID(), args[1]) : mod.waypoints().getGlobal(args[1]);
        if (waypoint == null) return message(source, "waypoint.not_found", "name", args[1]);
        TeleportService.Result result = mod.teleports().teleport(player, waypoint.position(),
                personal ? TeleportType.WAYPOINT_PERSONAL : TeleportType.WAYPOINT_GLOBAL, false);
        return result == TeleportService.Result.SUCCESS
                ? message(source, "teleport.success_waypoint", "name", waypoint.getName()) : 0;
    }

    private int deleteWaypoint(CommandSourceStack source, String[] args, boolean personal) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        if (!Permissions.has(player, personal ? "personal" : "global", personal)) return denied(source);
        if (args.length < 2) return message(source, "usage.del");
        boolean removed = personal ? mod.waypoints().deletePersonal(player.getUUID(), args[1]) : mod.waypoints().deleteGlobal(args[1]);
        return message(source, removed ? "waypoint.deleted" : "waypoint.not_found", "name", args[1]);
    }

    private int describeWaypoint(CommandSourceStack source, String[] args, boolean personal) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        if (!Permissions.has(player, personal ? "personal" : "global", personal)) return denied(source);
        if (args.length < 3) return message(source, "usage.desc");
        Waypoint waypoint = personal ? mod.waypoints().getPersonal(player.getUUID(), args[1]) : mod.waypoints().getGlobal(args[1]);
        if (waypoint == null) return message(source, "waypoint.not_found", "name", args[1]);
        waypoint.setDescription(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
        waypoint.setUpdatedAt(Instant.now().getEpochSecond());
        if (personal) mod.waypoints().setPersonal(player.getUUID(), waypoint);
        else mod.waypoints().setGlobal(waypoint);
        return message(source, "waypoint.desc_updated", "name", waypoint.getName());
    }

    private int listWaypoints(CommandSourceStack source, boolean personal, boolean global) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        if (!Permissions.has(player, "list", true)) return denied(source);
        mod.messages().send(player, "list.header");
        if (personal) sendList(player, "list.personal", mod.waypoints().listPersonal(player.getUUID()), true);
        if (global) sendList(player, "list.global", mod.waypoints().listGlobal(), false);
        return 1;
    }

    private void sendList(ServerPlayer player, String header, List<Waypoint> waypoints, boolean personal) {
        mod.messages().send(player, header, MessageService.vars("count", waypoints.size()));
        waypoints.stream().sorted(Comparator.comparing(Waypoint::getName, String.CASE_INSENSITIVE_ORDER))
                .forEach(waypoint -> player.sendSystemMessage(waypointEntry(player, waypoint, personal)));
    }

    private Component waypointEntry(ServerPlayer player, Waypoint waypoint, boolean personal) {
        String scope = personal ? "p" : "g";
        Component teleport = mod.messages().button(player, "list.button.teleport.label",
                "/ca tp" + scope + " " + waypoint.getName(), "list.button.teleport.hover", false);
        Component delete = Component.empty();
        Component edit = Component.empty();
        if (Permissions.has(player, personal ? "personal" : "global", personal)) {
            delete = mod.messages().button(player, "list.button.delete.label",
                    "/ca del" + scope + " " + waypoint.getName(), "list.button.delete.hover", false);
            edit = mod.messages().button(player, "list.button.desc.label",
                    "/ca desc" + scope + " " + waypoint.getName() + " ", "list.button.desc.hover", true);
        }
        String description = waypoint.getDescription() == null ? "" : waypoint.getDescription();
        Component desc = Component.literal(description.length() > 30 ? description.substring(0, 30) + "..." : description);
        if (description.length() > 30) {
            desc = desc.copy().withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(Component.literal(description))));
        }
        return mod.messages().component(player, "list.entry", MessageService.vars(
                "name", waypoint.getName(), "world", waypoint.getWorld(),
                "x", format(waypoint.getX()), "y", format(waypoint.getY()), "z", format(waypoint.getZ()),
                "yaw", format(waypoint.getYaw()), "pitch", format(waypoint.getPitch()),
                "desc", desc, "teleport", teleport, "delete", delete, "edit", edit));
    }

    private int directTeleport(CommandSourceStack source, String[] args, boolean toTarget) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        if (!Permissions.has(player, toTarget ? "tp" : "tphere", false)) return denied(source);
        if (args.length < 2) return message(source, "usage.tp_player");
        ServerPlayer target = findPlayer(args[1]);
        if (target == null) return message(source, "player.not_found", "player", args[1]);
        ServerPlayer teleported = toTarget ? player : target;
        TeleportPosition destination = TeleportPosition.from(toTarget ? target : player);
        TeleportService.Result result = mod.teleports().teleport(teleported, destination,
                toTarget ? TeleportType.TP : TeleportType.TPHERE, false);
        if (result != TeleportService.Result.SUCCESS) return 0;
        return message(source, toTarget ? "tp.success" : "tphere.success", "player", target.getName().getString());
    }

    private int requestTpa(CommandSourceStack source, String[] args, boolean toTarget) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        if (!Permissions.has(player, toTarget ? "tpa" : "tpahere", true)) return denied(source);
        if (args.length < 2) return message(source, "usage.tpa");
        ServerPlayer target = findPlayer(args[1]);
        if (target == null) return message(source, "player.not_found", "player", args[1]);
        if (target.getUUID().equals(player.getUUID())) return message(source, "tpa.self");
        TeleportPosition from = TeleportPosition.from(player);
        TeleportPosition destination = TeleportPosition.from(target);
        if (!mod.config().isWorldAllowed(destination.world())) return message(source, "world.not_allowed_target", "world", destination.world());
        if (!from.world().equalsIgnoreCase(destination.world()) && !Permissions.has(player, "crossworld", false)) {
            return message(source, "world.crossworld_denied", "world", destination.world());
        }
        if (toTarget && mod.allowlist().isAllowed(target.getUUID(), player.getUUID())) {
            TeleportService.Result result = mod.teleports().teleport(player, destination, TeleportType.TPA, false);
            if (result == TeleportService.Result.SUCCESS) {
                message(source, "tpa.auto.sent", "player", target.getName().getString());
                mod.messages().send(target, "tpa.auto.received", MessageService.vars("player", player.getName().getString()));
            }
            return result == TeleportService.Result.SUCCESS ? 1 : 0;
        }

        long now = Instant.now().getEpochSecond();
        TpaManager.TpaRequest request = new TpaManager.TpaRequest(player.getUUID(), target.getUUID(),
                toTarget ? TpaManager.Type.TO_TARGET : TpaManager.Type.HERE, now, now + mod.config().tpaTimeoutSeconds);
        if (!mod.tpa().add(request, mod.config().tpaDuplicatePolicy)) return message(source, "tpa.duplicate");
        Component accept = mod.messages().button(target, "tpa.accept.label", "/ca accept " + player.getName().getString(), "tpa.accept.hover", false);
        Component deny = mod.messages().button(target, "tpa.deny.label", "/ca deny " + player.getName().getString(), "tpa.deny.hover", false);
        mod.messages().send(target, toTarget ? "tpa.request.to_target" : "tpa.request.here",
                MessageService.vars("player", player.getName().getString(), "accept", accept, "deny", deny));
        return message(source, "tpa.sent", "player", target.getName().getString());
    }

    private int cancelTpa(CommandSourceStack source) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        List<TpaManager.TpaRequest> removed = mod.tpa().removeAllFrom(player.getUUID());
        if (removed.isEmpty()) return message(source, "tpa.none");
        message(source, "tpa.cancelled");
        for (TpaManager.TpaRequest request : removed) {
            ServerPlayer target = mod.server().getPlayerList().getPlayer(request.targetId());
            if (target != null) mod.messages().send(target, "tpa.cancelled_target", MessageService.vars("player", player.getName().getString()));
        }
        return 1;
    }

    private int acceptTpa(CommandSourceStack source, String[] args) {
        ServerPlayer target = player(source);
        if (target == null) return 0;
        TpaManager.TpaRequest request = findRequest(target, args);
        if (request == null) return message(source, "tpa.none");
        mod.tpa().remove(request);
        ServerPlayer sender = mod.server().getPlayerList().getPlayer(request.senderId());
        if (sender == null) return message(source, "player.not_found", "player", "?");
        message(source, "tpa.accepted", "player", sender.getName().getString());
        mod.messages().send(sender, "tpa.accepted_sender", MessageService.vars("player", target.getName().getString()));
        if (request.type() == TpaManager.Type.TO_TARGET) {
            mod.teleports().teleport(sender, TeleportPosition.from(target), TeleportType.TPA, false);
        } else {
            mod.teleports().teleport(target, TeleportPosition.from(sender), TeleportType.TPAHERE, false);
        }
        return 1;
    }

    private int denyTpa(CommandSourceStack source, String[] args) {
        ServerPlayer target = player(source);
        if (target == null) return 0;
        TpaManager.TpaRequest request = findRequest(target, args);
        if (request == null) return message(source, "tpa.none");
        mod.tpa().remove(request);
        ServerPlayer sender = mod.server().getPlayerList().getPlayer(request.senderId());
        if (sender != null) mod.messages().send(sender, "tpa.denied", MessageService.vars("player", target.getName().getString()));
        return message(source, "tpa.denied_target", "player", sender == null ? "?" : sender.getName().getString());
    }

    private TpaManager.TpaRequest findRequest(ServerPlayer target, String[] args) {
        if (args.length < 2) return mod.tpa().latest(target.getUUID());
        ServerPlayer sender = findPlayer(args[1]);
        return sender == null ? null : mod.tpa().get(sender.getUUID(), target.getUUID());
    }

    private int updateAllowlist(CommandSourceStack source, String[] args, boolean add) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        if (!Permissions.has(player, "tpa.allowlist", true)) return denied(source);
        if (args.length < 2) return message(source, add ? "usage.tpaallow" : "usage.tpadisallow");
        ServerPlayer online = findPlayer(args[1]);
        UUID uuid = online == null ? StpImporter.offlineUuid(args[1]) : online.getUUID();
        boolean changed = add ? mod.allowlist().add(player.getUUID(), uuid) : mod.allowlist().remove(player.getUUID(), uuid);
        mod.allowlist().saveIfDirty();
        String key = add ? (changed ? "tpa.allow.added" : "tpa.allow.exists")
                : (changed ? "tpa.allow.removed" : "tpa.allow.not_found");
        return message(source, key, "player", online == null ? args[1] : online.getName().getString());
    }

    private int showAllowlist(CommandSourceStack source) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        if (!Permissions.has(player, "tpa.allowlist", true)) return denied(source);
        List<UUID> values = mod.allowlist().list(player.getUUID());
        if (values.isEmpty()) return message(source, "tpa.allow.list_empty");
        List<String> names = values.stream().map(uuid -> {
            ServerPlayer online = mod.server().getPlayerList().getPlayer(uuid);
            return online == null ? uuid.toString() : online.getName().getString();
        }).toList();
        return message(source, "tpa.allow.list", "list", String.join(", ", names));
    }

    private int back(CommandSourceStack source) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        if (!Permissions.has(player, "back", true)) return denied(source);
        TeleportPosition back = mod.backs().get(player.getUUID());
        if (back == null) return message(source, "back.none");
        return mod.teleports().teleport(player, back, TeleportType.BACK, false) == TeleportService.Result.SUCCESS
                ? message(source, "back.success") : 0;
    }

    private int randomTeleport(CommandSourceStack source) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        return mod.randomTeleports().start(player) ? 1 : 0;
    }

    private int confirm(CommandSourceStack source) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        ConfirmManager.PendingTeleport pending = mod.confirms().get(player.getUUID());
        if (pending == null) return message(source, "confirm.none");
        mod.confirms().clear(player.getUUID());
        return mod.teleports().teleport(player, pending.destination(), pending.type(), true) == TeleportService.Result.SUCCESS
                ? message(source, "confirm.success") : 0;
    }

    private int cancelConfirm(CommandSourceStack source) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        mod.confirms().clear(player.getUUID());
        return message(source, "confirm.cancelled");
    }

    private int reload(CommandSourceStack source) {
        if (!Permissions.isAdmin(source)) return denied(source);
        boolean success = mod.reloadAll();
        return message(source, success ? "reload.success" : "importstp.failed", "error", "reload failed");
    }

    private int importStp(CommandSourceStack source, String[] args) {
        if (!Permissions.isAdmin(source)) return denied(source);
        ImportOptions options = ImportOptions.parse(args);
        if (options == null) return message(source, "usage.importstp");
        Path file = mod.dataDirectory().resolve(options.fileName());
        if (!Files.exists(file)) return message(source, "importstp.missing", "file", options.fileName());
        message(source, "importstp.start", "file", options.fileName());
        List<String> warnings = new ArrayList<>();
        try {
            Map<String, String> uuidMap = StpImporter.loadStringMap(mod.dataDirectory().resolve("stp_uuid_map.json"), warnings);
            Map<String, String> worldMap = StpImporter.loadStringMap(mod.dataDirectory().resolve("stp_world_map.json"), warnings);
            StpImporter.Result result = StpImporter.load(mod.server(), file, options.uuidMode(), options.includeBack(), uuidMap, worldMap);
            if (options.clear()) {
                mod.waypoints().replaceAll(result.personal(), result.global());
            } else {
                for (Map.Entry<String, Map<String, Waypoint>> entry : result.personal().entrySet()) {
                    UUID owner = UUID.fromString(entry.getKey());
                    entry.getValue().values().forEach(waypoint -> mod.waypoints().setPersonal(owner, waypoint));
                }
                result.global().values().forEach(mod.waypoints()::setGlobal);
            }
            mod.waypoints().save();
            message(source, "importstp.done", "players", result.personalPlayers(), "personal", result.personalWaypoints(),
                    "global", result.globalWaypoints(), "skipped", result.skipped());
            warnings.addAll(result.warnings());
            if (!warnings.isEmpty()) {
                message(source, "importstp.warnings", "count", warnings.size());
                warnings.forEach(warning -> Crossanywherefabric.LOGGER.warn("[STP Import] {}", warning));
            }
            return 1;
        } catch (Exception exception) {
            Crossanywherefabric.LOGGER.error("STP import failed", exception);
            return message(source, "importstp.failed", "error", exception.getMessage() == null ? "unknown error" : exception.getMessage());
        }
    }

    private int easyTeleport(CommandSourceStack source, String name) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        if (!Permissions.has(player, "easy", true)) return denied(source);
        if (!NameValidator.isValid(name, mod.config().waypointNameMaxLength, mod.config().allowUnicodeNames)) {
            return message(source, "waypoint.invalid_name", "max", mod.config().waypointNameMaxLength);
        }
        if (mod.waypoints().getPersonal(player.getUUID(), name) != null) return teleportWaypoint(source, new String[]{"tpp", name}, true);
        if (mod.waypoints().getGlobal(name) != null) return teleportWaypoint(source, new String[]{"tpg", name}, false);
        ServerPlayer target = findPlayer(name);
        if (target != null) {
            if (Permissions.has(player, "tp", false)) return directTeleport(source, new String[]{"tp", name}, true);
            if (Permissions.has(player, "tpa", true)) return requestTpa(source, new String[]{"tpa", name}, true);
            return denied(source);
        }
        return message(source, "waypoint.not_found", "name", name);
    }

    private CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return suggestFor("", context, builder);
    }

    private CompletableFuture<Suggestions> suggestShortcut(String prepend, CommandContext<CommandSourceStack> context,
                                                            SuggestionsBuilder builder) {
        return suggestFor(prepend + " ", context, builder);
    }

    private CompletableFuture<Suggestions> suggestFor(String prepend, CommandContext<CommandSourceStack> context,
                                                       SuggestionsBuilder builder) {
        String raw = prepend + builder.getRemaining();
        String[] args = split(raw);
        boolean first = args.length <= 1 && !raw.endsWith(" ");
        Set<String> values = new LinkedHashSet<>();
        ServerPlayer player = context.getSource().getPlayer();
        if (first && prepend.isEmpty()) {
            values.addAll(SUB_COMMANDS);
            if (player != null && mod.config().easyTp) {
                mod.waypoints().listPersonal(player.getUUID()).forEach(waypoint -> values.add(waypoint.getName()));
                mod.waypoints().listGlobal().forEach(waypoint -> values.add(waypoint.getName()));
                mod.server().getPlayerList().getPlayers().forEach(online -> values.add(online.getName().getString()));
            }
        } else if (args.length > 0) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (player != null) {
                switch (sub) {
                    case "tpp", "tpersonal", "delp", "delpersonal", "descp" ->
                            mod.waypoints().listPersonal(player.getUUID()).forEach(waypoint -> values.add(waypoint.getName()));
                    case "tpg", "tglobal", "delg", "delglobal", "descg" ->
                            mod.waypoints().listGlobal().forEach(waypoint -> values.add(waypoint.getName()));
                    case "tp", "tpa", "tpahere", "tphere", "tpaallow" ->
                            mod.server().getPlayerList().getPlayers().forEach(online -> values.add(online.getName().getString()));
                    case "tpadisallow" -> mod.allowlist().list(player.getUUID()).forEach(uuid -> values.add(uuid.toString()));
                    case "accept", "allow", "deny", "reject" -> mod.tpa().forTarget(player.getUUID()).forEach(request -> {
                        ServerPlayer sender = mod.server().getPlayerList().getPlayer(request.senderId());
                        if (sender != null) values.add(sender.getName().getString());
                    });
                    case "importstp" -> values.addAll(List.of("--include-back", "--clear", "--offline-uuid", "--raw-uuid", "--auto-uuid"));
                    default -> { }
                }
            }
        }
        int offset = builder.getInput().lastIndexOf(' ') + 1;
        return SharedSuggestionProvider.suggest(values, builder.createOffset(offset));
    }

    private ServerPlayer player(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) mod.messages().send(source, "player_only");
        return player;
    }

    private ServerPlayer findPlayer(String name) {
        return mod.server() == null ? null : mod.server().getPlayerList().getPlayerByName(name);
    }

    private int denied(CommandSourceStack source) {
        return message(source, "no_permission");
    }

    private int message(CommandSourceStack source, String key, Object... variables) {
        mod.messages().send(source, key, MessageService.vars(variables));
        return 1;
    }

    private String format(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private String[] split(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        return trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
    }

    private record ImportOptions(String fileName, boolean includeBack, boolean clear, StpImporter.UuidMode uuidMode) {
        private static ImportOptions parse(String[] args) {
            String fileName = "example_data.json";
            boolean includeBack = false;
            boolean clear = false;
            boolean fileSet = false;
            StpImporter.UuidMode uuidMode = StpImporter.UuidMode.BUKKIT;
            for (int index = 1; index < args.length; index++) {
                String arg = args[index];
                switch (arg.toLowerCase(Locale.ROOT)) {
                    case "--include-back", "-b" -> includeBack = true;
                    case "--clear", "-c" -> clear = true;
                    case "--offline-uuid", "--offline" -> uuidMode = StpImporter.UuidMode.OFFLINE;
                    case "--raw-uuid", "--raw" -> uuidMode = StpImporter.UuidMode.RAW;
                    case "--auto-uuid", "--auto" -> uuidMode = StpImporter.UuidMode.AUTO;
                    default -> {
                        if (arg.startsWith("-") || fileSet) return null;
                        fileName = arg;
                        fileSet = true;
                    }
                }
            }
            return new ImportOptions(fileName, includeBack, clear, uuidMode);
        }
    }
}
