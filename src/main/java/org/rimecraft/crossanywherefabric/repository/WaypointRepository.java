package org.rimecraft.crossanywherefabric.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.rimecraft.crossanywherefabric.model.Waypoint;
import org.rimecraft.crossanywherefabric.util.NameValidator;
import org.slf4j.Logger;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public final class WaypointRepository {
    private static final Type PERSONAL_TYPE = new TypeToken<Map<String, Map<String, Waypoint>>>() { }.getType();
    private static final Type GLOBAL_TYPE = new TypeToken<Map<String, Waypoint>>() { }.getType();

    private final Path personalFile;
    private final Path globalFile;
    private final Logger logger;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, Map<String, Waypoint>> personal = new HashMap<>();
    private final Map<String, Waypoint> global = new HashMap<>();
    private boolean dirty;

    public WaypointRepository(Path dataFolder, Logger logger) {
        personalFile = dataFolder.resolve("personal_waypoints.json");
        globalFile = dataFolder.resolve("global_waypoints.json");
        this.logger = logger;
    }

    public synchronized void load() {
        personal.clear();
        global.clear();
        personal.putAll(read(personalFile, PERSONAL_TYPE, HashMap::new));
        global.putAll(read(globalFile, GLOBAL_TYPE, HashMap::new));
        dirty = false;
    }

    private <T> T read(Path file, Type type, Supplier<T> emptyValue) {
        try {
            if (!Files.exists(file)) {
                return emptyValue.get();
            }
            T value = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), type);
            if (value != null) {
                return value;
            }
        } catch (Exception exception) {
            logger.error("Failed to load {}", file, exception);
        }
        return emptyValue.get();
    }

    public synchronized void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(personalFile.getParent());
            Files.writeString(personalFile, gson.toJson(personal), StandardCharsets.UTF_8);
            Files.writeString(globalFile, gson.toJson(global), StandardCharsets.UTF_8);
            dirty = false;
        } catch (Exception exception) {
            logger.error("Failed to save waypoint data", exception);
        }
    }

    public synchronized void replaceAll(Map<String, Map<String, Waypoint>> personalData, Map<String, Waypoint> globalData) {
        personal.clear();
        global.clear();
        if (personalData != null) personal.putAll(personalData);
        if (globalData != null) global.putAll(globalData);
        dirty = true;
    }

    public synchronized Waypoint getPersonal(UUID uuid, String name) {
        Map<String, Waypoint> map = personal.get(uuid.toString());
        return map == null ? null : map.get(NameValidator.key(name));
    }

    public synchronized Waypoint getGlobal(String name) {
        return global.get(NameValidator.key(name));
    }

    public synchronized boolean setPersonal(UUID uuid, Waypoint waypoint) {
        Map<String, Waypoint> map = personal.computeIfAbsent(uuid.toString(), ignored -> new HashMap<>());
        boolean existed = map.put(NameValidator.key(waypoint.getName()), waypoint) != null;
        dirty = true;
        return existed;
    }

    public synchronized boolean setGlobal(Waypoint waypoint) {
        boolean existed = global.put(NameValidator.key(waypoint.getName()), waypoint) != null;
        dirty = true;
        return existed;
    }

    public synchronized boolean deletePersonal(UUID uuid, String name) {
        Map<String, Waypoint> map = personal.get(uuid.toString());
        if (map == null || map.remove(NameValidator.key(name)) == null) return false;
        if (map.isEmpty()) personal.remove(uuid.toString());
        dirty = true;
        return true;
    }

    public synchronized boolean deleteGlobal(String name) {
        if (global.remove(NameValidator.key(name)) == null) return false;
        dirty = true;
        return true;
    }

    public synchronized List<Waypoint> listPersonal(UUID uuid) {
        Map<String, Waypoint> map = personal.get(uuid.toString());
        return map == null ? Collections.emptyList() : new ArrayList<>(map.values());
    }

    public synchronized List<Waypoint> listGlobal() {
        return new ArrayList<>(global.values());
    }

    public synchronized int countPersonal(UUID uuid) {
        Map<String, Waypoint> map = personal.get(uuid.toString());
        return map == null ? 0 : map.size();
    }

    public synchronized int countGlobal() {
        return global.size();
    }
}
