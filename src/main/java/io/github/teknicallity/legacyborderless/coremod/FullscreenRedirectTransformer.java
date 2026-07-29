package io.github.teknicallity.legacyborderless.coremod;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Rewrites {@code net.minecraft.client.Minecraft} so that fullscreen becomes borderless. Three edits, all matched
 * on stable anchors (LWJGL owner/name/descriptor and the class's {@code transformedName}), so they work identically
 * in a dev workspace (voldeloom names) and in production (FML SRG names):
 *
 * <ol>
 *     <li><b>Neuter {@code toggleFullscreen()}</b> — replace its whole body with {@code return}. This is the method
 *         the Video Settings "Fullscreen" option calls; it does an intrusive display-mode dance that fights the
 *         borderless engine, and it is no longer needed (F11 is the mod's key binding). It is identified as the one
 *         method that calls {@code Display.setVSyncEnabled} (unique in 1.6.4's Minecraft).</li>
 *     <li><b>Redirect {@code Display.setFullscreen(boolean)}</b> — the remaining call (in {@code startGame()}, for
 *         launch-in-fullscreen) is rewritten to {@code BorderlessEngine.setFullscreenFromGame(boolean)} so it too
 *         becomes borderless rather than LWJGL 2 exclusive fullscreen.</li>
 *     <li><b>Disable the hard-coded F11</b> — {@code runTick()} has a non-rebindable
 *         {@code if (Keyboard.getEventKey() == 87) toggleFullscreen()}; the compared key code is changed so the
 *         branch is never taken, freeing F11 for the mod's rebindable key binding.</li>
 * </ol>
 *
 * Edits 2 and 3 do not change stack shapes; edit 1 rebuilds the method with fresh max/locals. {@link ClassWriter}
 * is used with flags {@code 0}.
 */
public class FullscreenRedirectTransformer implements IClassTransformer {

    private static final String TARGET_CLASS = "net.minecraft.client.Minecraft";

    private static final String DISPLAY_OWNER = "org/lwjgl/opengl/Display";
    private static final String SET_FULLSCREEN = "setFullscreen";
    private static final String SET_FULLSCREEN_DESC = "(Z)V";
    private static final String SET_VSYNC = "setVSyncEnabled";
    private static final String SET_VSYNC_DESC = "(Z)V";

    private static final String HOOK_OWNER = "io/github/teknicallity/legacyborderless/engine/BorderlessEngine";
    private static final String HOOK_NAME = "setFullscreenFromGame";

    private static final String KEYBOARD_OWNER = "org/lwjgl/input/Keyboard";
    private static final String GET_EVENT_KEY = "getEventKey";
    private static final String GET_EVENT_KEY_DESC = "()I";
    private static final int KEY_F11 = 87;
    private static final int NEVER_A_KEY = -1;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !TARGET_CLASS.equals(transformedName)) {
            return basicClass;
        }

        ClassReader reader = new ClassReader(basicClass);
        ClassNode node = new ClassNode();
        reader.accept(node, 0);

        boolean gutted = gutToggleFullscreen(node);
        int redirected = redirectSetFullscreen(node);
        int disabledF11 = disableHardCodedF11(node);

        if (!gutted && redirected == 0 && disabledF11 == 0) {
            System.out.println("[LegacyBorderless] WARN: nothing patched in Minecraft; F11 and the Video Settings "
                    + "toggle will behave as vanilla (the key binding still calls the engine).");
            return basicClass;
        }

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        System.out.println("[LegacyBorderless] Patched Minecraft: neutered toggleFullscreen=" + gutted
                + ", redirected " + redirected + " Display.setFullscreen call(s), disabled " + disabledF11
                + " hard-coded F11 handler(s).");
        return writer.toByteArray();
    }

    /** Replace toggleFullscreen()'s body with a bare {@code return}. */
    private static boolean gutToggleFullscreen(ClassNode node) {
        for (Object methodObj : node.methods) {
            MethodNode method = (MethodNode) methodObj;
            if (!methodCalls(method, DISPLAY_OWNER, SET_VSYNC, SET_VSYNC_DESC)) {
                continue;
            }
            method.instructions.clear();
            method.instructions.add(new InsnNode(Opcodes.RETURN));
            if (method.tryCatchBlocks != null) {
                method.tryCatchBlocks.clear();
            }
            if (method.localVariables != null) {
                method.localVariables.clear();
            }
            method.maxStack = 0;
            method.maxLocals = Type.getArgumentsAndReturnSizes(method.desc) >> 2; // includes 'this'
            return true;
        }
        return false;
    }

    private static int redirectSetFullscreen(ClassNode node) {
        int count = 0;
        for (Object methodObj : node.methods) {
            MethodNode method = (MethodNode) methodObj;
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn.getOpcode() != Opcodes.INVOKESTATIC || !(insn instanceof MethodInsnNode)) {
                    continue;
                }
                MethodInsnNode call = (MethodInsnNode) insn;
                if (DISPLAY_OWNER.equals(call.owner)
                        && SET_FULLSCREEN.equals(call.name)
                        && SET_FULLSCREEN_DESC.equals(call.desc)) {
                    call.owner = HOOK_OWNER;
                    call.name = HOOK_NAME;
                    count++;
                }
            }
        }
        return count;
    }

    private static int disableHardCodedF11(ClassNode node) {
        int count = 0;
        for (Object methodObj : node.methods) {
            MethodNode method = (MethodNode) methodObj;
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn.getOpcode() != Opcodes.INVOKESTATIC || !(insn instanceof MethodInsnNode)) {
                    continue;
                }
                MethodInsnNode call = (MethodInsnNode) insn;
                if (!KEYBOARD_OWNER.equals(call.owner)
                        || !GET_EVENT_KEY.equals(call.name)
                        || !GET_EVENT_KEY_DESC.equals(call.desc)) {
                    continue;
                }
                AbstractInsnNode next = nextRealInsn(insn);
                if (next instanceof IntInsnNode) {
                    IntInsnNode push = (IntInsnNode) next;
                    if ((push.getOpcode() == Opcodes.BIPUSH || push.getOpcode() == Opcodes.SIPUSH)
                            && push.operand == KEY_F11) {
                        push.operand = NEVER_A_KEY;
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static boolean methodCalls(MethodNode method, String owner, String name, String desc) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if (owner.equals(call.owner) && name.equals(call.name) && desc.equals(call.desc)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Next actual bytecode instruction, skipping labels, line numbers and frames. */
    private static AbstractInsnNode nextRealInsn(AbstractInsnNode insn) {
        AbstractInsnNode n = insn.getNext();
        while (n != null && (n instanceof LabelNode || n instanceof LineNumberNode || n instanceof FrameNode)) {
            n = n.getNext();
        }
        return n;
    }
}
