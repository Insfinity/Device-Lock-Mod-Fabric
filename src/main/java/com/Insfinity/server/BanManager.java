package com.Insfinity.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages device bans, persisted as JSON under mods/DeviceLock/bans.json.
 */
public final class BanManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Path dataDir;
    private final Path bansFile;
    private Map<String, BanEntry> bans = new HashMap<>();

    public BanManager() {
        this.dataDir = FabricLoader.getInstance().getGameDir().resolve("mods").resolve("DeviceLock");
        this.bansFile = dataDir.resolve("bans.json");
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    public synchronized void load() {
        if (Files.exists(bansFile)) {
            try {
                String json = Files.readString(bansFile, StandardCharsets.UTF_8);
                if (json != null && !json.isBlank()) {
                    Map<String, BanEntry> loaded = GSON.fromJson(json,
                            new TypeToken<Map<String, BanEntry>>() {}.getType());
                    if (loaded != null) {
                        bans = loaded;
                    }
                }
            } catch (IOException | JsonSyntaxException e) {
                System.err.println("[DeviceLock] Failed to parse bans.json: " + e.getMessage()
                        + " — backing up corrupted file and starting fresh.");
                backupCorruptedFile(bansFile);
                bans = new HashMap<>();
            }
        }
        // Clean up expired entries
        bans.entrySet().removeIf(e -> {
            BanEntry b = e.getValue();
            return b != null && b.expireTime != null
                    && Instant.parse(b.expireTime).isBefore(Instant.now());
        });
        save();
    }

    private void backupCorruptedFile(Path file) {
        try {
            Path backup = file.resolveSibling(file.getFileName() + ".corrupted");
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            System.err.println("[DeviceLock] Failed to back up corrupted file: " + ex.getMessage());
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(dataDir);
            Files.writeString(bansFile, GSON.toJson(bans), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[DeviceLock] Failed to save bans.json: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Ban operations
    // ------------------------------------------------------------------

    /**
     * Ban a device.
     * @param deviceId the device ID
     * @param reason   ban reason (may be null)
     * @param durationDays ban duration in days; 0 or negative means permanent
     */
    public synchronized void ban(String deviceId, String reason, int durationDays) {
        BanEntry entry = new BanEntry();
        entry.reason = reason;
        entry.banTime = Instant.now().toString();
        if (durationDays > 0) {
            entry.expireTime = Instant.now().plusSeconds((long) durationDays * 86400).toString();
            entry.durationDays = durationDays;
        } else {
            entry.expireTime = null;
            entry.durationDays = -1;
        }
        bans.put(deviceId.toUpperCase(), entry);
        save();
    }

    public synchronized boolean unban(String deviceId) {
        BanEntry removed = bans.remove(deviceId.toUpperCase());
        if (removed != null) {
            save();
            return true;
        }
        return false;
    }

    /** Returns the active ban entry for the device, or null if not banned / expired. */
    public synchronized BanEntry getBan(String deviceId) {
        BanEntry entry = bans.get(deviceId.toUpperCase());
        if (entry == null) return null;
        if (entry.expireTime != null && Instant.parse(entry.expireTime).isBefore(Instant.now())) {
            bans.remove(deviceId.toUpperCase());
            save();
            return null;
        }
        return entry;
    }

    public synchronized Map<String, BanEntry> getAllBans() {
        return new HashMap<>(bans);
    }

    // ------------------------------------------------------------------
    // Data class
    // ------------------------------------------------------------------

    public static class BanEntry {
        public String reason;
        public String banTime;      // ISO instant
        public String expireTime;   // ISO instant, null = permanent
        public int durationDays;    // -1 = permanent

        public boolean isActive() {
            if (expireTime == null) return true;
            return Instant.parse(expireTime).isAfter(Instant.now());
        }

        public String banTimeFormatted() {
            return banTime != null ? FMT.format(Instant.parse(banTime)) : "Unknown";
        }

        public String expireTimeFormatted() {
            if (expireTime == null) return "Permanent";
            return FMT.format(Instant.parse(expireTime));
        }

        public boolean isPermanent() {
            return expireTime == null;
        }
    }
}
