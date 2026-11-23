package com.dmzzero.magic;

import com.dmzzero.DMZZero;
import com.dragonminez.common.racial.RacialAbility;
import com.dragonminez.common.racial.RacialContext;
import com.dragonminez.common.racial.RacialRegistry;
import com.dragonminez.common.racial.impl.GlindDivinity;
import com.dragonminez.common.stats.StatsData;

import java.util.Optional;

// dmz's glind ability plus an active action, magic; the passives stay dmz's own. registered on both sides, since dmz's
// radial only offers the racial action when the client's copy of the ability has one
public final class GlindMagic extends GlindDivinity {

    @Override
    public boolean hasActiveAction() {
        return Magic.on();
    }

    @Override
    public boolean hasActiveAction(StatsData data) {
        return Magic.on() && data != null && isActive(data);
    }

    // dmz's client freezes your movement while the action charge is on, so dmz's charge never starts a spell and dmz
    // turns the flag off; dmz zero's client sends its own packet on the key press instead (MagicKeys)
    @Override
    public boolean canActivate(RacialContext ctx) {
        return false;
    }

    /** Takes DMZ's Glind ability's place, unless another mod has already replaced it. */
    public static boolean install() {
        Optional<RacialAbility> now = RacialRegistry.get(ID);
        if (now.isPresent() && now.get() instanceof GlindMagic) return true;
        if (now.isPresent() && now.get().getClass() != GlindDivinity.class) {
            DMZZero.LOG.warn("DMZ Zero: Glind's racial ability is already {}, so Glind magic stays off", now.get().getClass().getName());
            return false;
        }
        RacialRegistry.register(new GlindMagic());
        return true;
    }

    public static boolean installed() {
        return RacialRegistry.get(ID).filter(a -> a instanceof GlindMagic).isPresent();
    }
}
