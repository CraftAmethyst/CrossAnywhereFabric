package org.rimecraft.crossanywherefabric.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TpaAllowlistRepository {
    private static final Type TYPE = new TypeToken<Map<String, Set<String>>>() { }.getType();
    private final Path file;
    private final Logger logger;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, Set<String>> allowlist = new HashMap<>();
    private boolean dirty;

    public TpaAllowlistRepository(Path dataFolder, Logger logger) {
        file = dataFolder.resolve("tpa_allowlist.json");
        this.logger = logger;
    }

    public synchronized void load() {
        allowlist.clear();
        try {
            if (Files.exists(file)) {
                Map<String, Set<String>> loaded = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), TYPE);
                if (loaded != null) {
                    loaded.forEach((key, value) -> allowlist.put(key, value == null ? new HashSet<>() : new HashSet<>(value)));
                }
            }
        } catch (Exception exception) {
            logger.error("Failed to load {}", file, exception);
        }
        dirty = false;
    }

    public synchronized void saveIfDirty() {
        if (dirty) save();
    }

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, gson.toJson(allowlist), StandardCharsets.UTF_8);
            dirty = false;
        } catch (Exception exception) {
            logger.error("Failed to save {}", file, exception);
        }
    }

    public synchronized boolean isAllowed(UUID target, UUID sender) {
        Set<String> values = allowlist.get(target.toString());
        return values != null && values.contains(sender.toString());
    }

    public synchronized boolean add(UUID target, UUID allowed) {
        boolean added = allowlist.computeIfAbsent(target.toString(), ignored -> new HashSet<>()).add(allowed.toString());
        dirty |= added;
        return added;
    }

    public synchronized boolean remove(UUID target, UUID allowed) {
        Set<String> values = allowlist.get(target.toString());
        if (values == null || !values.remove(allowed.toString())) return false;
        if (values.isEmpty()) allowlist.remove(target.toString());
        dirty = true;
        return true;
    }

    public synchronized List<UUID> list(UUID target) {
        Set<String> values = allowlist.get(target.toString());
        if (values == null) return List.of();
        List<UUID> result = new ArrayList<>();
        for (String value : values) {
            try {
                result.add(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }
}
