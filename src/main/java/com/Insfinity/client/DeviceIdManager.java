package com.Insfinity.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/**
 * Collects stable hardware identifiers from the client machine and produces
 * a deterministic device ID (SHA-256, first 32 hex chars).
 *
 * The result is cached on disk so that transient unavailability of one
 * hardware source does not change the ID between sessions.
 */
public final class DeviceIdManager {

    private static final String CACHE_FILE_NAME = "device_id.cache";
    private static String cachedDeviceId;

    private DeviceIdManager() {}

    /**
     * Returns the stable device ID for this machine.
     * Tries the cache first; otherwise collects hardware info, hashes it,
     * and writes the cache.
     */
    public static synchronized String getDeviceId() {
        if (cachedDeviceId != null) {
            return cachedDeviceId;
        }

        // Try disk cache
        String fromCache = readCache();
        if (fromCache != null && !fromCache.isBlank()) {
            cachedDeviceId = fromCache.trim();
            return cachedDeviceId;
        }

        // Collect and hash
        String raw = collectHardwareFingerprint();
        String id = sha256Hex(raw).substring(0, 32).toUpperCase(Locale.ROOT);
        cachedDeviceId = id;
        writeCache(id);
        return id;
    }

    // ------------------------------------------------------------------
    // Hardware collection
    // ------------------------------------------------------------------

    private static String collectHardwareFingerprint() {
        StringBuilder sb = new StringBuilder();

        // 1. MAC addresses (physical interfaces only)
        sb.append("MAC:").append(collectMacAddresses()).append(';');

        // 2. OS name + arch (stable per install)
        sb.append("OS:").append(System.getProperty("os.name", "unknown"))
          .append('|').append(System.getProperty("os.arch", "unknown")).append(';');

        // 3. CPU info
        sb.append("CPU:").append(collectCpuInfo()).append(';');

        // 4. System / motherboard serial
        sb.append("SYS:").append(collectSystemSerial()).append(';');

        // NOTE: intentionally excludes user.home — device ID should identify
        // the physical machine, not the OS user account. This prevents
        // bypass via creating a new OS account and avoids false positives
        // for legitimate multi-user machines.

        return sb.toString();
    }

    private static String collectMacAddresses() {
        List<String> macs = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs != null && ifs.hasMoreElements()) {
                NetworkInterface ni = ifs.nextElement();
                if (ni.isLoopback() || ni.isVirtual() || ni.isPointToPoint()) {
                    continue;
                }
                byte[] hw = ni.getHardwareAddress();
                if (hw == null || hw.length == 0) {
                    continue;
                }
                StringBuilder mac = new StringBuilder();
                for (byte b : hw) {
                    mac.append(String.format("%02X", b));
                }
                macs.add(mac.toString());
            }
        } catch (Exception ignored) {
        }
        Collections.sort(macs);
        return String.join(",", macs);
    }

    private static String collectCpuInfo() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                // Windows: registry / environment fallback
                String env = System.getenv("PROCESSOR_IDENTIFIER");
                if (env != null) return env;
                return execTrim("wmic", "cpu", "get", "ProcessorId");
            } else if (os.contains("mac")) {
                return execTrim("sysctl", "-n", "machdep.cpu.brand_string")
                        + "|" + execTrim("sysctl", "-n", "hw.model");
            } else {
                // Linux
                return readFileTrim("/proc/cpuinfo")
                        .replaceAll("(?m)^(?!model name|processor|cpu family).*$", "")
                        .replaceAll("\\s+", " ");
            }
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String collectSystemSerial() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                return execTrim("wmic", "baseboard", "get", "serialnumber")
                        + "|" + execTrim("wmic", "bios", "get", "serialnumber");
            } else if (os.contains("mac")) {
                return execTrim("ioreg", "-rd1", "-c", "IOPlatformExpertDevice")
                        .replaceAll("(?s).*\"IOPlatformUUID\"\\s*=\\s*\"([^\"]+)\".*", "$1");
            } else {
                // Linux: try dmidecode (may need root), then /etc/machine-id
                String dmi = execTrim("cat", "/sys/class/dmi/id/product_uuid");
                if (dmi != null && !dmi.isBlank()) return dmi;
                return readFileTrim("/etc/machine-id");
            }
        } catch (Exception e) {
            return "unknown";
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String execTrim(String... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            p.waitFor();
            return out.toString().trim().replaceAll("\\s+", " ");
        } catch (Exception e) {
            return "";
        }
    }

    private static String readFileTrim(String path) {
        try {
            return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                    .trim().replaceAll("\\s+", " ");
        } catch (Exception e) {
            return "";
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            // Fallback: use hashCode-based pseudo ID (should never happen)
            return String.format("%064d", Math.abs((long) input.hashCode()));
        }
    }

    // ------------------------------------------------------------------
    // Cache
    // ------------------------------------------------------------------

    private static Path cachePath() {
        // Store in the game config directory if available, else user home
        String configDir = System.getProperty("fabric.config.dir",
                System.getProperty("user.home", ".") + File.separator + ".config");
        return Paths.get(configDir, "devicelock", CACHE_FILE_NAME);
    }

    private static String readCache() {
        try {
            Path p = cachePath();
            if (Files.exists(p)) {
                return new String(Files.readAllBytes(p), StandardCharsets.UTF_8).trim();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void writeCache(String id) {
        try {
            Path p = cachePath();
            Files.createDirectories(p.getParent());
            Files.write(p, id.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }
}
