package com.Insfinity.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Registers all /device sub-commands.
 *
 * /device lock   <deviceId> [reason] [durationDays]
 * /device unlock <deviceId>
 * /device client <uuid>
 * /device check  <deviceId>
 * /device list
 */
public final class DeviceLockCommand {

    private static BanManager banManager;
    private static DeviceDataManager dataManager;

    public static void setManagers(BanManager bm, DeviceDataManager dm) {
        banManager = bm;
        dataManager = dm;
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                literal("device")
                        .requires(src -> src.hasPermissionLevel(3))

                        // ---- lock ----
                        .then(literal("lock")
                                .then(argument("deviceId", StringArgumentType.word())
                                        .executes(DeviceLockCommand::lockNoReason)
                                        .then(argument("reason", StringArgumentType.greedyString())
                                                .executes(DeviceLockCommand::lockWithReason))))

                        // ---- unlock ----
                        .then(literal("unlock")
                                .then(argument("deviceId", StringArgumentType.word())
                                        .executes(DeviceLockCommand::unlock)))

                        // ---- client ----
                        .then(literal("client")
                                .then(argument("uuid", StringArgumentType.word())
                                        .executes(DeviceLockCommand::client)))

                        // ---- check ----
                        .then(literal("check")
                                .then(argument("deviceId", StringArgumentType.word())
                                        .executes(DeviceLockCommand::check)))

                        // ---- list ----
                        .then(literal("list")
                                .executes(DeviceLockCommand::list))
        );
    }

    // ------------------------------------------------------------------
    // /device lock
    // ------------------------------------------------------------------

    private static int lockNoReason(CommandContext<ServerCommandSource> ctx) {
        String deviceId = StringArgumentType.getString(ctx, "deviceId").toUpperCase();
        banManager.ban(deviceId, null, -1);
        ctx.getSource().sendFeedback(() -> Text.literal(
                "§a[DeviceLock] §fDevice §e" + deviceId + " §fhas been permanently banned"), false);
        return 1;
    }

    private static int lockWithReason(CommandContext<ServerCommandSource> ctx) {
        String deviceId = StringArgumentType.getString(ctx, "deviceId").toUpperCase();
        String raw = StringArgumentType.getString(ctx, "reason");

        // Parse trailing number as duration (days)
        int duration = -1;
        String reason = raw;
        int lastSpace = raw.lastIndexOf(' ');
        if (lastSpace > 0) {
            String lastToken = raw.substring(lastSpace + 1);
            if (lastToken.matches("\\d+")) {
                duration = Integer.parseInt(lastToken);
                reason = raw.substring(0, lastSpace).trim();
            }
        }

        banManager.ban(deviceId, reason.isBlank() ? null : reason, duration);

        String durText = duration > 0 ? duration + " day(s)" : "Permanent";
        String reasonText = (reason != null && !reason.isBlank()) ? reason : "None specified";
        ctx.getSource().sendFeedback(() -> Text.literal(
                "§a[DeviceLock] §fDevice §e" + deviceId + " §fhas been banned\n" +
                "  Reason: §c" + reasonText + "\n" +
                "  Duration: §6" + durText), false);
        return 1;
    }

    // ------------------------------------------------------------------
    // /device unlock
    // ------------------------------------------------------------------

    private static int unlock(CommandContext<ServerCommandSource> ctx) {
        String deviceId = StringArgumentType.getString(ctx, "deviceId").toUpperCase();
        boolean removed = banManager.unban(deviceId);
        if (removed) {
            ctx.getSource().sendFeedback(() -> Text.literal(
                    "§a[DeviceLock] §fDevice §e" + deviceId + " §fhas been unbanned"), false);
        } else {
            ctx.getSource().sendError(Text.literal(
                    "§c[DeviceLock] §fDevice §e" + deviceId + " §fis not banned"));
        }
        return removed ? 1 : 0;
    }

    // ------------------------------------------------------------------
    // /device client <uuid>
    // ------------------------------------------------------------------

    private static int client(CommandContext<ServerCommandSource> ctx) {
        String uuidStr = StringArgumentType.getString(ctx, "uuid");
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendError(Text.literal("§cInvalid UUID format"));
            return 0;
        }

        String deviceId = dataManager.getDeviceIdByUuid(uuid);
        if (deviceId == null) {
            ctx.getSource().sendFeedback(() -> Text.literal(
                    "§e[DeviceLock] §fNo device record found for UUID §a" + uuid), false);
        } else {
            BanManager.BanEntry ban = banManager.getBan(deviceId);
            String banStatus = ban != null ? "§cBanned" : "§aClean";
            ctx.getSource().sendFeedback(() -> Text.literal(
                    "§a[DeviceLock] §fUUID: §b" + uuid + "\n" +
                    "  Device ID: §e" + deviceId + "\n" +
                    "  Status: " + banStatus), false);
        }
        return 1;
    }

    // ------------------------------------------------------------------
    // /device check <deviceId>
    // ------------------------------------------------------------------

    private static int check(CommandContext<ServerCommandSource> ctx) {
        String deviceId = StringArgumentType.getString(ctx, "deviceId").toUpperCase();
        List<DeviceDataManager.PlayerRecord> players = dataManager.getPlayersByDevice(deviceId);
        BanManager.BanEntry ban = banManager.getBan(deviceId);

        StringBuilder sb = new StringBuilder();
        sb.append("§a[DeviceLock] §fDevice ID: §e").append(deviceId).append("\n");
        sb.append("  Ban status: ").append(ban != null ? "§cBanned" : "§aNot banned").append("\n");
        if (ban != null) {
            sb.append("  Reason: §c").append(ban.reason != null ? ban.reason : "None specified").append("\n");
            sb.append("  Banned at: §f").append(ban.banTimeFormatted()).append("\n");
            sb.append("  Expires: §f").append(ban.expireTimeFormatted()).append("\n");
        }
        sb.append("  §7--- Players who used this device (").append(players.size()).append(") ---§r\n");

        if (players.isEmpty()) {
            sb.append("  §7No records§r");
        } else {
            for (DeviceDataManager.PlayerRecord rec : players) {
                sb.append("  §fPlayer: §a").append(rec.name != null ? rec.name : "?")
                  .append(" §7| UUID: §b").append(rec.uuid)
                  .append(" §7| Last login: §e").append(rec.lastLoginFormatted()).append("\n");
            }
        }

        final String msg = sb.toString();
        ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
        return 1;
    }

    // ------------------------------------------------------------------
    // /device list
    // ------------------------------------------------------------------

    private static int list(CommandContext<ServerCommandSource> ctx) {
        List<String> allDevices = dataManager.getAllDeviceIds();
        Map<String, BanManager.BanEntry> allBans = banManager.getAllBans();

        // Include banned devices that may not have login records
        for (String bannedId : allBans.keySet()) {
            if (!allDevices.contains(bannedId)) {
                allDevices.add(bannedId);
            }
        }

        if (allDevices.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.literal(
                    "§e[DeviceLock] §fNo device records found"), false);
            return 0;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("§a[DeviceLock] §fTotal devices: ").append(allDevices.size()).append("\n");
        sb.append("§7---§r\n");

        for (String deviceId : allDevices) {
            BanManager.BanEntry ban = banManager.getBan(deviceId);
            String status;
            if (ban != null) {
                status = "§cBanned §7(expires " + ban.expireTimeFormatted() + ")§r";
            } else {
                status = "§aClean§r";
            }
            int playerCount = dataManager.getPlayersByDevice(deviceId).size();
            sb.append("  §e").append(deviceId)
              .append(" §7| ").append(status)
              .append(" §7| Players: §f").append(playerCount).append("\n");
        }

        final String msg = sb.toString();
        ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
        return 1;
    }
}
