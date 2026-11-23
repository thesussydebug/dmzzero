package com.dmzzero.network;

import com.dmzzero.chamber.ClientChamber;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// how deep in the time chamber you are, once a second; zone -1 means you've left
public record ChamberStateS2C(int zone, float temperature, float tp, boolean winded) {

    public static ChamberStateS2C left() {
        return new ChamberStateS2C(-1, 0f, 1f, false);
    }

    public static void encode(ChamberStateS2C msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.zone);
        buf.writeFloat(msg.temperature);
        buf.writeFloat(msg.tp);
        buf.writeBoolean(msg.winded);
    }

    public static ChamberStateS2C decode(FriendlyByteBuf buf) {
        return new ChamberStateS2C(buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
    }

    public static void handle(ChamberStateS2C msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientChamber.set(msg.zone, msg.temperature, msg.tp, msg.winded));
        ctx.get().setPacketHandled(true);
    }
}
