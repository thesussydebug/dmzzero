package com.dmzzero.status;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Counts the mixin hooks that found their target. Mixin skips a hook with require = 0 that finds nothing, without an
 * error, so a DragonMineZ update can switch part of a feature off unnoticed. Each such miss is logged when its class
 * loads, and {@link HookReport} logs the count once the server has started. Loaded by mixin before the game, so it
 * touches no Minecraft or Forge class.
 */
public final class HookPlugin implements IMixinConfigPlugin {
    private static final Logger LOG = LoggerFactory.getLogger("dmzzero");
    private static final Set<String> INJECTORS = Set.of(
            "Lorg/spongepowered/asm/mixin/injection/Inject;",
            "Lorg/spongepowered/asm/mixin/injection/Redirect;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;");
    private static final List<String> MISSED = new ArrayList<>();
    private static int hooks;
    private static int bound;
    private static int applied;

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    // a hook that bound leaves a call to its handler, which mixin copies into the target under a prefixed name
    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        int found = 0, ok = 0;
        List<String> missed = new ArrayList<>();
        for (MethodNode handler : mixinInfo.getClassNode(0).methods) {
            if (!injector(handler)) continue;
            found++;
            if (called(targetClass, handler)) {
                ok++;
            } else {
                missed.add(mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1) + "." + handler.name);
            }
        }
        synchronized (HookPlugin.class) {
            hooks += found;
            bound += ok;
            applied++;
            MISSED.addAll(missed);
        }
        for (String m : missed) {
            LOG.warn("DMZ Zero's hook {} found nothing in {}, so that part of the feature does nothing. "
                    + "DragonMineZ or another mod may have changed that code.", m, targetClassName);
        }
    }

    private static boolean injector(MethodNode m) {
        if (m.visibleAnnotations == null) return false;
        for (AnnotationNode a : m.visibleAnnotations) {
            if (INJECTORS.contains(a.desc)) return true;
        }
        return false;
    }

    private static boolean called(ClassNode target, MethodNode handler) {
        Set<String> names = new HashSet<>();
        for (MethodNode m : target.methods) {
            if (m.desc.equals(handler.desc) && (m.name.equals(handler.name) || m.name.endsWith("$" + handler.name))) names.add(m.name);
        }
        if (names.isEmpty()) return false;
        for (MethodNode m : target.methods) {
            if (m.instructions == null || names.contains(m.name) && m.desc.equals(handler.desc)) continue;
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof MethodInsnNode call && call.owner.equals(target.name) && names.contains(call.name)
                        && call.desc.equals(handler.desc)) return true;
            }
        }
        return false;
    }

    /** Hooks in the mixins applied so far. */
    public static synchronized int hooks() {
        return hooks;
    }

    /** Of those, the ones that found their target. */
    public static synchronized int bound() {
        return bound;
    }

    /** Mixins applied so far. One that targets a class not loaded yet, or a mod that isn't installed, isn't counted. */
    public static synchronized int applied() {
        return applied;
    }

    /** The hooks that found nothing, as Mixin.handler. */
    public static synchronized List<String> missed() {
        return List.copyOf(MISSED);
    }
}
