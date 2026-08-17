package com.Insfinity.client;

import com.Insfinity.networking.DeviceInfoPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientLoginNetworkHandler;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;

import java.util.concurrent.CompletableFuture;

public class DeviceLockClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // --- Login phase: respond to server's device-info query (legacy PacketByteBuf API) ---
        ClientLoginNetworking.registerGlobalReceiver(DeviceInfoPayload.LOGIN_ID.id(),
                (MinecraftClient client, ClientLoginNetworkHandler handler,
                 PacketByteBuf buf, java.util.function.Consumer<net.minecraft.network.PacketCallbacks> callbacks) -> {
                    String deviceId = DeviceIdManager.getDeviceId();
                    boolean isVM = VMDetector.isVirtualMachine();

                    PacketByteBuf response = PacketByteBufs.create();
                    response.writeString(deviceId);
                    response.writeBoolean(isVM);
                    return CompletableFuture.completedFuture(response);
                });

        // --- Play phase: proactively send device info so the server can
        //     record the UUID <-> deviceId mapping (login phase has no UUID). ---
        ClientPlayConnectionEvents.JOIN.register(
                (ClientPlayNetworkHandler handler, PacketSender sender, MinecraftClient client) -> {
                    String deviceId = DeviceIdManager.getDeviceId();
                    boolean isVM = VMDetector.isVirtualMachine();
                    sender.sendPacket(new DeviceInfoPayload(deviceId, isVM), null);
                });
    }
}
