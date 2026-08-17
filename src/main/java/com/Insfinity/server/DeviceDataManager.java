package com.Insfinity.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks which player UUIDs have used which device IDs, with last-login timestamps.
 * Persisted as JSON under mods/DeviceLock/devices.json.
 */
public final class DeviceDataManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Path dataDir;
    private final Path devicesFile;

    /** deviceId (uppercase) → { uuid → PlayerRecord } */
    private Map<String, Map<String, PlayerRecord>> deviceMap = new HashMap<>();

    public DeviceDataManager() {
        this.dataDir = FabricLoader.getInstance().getGameDir().resolve("mods").resolve("DeviceLock");
        this.devicesFile = dataDir.resolve("devices.json");
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    public synchronized void load() {
        try {
            if (Files.exists(devicesFile)) {
                String json = Files.readString(devicesFile, StandardCharsets.UTF_8);
                Map<String, Map<String, PlayerRecord>> loaded = GSON.fromJson(json,
                        new TypeToken<Map<String, Map<String, PlayerRecord>>>() {}.getType());
                if (loaded != null) {
                    deviceMap = loaded;
                }
            }
        } catch (IOException e) {
            System.err.println("[DeviceLock] Failed to load devices.json: " + e.getMessage());
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(dataDir);
            Files.writeString(devicesFile, GSON.toJson(deviceMap), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[DeviceLock] Failed to save devices.json: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Recording
    // ------------------------------------------------------------------

    /** Record that a player logged in from a device. */
    public synchronized void recordLogin(UUID uuid, String playerName, String deviceId) {
        String key = deviceId.toUpperCase();
        deviceMap.computeIfAbsent(key, k -> new HashMap<>());
        PlayerRecord rec = new PlayerRecord();
        rec.name = playerName;
        rec.lastLogin = Instant.now().toString();
        deviceMap.get(key).put(uuid.toString(), rec);
        save();
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    /** Returns the device ID most recently used by the given UUID, or null. */
    public synchronized String getDeviceIdByUuid(UUID uuid) {
        String uuidStr = uuid.toString();
        String bestDevice = null;
        Instant bestTime = null;
        for (Map.Entry<String, Map<String, PlayerRecord>> entry : deviceMap.entrySet()) {
            PlayerRecord rec = entry.getValue().get(uuidStr);
            if (rec != null && rec.lastLogin != null) {
                Instant t = Instant.parse(rec.lastLogin);
                if (bestTime == null || t.isAfter(bestTime)) {
                    bestTime = t;
                    bestDevice = entry.getKey();
                }
            }
        }
        return bestDevice;
    }

    /** Returns all player records for a given device ID. */
    public synchronized List<PlayerRecord> getPlayersByDevice(String deviceId) {
        Map<String, PlayerRecord> map = deviceMap.get(deviceId.toUpperCase());
        if (map == null) return List.of();
        List<PlayerRecord> result = new ArrayList<>();
        for (Map.Entry<String, PlayerRecord> e : map.entrySet()) {
            PlayerRecord rec = new PlayerRecord();
            rec.uuid = e.getKey();
            rec.name = e.getValue().name;
            rec.lastLogin = e.getValue().lastLogin;
            result.add(rec);
        }
        result.sort((a, b) -> {
            Instant ta = a.lastLogin != null ? Instant.parse(a.lastLogin) : Instant.EPOCH;
            Instant tb = b.lastLogin != null ? Instant.parse(b.lastLogin) : Instant.EPOCH;
            return tb.compareTo(ta); // newest first
        });
        return result;
    }

    /** Returns all known device IDs. */
    public synchronized List<String> getAllDeviceIds() {
        return new ArrayList<>(deviceMap.keySet());
    }

    // ------------------------------------------------------------------
    // Data class
    // ------------------------------------------------------------------

    public static class PlayerRecord {
        public String uuid;       // set on read for output
        public String name;
        public String lastLogin;  // ISO instant

        public String lastLoginFormatted() {
            if (lastLogin == null) return "Unknown";
            return FMT.format(Instant.parse(lastLogin));
        }
    }
}
