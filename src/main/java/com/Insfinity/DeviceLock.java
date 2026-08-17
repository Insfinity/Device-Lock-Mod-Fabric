package com.Insfinity;

import com.Insfinity.networking.DeviceInfoPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.util.Identifier;

public class DeviceLock implements ModInitializer {
    public static final String MOD_ID = "device-lock";

    /** Login-phase query identifier (server requests, client responds via PacketByteBuf). */
    public static final Identifier DEVICE_INFO_LOGIN_ID = Identifier.of(MOD_ID, "device_info");

    /** Play-phase packet identifier (client -> server CustomPayload mapping report). */
    public static final Identifier DEVICE_INFO_PLAY_ID = Identifier.of(MOD_ID, "device_info_play");

    @Override
    public void onInitialize() {
        // Register the play-phase custom payload codec (client -> server).
        // Login phase uses the legacy PacketByteBuf API and needs no registration.
        PayloadTypeRegistry.playC2S().register(DeviceInfoPayload.PLAY_ID, DeviceInfoPayload.CODEC);
    }
}
