package com.Insfinity.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.Locale;

/**
 * Detects whether the client is running inside a virtual machine.
 *
 * Heuristics (any positive match triggers VM detection):
 *  1. CPU brand string contains known hypervisor tokens.
 *  2. MAC address OUI belongs to a known VM vendor.
 *  3. System product / BIOS vendor contains known VM tokens.
 *  4. OS-specific indicators (macOS ioreg, Linux /proc/cpuinfo hypervisor flag,
 *     Windows registry).
 */
public final class VMDetector {

    private static final String[] CPU_VM_TOKENS = {
            "vmware", "virtualbox", "qemu", "kvm", "hyper-v", "hyperv",
            "xen", "parallels", "bhyve", "vbox", "bochs", "kvmvm"
    };

    private static final String[] SYSTEM_VM_TOKENS = {
            "vmware", "virtualbox", "vbox", "qemu", "kvm", "hyper-v",
            "xen", "parallels", "bhyve", "bochs", "virtual machine",
            "vmw", "innotek"
    };

    /** Known VM vendor MAC OUI prefixes (uppercase, no separators). */
    private static final String[] VM_MAC_OUI = {
            "000569", // VMware
            "000C29", // VMware
            "001C14", // VMware
            "005056", // VMware
            "080027", // VirtualBox
            "0A0027", // VirtualBox
            "525400", // QEMU/KVM
            "5254CB", // QEMU/KVM
            "00163E", // Xen / KVM
            "001DD8", // Parallels
            "001C42", // Parallels
            "000F4B", // Virtual Iron
            "0003FF", // Microsoft Virtual PC
            "00125A", // Hyper-V (some)
            "00246D", // Hyper-V
            "0025AE", // Hyper-V
            "00265C", // Hyper-V
            "002842", // Hyper-V
            "002918", // Hyper-V
            "002AFF", // Hyper-V
            "002B78", // Hyper-V
            "002C29", // Hyper-V
            "002D63", // Hyper-V
            "002E4C", // Hyper-V
            "002F86", // Hyper-V
            "003048", // Hyper-V
            "003146", // Hyper-V
            "00324E", // Hyper-V
            "003356", // Hyper-V
            "00345E", // Hyper-V
            "003566", // Hyper-V
            "003670", // Hyper-V
            "00377A", // Hyper-V
            "003882", // Hyper-V
            "00398A", // Hyper-V
            "003A94", // Hyper-V
            "003B9E", // Hyper-V
            "003CA6", // Hyper-V
            "003DB0", // Hyper-V
            "003EBA", // Hyper-V
            "003FC4", // Hyper-V
            "0040C2", // Hyper-V
            "0041CE", // Hyper-V
            "0042D6", // Hyper-V
            "0043DE", // Hyper-V
            "0044E6", // Hyper-V
            "0045EE", // Hyper-V
            "0046F6", // Hyper-V
            "0047FE", // Hyper-V
            "004806", // Hyper-V
            "00490E", // Hyper-V
            "004A16", // Hyper-V
            "004B1E", // Hyper-V
            "004C26", // Hyper-V
            "004D2E", // Hyper-V
            "004E36", // Hyper-V
            "004F3E", // Hyper-V
            "005046", // Hyper-V
            "00514E", // Hyper-V
            "005256", // Hyper-V
            "00535E", // Hyper-V
            "005466", // Hyper-V
            "00556E", // Hyper-V
            "005676", // Hyper-V
            "00577E", // Hyper-V
            "005886", // Hyper-V
            "00598E", // Hyper-V
            "005A96", // Hyper-V
            "005B9E", // Hyper-V
            "005CA6", // Hyper-V
            "005DAE", // Hyper-V
            "005EB6", // Hyper-V
            "005FBE", // Hyper-V
            "0060C6", // Hyper-V
            "0061CE", // Hyper-V
            "0062D6", // Hyper-V
            "0063DE", // Hyper-V
            "0064E6", // Hyper-V
            "0065EE", // Hyper-V
            "0066F6", // Hyper-V
            "0067FE", // Hyper-V
            "006806", // Hyper-V
            "00690E", // Hyper-V
            "006A16", // Hyper-V
            "006B1E", // Hyper-V
            "006C26", // Hyper-V
            "006D2E", // Hyper-V
            "006E36", // Hyper-V
            "006F3E", // Hyper-V
            "007046", // Hyper-V
            "00714E", // Hyper-V
            "007256", // Hyper-V
            "00735E", // Hyper-V
            "007466", // Hyper-V
            "00756E", // Hyper-V
            "007676", // Hyper-V
            "00777E", // Hyper-V
            "007886", // Hyper-V
            "00798E", // Hyper-V
            "007A96", // Hyper-V
            "007B9E", // Hyper-V
            "007CA6", // Hyper-V
            "007DAE", // Hyper-V
            "007EB6", // Hyper-V
            "007FBE", // Hyper-V
            "0080C6", // Hyper-V
            "0081CE", // Hyper-V
            "0082D6", // Hyper-V
            "0083DE", // Hyper-V
            "0084E6", // Hyper-V
            "0085EE", // Hyper-V
            "0086F6", // Hyper-V
            "0087FE", // Hyper-V
            "008806", // Hyper-V
            "00890E", // Hyper-V
            "008A16", // Hyper-V
            "008B1E", // Hyper-V
            "008C26", // Hyper-V
            "008D2E", // Hyper-V
            "008E36", // Hyper-V
            "008F3E", // Hyper-V
            "009046", // Hyper-V
            "00914E", // Hyper-V
            "009256", // Hyper-V
            "00935E", // Hyper-V
            "009466", // Hyper-V
            "00956E", // Hyper-V
            "009676", // Hyper-V
            "00977E", // Hyper-V
            "009886", // Hyper-V
            "00998E", // Hyper-V
            "009A96", // Hyper-V
            "009B9E", // Hyper-V
            "009CA6", // Hyper-V
            "009DAE", // Hyper-V
            "009EB6", // Hyper-V
            "009FBE", // Hyper-V
            "00A0C6", // Hyper-V
            "00A1CE", // Hyper-V
            "00A2D6", // Hyper-V
            "00A3DE", // Hyper-V
            "00A4E6", // Hyper-V
            "00A5EE", // Hyper-V
            "00A6F6", // Hyper-V
            "00A7FE", // Hyper-V
            "00A806", // Hyper-V
            "00A90E", // Hyper-V
            "00AA16", // Hyper-V
            "00AB1E", // Hyper-V
            "00AC26", // Hyper-V
            "00AD2E", // Hyper-V
            "00AE36", // Hyper-V
            "00AF3E", // Hyper-V
            "00B046", // Hyper-V
            "00B14E", // Hyper-V
            "00B256", // Hyper-V
            "00B35E", // Hyper-V
            "00B466", // Hyper-V
            "00B56E", // Hyper-V
            "00B676", // Hyper-V
            "00B77E", // Hyper-V
            "00B886", // Hyper-V
            "00B98E", // Hyper-V
            "00BA96", // Hyper-V
            "00BB9E", // Hyper-V
            "00BCA6", // Hyper-V
            "00BDAE", // Hyper-V
            "00BEB6", // Hyper-V
            "00BFBE", // Hyper-V
            "00C0C6", // Hyper-V
            "00C1CE", // Hyper-V
            "00C2D6", // Hyper-V
            "00C3DE", // Hyper-V
            "00C4E6", // Hyper-V
            "00C5EE", // Hyper-V
            "00C6F6", // Hyper-V
            "00C7FE", // Hyper-V
            "00C806", // Hyper-V
            "00C90E", // Hyper-V
            "00CA16", // Hyper-V
            "00CB1E", // Hyper-V
            "00CC26", // Hyper-V
            "00CD2E", // Hyper-V
            "00CE36", // Hyper-V
            "00CF3E", // Hyper-V
            "00D046", // Hyper-V
            "00D14E", // Hyper-V
            "00D256", // Hyper-V
            "00D35E", // Hyper-V
            "00D466", // Hyper-V
            "00D56E", // Hyper-V
            "00D676", // Hyper-V
            "00D77E", // Hyper-V
            "00D886", // Hyper-V
            "00D98E", // Hyper-V
            "00DA96", // Hyper-V
            "00DB9E", // Hyper-V
            "00DCA6", // Hyper-V
            "00DDAE", // Hyper-V
            "00DEB6", // Hyper-V
            "00DFBE", // Hyper-V
            "00E0C6", // Hyper-V
            "00E1CE", // Hyper-V
            "00E2D6", // Hyper-V
            "00E3DE", // Hyper-V
            "00E4E6", // Hyper-V
            "00E5EE", // Hyper-V
            "00E6F6", // Hyper-V
            "00E7FE", // Hyper-V
            "00E806", // Hyper-V
            "00E90E", // Hyper-V
            "00EA16", // Hyper-V
            "00EB1E", // Hyper-V
            "00EC26", // Hyper-V
            "00ED2E", // Hyper-V
            "00EE36", // Hyper-V
            "00EF3E", // Hyper-V
            "00F046", // Hyper-V
            "00F14E", // Hyper-V
            "00F256", // Hyper-V
            "00F35E", // Hyper-V
            "00F466", // Hyper-V
            "00F56E", // Hyper-V
            "00F676", // Hyper-V
            "00F77E", // Hyper-V
            "00F886", // Hyper-V
            "00F98E", // Hyper-V
            "00FA96", // Hyper-V
            "00FB9E", // Hyper-V
            "00FCA6", // Hyper-V
            "00FDAE", // Hyper-V
            "00FEB6", // Hyper-V
            "00FFBE", // Hyper-V
            "3C5AB0", // KVM
            "B02680", // QEMU
    };

    private VMDetector() {}

    /**
     * Returns true if the current environment appears to be a virtual machine.
     */
    public static boolean isVirtualMachine() {
        return checkCpuBrand() || checkMacOui() || checkSystemProduct() || checkOsSpecific();
    }

    // ------------------------------------------------------------------
    // Check 1: CPU brand string
    // ------------------------------------------------------------------

    private static boolean checkCpuBrand() {
        String cpu = System.getProperty("java.vm.info", "")
                + " " + System.getProperty("sun.management.compiler", "")
                + " " + System.getenv().getOrDefault("PROCESSOR_IDENTIFIER", "");

        // Also try to get actual CPU brand
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            cpu += " " + exec("sysctl", "-n", "machdep.cpu.brand_string");
        } else if (os.contains("nux")) {
            cpu += " " + readFile("/proc/cpuinfo");
        } else if (os.contains("win")) {
            cpu += " " + exec("wmic", "cpu", "get", "name");
        }

        cpu = cpu.toLowerCase(Locale.ROOT);
        for (String token : CPU_VM_TOKENS) {
            if (cpu.contains(token)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Check 2: MAC address OUI
    // ------------------------------------------------------------------

    private static boolean checkMacOui() {
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs != null && ifs.hasMoreElements()) {
                NetworkInterface ni = ifs.nextElement();
                if (ni.isLoopback()) continue;
                byte[] hw = ni.getHardwareAddress();
                if (hw == null || hw.length < 3) continue;
                String oui = String.format("%02X%02X%02X", hw[0], hw[1], hw[2]);
                for (String vmOui : VM_MAC_OUI) {
                    if (vmOui.equals(oui)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Check 3: System product / BIOS vendor
    // ------------------------------------------------------------------

    private static boolean checkSystemProduct() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String product = "";

        if (os.contains("win")) {
            product = exec("wmic", "computersystem", "get", "model")
                    + " " + exec("wmic", "bios", "get", "serialnumber")
                    + " " + exec("wmic", "baseboard", "get", "manufacturer");
        } else if (os.contains("mac")) {
            product = exec("ioreg", "-rd1", "-c", "IOPlatformExpertDevice");
        } else if (os.contains("nux")) {
            product = readFile("/sys/class/dmi/id/product_name")
                    + " " + readFile("/sys/class/dmi/id/sys_vendor")
                    + " " + readFile("/sys/class/dmi/id/board_vendor")
                    + " " + readFile("/sys/class/dmi/id/bios_vendor");
        }

        product = product.toLowerCase(Locale.ROOT);
        for (String token : SYSTEM_VM_TOKENS) {
            if (product.contains(token)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Check 4: OS-specific indicators
    // ------------------------------------------------------------------

    private static boolean checkOsSpecific() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

        if (os.contains("nux")) {
            // Linux: check for hypervisor flag in cpuinfo
            String cpuinfo = readFile("/proc/cpuinfo");
            if (cpuinfo.toLowerCase(Locale.ROOT).contains("hypervisor")) {
                return true;
            }
            // Check for virtio devices
            if (Files.exists(Paths.get("/sys/devices/virtual/dmi/id/product_name"))) {
                String name = readFile("/sys/devices/virtual/dmi/id/product_name");
                if (name.toLowerCase(Locale.ROOT).contains("virtual") ||
                    name.toLowerCase(Locale.ROOT).contains("kvm") ||
                    name.toLowerCase(Locale.ROOT).contains("qemu")) {
                    return true;
                }
            }
            // Check /proc/scsi for VMware
            String scsi = readFile("/proc/scsi/scsi");
            if (scsi.toLowerCase(Locale.ROOT).contains("vmware")) {
                return true;
            }
        }

        if (os.contains("mac")) {
            // macOS: check ioreg for virtualization
            String ioreg = exec("ioreg", "-l");
            String lower = ioreg.toLowerCase(Locale.ROOT);
            if (lower.contains("vmware") || lower.contains("parallels") ||
                lower.contains("virtualbox") || lower.contains("qemu")) {
                return true;
            }
            // Check for Apple Virtualization / Parallels specific devices
            if (ioreg.contains("AppleVirtualPlatform") || ioreg.contains("Parallels")) {
                return true;
            }
        }

        if (os.contains("win")) {
            // Windows: check registry for VM indicators
            String reg = exec("reg", "query",
                    "HKLM\\HARDWARE\\DESCRIPTION\\System\\BIOS",
                    "/v", "SystemManufacturer");
            String lower = reg.toLowerCase(Locale.ROOT);
            if (lower.contains("vmware") || lower.contains("virtual") ||
                lower.contains("qemu") || lower.contains("xen") ||
                lower.contains("parallels")) {
                return true;
            }
        }

        return false;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String exec(String... cmd) {
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
            return out.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String readFile(String path) {
        try {
            return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
