package com.Insfinity.networking;

import com.Insfinity.DeviceLock;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Play-phase CustomPayload carrying the client's device ID and VM status.
 *
 * The login phase uses the legacy PacketByteBuf API directly (see
 * DeviceLockClient / DeviceLockServer), so this payload is only used
 * during the play phase to report the UUID <-> deviceId mapping.
 */
public record DeviceInfoPayload(String deviceId, boolean isVM) implements CustomPayload {

    /** Wraps the login-phase Identifier for reuse; only .id() is used in login code. */
    public static final Id<DeviceInfoPayload> LOGIN_ID =
            new Id<>(DeviceLock.DEVICE_INFO_LOGIN_ID);

    /** Actual CustomPayload Id for the play-phase packet. */
    public static final Id<DeviceInfoPayload> PLAY_ID =
            new Id<>(DeviceLock.DEVICE_INFO_PLAY_ID);

    /**
     * Codec backed by PacketByteBuf. Play phase requires RegistryByteBuf,
     * which extends PacketByteBuf, so this codec satisfies
     * PacketCodec<? super RegistryByteBuf, DeviceInfoPayload>.
     */
    public static final PacketCodec<PacketByteBuf, DeviceInfoPayload> CODEC =
            CustomPayload.codecOf(
                    (payload, buf) -> {
                        buf.writeString(payload.deviceId());
                        buf.writeBoolean(payload.isVM());
                    },
                    buf -> new DeviceInfoPayload(buf.readString(), buf.readBoolean())
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return PLAY_ID;
    }
}
