package com.Insfinity.server;

import com.Insfinity.networking.DeviceInfoPayload;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.LoginPacketSender;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Server-side entrypoint.
 *
 * Flow:
 *  1. Login phase  – server queries client for device info (legacy PacketByteBuf);
 *     if VM or banned, the connection is rejected before the player enters the world.
 *  2. Play phase   – client resends device info via CustomPayload so the server
 *     can record the UUID <-> deviceId mapping.
 */
public class DeviceLockServer implements DedicatedServerModInitializer {

    public static BanManager banManager;
    public static DeviceDataManager dataManager;

    @Override
    public void onInitializeServer() {
        banManager = new BanManager();
        dataManager = new DeviceDataManager();
        banManager.load();
        dataManager.load();
        DeviceLockCommand.setManagers(banManager, dataManager);

        // --- Commands ---
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) ->
                        DeviceLockCommand.register(dispatcher));

        // --- Login phase: ask client for device info ---
        ServerLoginConnectionEvents.QUERY_START.register(
                (ServerLoginNetworkHandler handler, MinecraftServer server,
                 LoginPacketSender sender, ServerLoginNetworking.LoginSynchronizer synchronizer) -> {
                    PacketByteBuf buf = PacketByteBufs.create();
                    sender.sendPacket(DeviceInfoPayload.LOGIN_ID.id(), buf);
                });

        // --- Login phase: receive device info response & enforce bans / VM ---
        ServerLoginNetworking.registerGlobalReceiver(DeviceInfoPayload.LOGIN_ID.id(),
                (MinecraftServer server, ServerLoginNetworkHandler handler,
                 boolean understood, PacketByteBuf buf,
                 ServerLoginNetworking.LoginSynchronizer synchronizer,
                 PacketSender responseSender) -> {

                    if (!understood) {
                        synchronizer.waitFor(server.submit(() ->
                                handler.disconnect(Text.literal(
                                        "§cThis server requires the DeviceLock mod\n" +
                                        "§fPlease install it and try again"))));
                        return;
                    }

                    String deviceId = buf.readString();
                    boolean isVM = buf.readBoolean();

                    synchronizer.waitFor(server.submit(() -> {
                        if (isVM) {
                            handler.disconnect(Text.literal("§cVirtual machines are not allowed on this server"));
                            return;
                        }
                        BanManager.BanEntry ban = banManager.getBan(deviceId);
                        if (ban != null) {
                            String reason = ban.reason != null ? ban.reason : "No reason provided";
                            handler.disconnect(Text.literal(
                                    "§cYour device has been banned\n\n" +
                                    "§fReason: §c" + reason + "\n" +
                                    "§fBanned at: §e" + ban.banTimeFormatted() + "\n" +
                                    "§fExpires: §6" + ban.expireTimeFormatted()));
                        }
                    }));
                });

        // --- Play phase: record UUID <-> deviceId mapping (double-check bans) ---
        ServerPlayNetworking.registerGlobalReceiver(DeviceInfoPayload.PLAY_ID,
                (DeviceInfoPayload payload, ServerPlayNetworking.Context context) -> {
                    String deviceId = payload.deviceId();
                    boolean isVM = payload.isVM();
                    ServerPlayerEntity player = context.player();

                    context.server().execute(() -> {
                        if (isVM) {
                            player.networkHandler.disconnect(Text.literal("§cVirtual machines are not allowed on this server"));
                            return;
                        }
                        BanManager.BanEntry ban = banManager.getBan(deviceId);
                        if (ban != null) {
                            String reason = ban.reason != null ? ban.reason : "No reason provided";
                            player.networkHandler.disconnect(Text.literal(
                                    "§cYour device has been banned\n\n" +
                                    "§fReason: §c" + reason + "\n" +
                                    "§fBanned at: §e" + ban.banTimeFormatted() + "\n" +
                                    "§fExpires: §6" + ban.expireTimeFormatted()));
                            return;
                        }
                        dataManager.recordLogin(player.getUuid(),
                                player.getName().getString(), deviceId);
                    });
                });

        System.out.println("[DeviceLock] Server initialized successfully.");
    }
}
