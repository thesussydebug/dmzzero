package com.dmzzero.beam;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.beam.BeamMath.V;
import com.dmzzero.smash.Smashes;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.server.events.players.combat.KnockbackHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// firing a beam pushes you back the way it didn't go: the beam jump, and the tournament ring-out save
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class BeamRecoil {

    private BeamRecoil() {
    }

    // dmz posts this on the server once a charged ki attack has really gone off
    @SubscribeEvent
    public static void onKiFire(DMZEvent.KiAttackFireEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer p) || !ZeroConfig.SERVER.beamRecoil.get()) return;
        KiAttackData data = event.getKiAttack();
        if (data == null || data.getKiType() == null) return;
        recoil(p, data.getKiType().name(), data.getActualSize(), KiAttackData.getDefaultSizeForType(data.getKiType()), event.getChargeMultiplier());
    }

    public static V recoil(ServerPlayer p, String kiType, float size, float defaultSize, float charge) {
        double strength = RecoilMath.strength(kiType, size, defaultSize, charge, ZeroConfig.SERVER.recoilStrength.get());
        Vec3 look = p.getLookAngle();
        // dmz counts you as flying only off the ground, so standing is standing either way
        V push = RecoilMath.push(new V(look.x, look.y, look.z), strength, p.onGround());
        if (push.length() < 0.02) return push;
        Vec3 motion = new Vec3(push.x(), push.y(), push.z());
        // dmz's own knockback keeps a flying player's flight in step; the recoil itself never counts as being sent flying by a hit
        Smashes.quietly(() -> KnockbackHelper.apply(p, motion));
        p.hurtMarked = true;
        if (push.y() > 0) p.resetFallDistance();
        return push;
    }
}
