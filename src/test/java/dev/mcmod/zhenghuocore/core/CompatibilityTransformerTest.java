package dev.mcmod.zhenghuocore.core;

import java.io.*;
import java.util.zip.*;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.Assert.*;

public class CompatibilityTransformerTest {
    @Test public void realForgeLifecycleHooksPreserveBytecodeAndRunOnceAtTheirOwnLifecyclePoint() throws Exception {
        String forge = "build/fg_cache/net/minecraftforge/forge/1.12.2-14.23.5.2864_mapped_stable_39-1.12/forge-1.12.2-14.23.5.2864_mapped_stable_39-1.12.jar";
        try (ZipFile zip = new ZipFile(forge)) {
            for (String name : new String[]{"net.minecraft.world.World", "net.minecraft.tileentity.TileEntity"}) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (InputStream in = zip.getInputStream(zip.getEntry(name.replace('.', '/') + ".class"))) {
                    byte[] chunk = new byte[4096]; int n;
                    while ((n = in.read(chunk)) >= 0) out.write(chunk, 0, n);
                }
                ClassNode before = new ClassNode(), after = new ClassNode();
                new ClassReader(out.toByteArray()).accept(before, 0);
                new ClassReader(new CompatibilityTransformer().transform(name, name, out.toByteArray())).accept(after, 0);
                assertEquals(before.interfaces, after.interfaces);
                assertEquals(before.methods.size(), after.methods.size());
                int hooks = 0;
                for (MethodNode method : after.methods) {
                    new Analyzer<BasicValue>(new BasicVerifier()).analyze(after.name, method);
                    for (AbstractInsnNode insn : method.instructions.toArray()) if (insn instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode) insn;
                        if (!call.owner.equals("dev/mcmod/zhenghuocore/compat/CompatibilityEvents")) continue;
                        hooks++;
                        assertEquals(name.endsWith("World") ? "addTileEntity" : "invalidate", method.name);
                        assertEquals(name.endsWith("World") ? "tileLoaded" : "tileInvalidated", call.name);
                    }
                }
                assertEquals(1, hooks);
            }
        }
    }
    @Test public void realFluxEuBridgeUsesFeWithoutChangingNativeFeMethods() throws Exception {
        String name = "sonar.fluxnetworks.common.tileentity.energy.TileIC2Energy";
        try (ZipFile zip = new ZipFile("vendor/fluxnetworks-4.1.1.34-patched.jar")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream in = zip.getInputStream(zip.getEntry(name.replace('.', '/') + ".class"))) {
                byte[] chunk = new byte[4096]; int n;
                while ((n = in.read(chunk)) >= 0) bytes.write(chunk, 0, n);
            }
            ClassNode before = new ClassNode(), after = new ClassNode();
            new ClassReader(bytes.toByteArray()).accept(before, 0);
            new ClassReader(new CompatibilityTransformer().transform(name,name,bytes.toByteArray())).accept(after,0);
            assertEquals(before.interfaces,after.interfaces);
            assertEquals(before.methods.size(),after.methods.size());
            int hooks = 0;
            for (MethodNode method : after.methods) {
                new Analyzer<BasicValue>(new BasicVerifier()).analyze(after.name,method);
                for (AbstractInsnNode insn : method.instructions.toArray()) if (insn instanceof MethodInsnNode
                        && ((MethodInsnNode)insn).owner.equals("dev/mcmod/zhenghuocore/compat/FluxCompatibility")) hooks++;
            }
            assertEquals(2,hooks);
        }
    }
    @Test public void realBotaniaReceiverConsumersRetainValidBytecodeAndNativeMethods() throws Exception {
        String[] classes = {"vazkii.botania.common.block.tile.mana.TileSpreader", "vazkii.botania.common.entity.EntityManaBurst",
                "vazkii.botania.common.entity.EntitySpark", "vazkii.botania.common.item.ItemSpark"};
        try (ZipFile zip = new ZipFile("vendor/botania-1.10-364.4.jar")) {
            for (String name : classes) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream in = zip.getInputStream(zip.getEntry(name.replace('.', '/') + ".class"))) {
                    byte[] chunk = new byte[4096]; int n;
                    while ((n = in.read(chunk)) >= 0) bytes.write(chunk, 0, n);
                }
                ClassNode before = new ClassNode(), after = new ClassNode();
                new ClassReader(bytes.toByteArray()).accept(before, 0);
                new ClassReader(new CompatibilityTransformer().transform(name, name, bytes.toByteArray())).accept(after, 0);
                assertEquals(before.interfaces, after.interfaces);
                assertEquals(before.methods.size(), after.methods.size());
                for (MethodNode method : after.methods) new Analyzer<BasicValue>(new BasicVerifier()).analyze(after.name, method);
            }
        }
    }
    @Test public void realBuildCraftPatchHasValidStackAndPreservesMjMethods() throws Exception {
        byte[] bytes;
        try (ZipFile zip = new ZipFile("vendor/buildcraft-8.0.1-robots.1.jar"); InputStream in = zip.getInputStream(zip.getEntry("buildcraft/api/mj/MjCapabilityHelper.class"))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] chunk = new byte[4096]; int n;
            while ((n = in.read(chunk)) >= 0) out.write(chunk, 0, n); bytes = out.toByteArray();
        }
        String name = "buildcraft.api.mj.MjCapabilityHelper";
        ClassNode before = new ClassNode(), after = new ClassNode(); new ClassReader(bytes).accept(before, 0);
        new ClassReader(new CompatibilityTransformer().transform(name, name, bytes)).accept(after, 0);
        assertEquals(before.methods.size(), after.methods.size());
        for (MethodNode method : after.methods) new Analyzer<BasicValue>(new BasicVerifier()).analyze(after.name, method);
        assertArrayEquals(bytes, new CompatibilityTransformer().transform("other.Class", "other.Class", bytes));
    }
}
