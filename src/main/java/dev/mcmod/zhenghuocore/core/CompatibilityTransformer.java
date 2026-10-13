package dev.mcmod.zhenghuocore.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Let BC tiles use the attached bidirectional FE adapter instead of the built-in incomplete wrapper. */
public final class CompatibilityTransformer implements IClassTransformer, Opcodes {
    @Override public byte[] transform(String name, String transformed, byte[] data) {
        if (data != null && ("net.minecraft.world.World".equals(transformed)
                || "net.minecraft.tileentity.TileEntity".equals(transformed))) return lifecycle(transformed, data);
        if (data != null && "sonar.fluxnetworks.common.tileentity.energy.TileIC2Energy".equals(transformed)) return fluxEu(data);
        if (data != null && ("vazkii.botania.common.block.tile.mana.TileSpreader".equals(transformed)
                || "vazkii.botania.common.entity.EntityManaBurst".equals(transformed)
                || "vazkii.botania.common.entity.EntitySpark".equals(transformed)
                || "vazkii.botania.common.item.ItemSpark".equals(transformed))) return manaConsumers(transformed, data);
        boolean helper = "buildcraft.api.mj.MjCapabilityHelper".equals(transformed);
        boolean engine = "buildcraft.lib.engine.TileEngineBase_BC8".equals(transformed);
        if (data == null || !helper && !engine) return data;
        ClassNode node = new ClassNode(); new ClassReader(data).accept(node, 0);
        int changes = 0;
        for (MethodNode method : node.methods) if (method.name.equals("getCapability") && method.desc.equals("(Lnet/minecraftforge/common/capabilities/Capability;Lnet/minecraft/util/EnumFacing;)Ljava/lang/Object;")) {
            InsnList code = new InsnList(); LabelNode nativePath = new LabelNode();
            code.add(new VarInsnNode(ALOAD, 1));
            code.add(new FieldInsnNode(GETSTATIC, "net/minecraftforge/energy/CapabilityEnergy", "ENERGY", "Lnet/minecraftforge/common/capabilities/Capability;"));
            code.add(new JumpInsnNode(IF_ACMPNE, nativePath));
            if (helper) code.add(new InsnNode(ACONST_NULL));
            else {
                code.add(new VarInsnNode(ALOAD, 0)); code.add(new VarInsnNode(ALOAD, 1)); code.add(new VarInsnNode(ALOAD, 2));
                code.add(new MethodInsnNode(INVOKESPECIAL, "buildcraft/lib/tile/TileBC_Neptune", "getCapability", method.desc, false));
            }
            code.add(new InsnNode(ARETURN)); code.add(nativePath);
            code.add(new FrameNode(F_SAME, 0, null, 0, null));
            method.instructions.insert(code); changes++;
        }
        if (changes != 1) throw new IllegalStateException("Unsupported BuildCraft capability layout in " + transformed + ": " + changes);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
    private byte[] lifecycle(String name, byte[] data) {
        ClassNode node = new ClassNode(); new ClassReader(data).accept(node, 0);
        String hooks = "dev/mcmod/zhenghuocore/compat/CompatibilityEvents";
        int changes = 0;
        for (MethodNode method : node.methods) {
            if (name.endsWith(".World") && (method.name.equals("addTileEntity") || method.name.equals("func_175700_a"))
                    && method.desc.equals("(Lnet/minecraft/tileentity/TileEntity;)Z")) {
                for (AbstractInsnNode instruction : method.instructions.toArray()) if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (call.name.equals("onLoad") && call.desc.equals("()V")) {
                        InsnList code = new InsnList(); code.add(new VarInsnNode(ALOAD, 1));
                        code.add(new MethodInsnNode(INVOKESTATIC, hooks, "tileLoaded", "(Lnet/minecraft/tileentity/TileEntity;)V", false));
                        method.instructions.insert(call, code); changes++;
                    }
                }
            } else if (name.endsWith(".TileEntity") && (method.name.equals("invalidate") || method.name.equals("func_145843_s"))
                    && method.desc.equals("()V")) {
                for (AbstractInsnNode instruction : method.instructions.toArray()) if (instruction.getOpcode() == RETURN) {
                    InsnList code = new InsnList(); code.add(new VarInsnNode(ALOAD, 0));
                    code.add(new MethodInsnNode(INVOKESTATIC, hooks, "tileInvalidated", "(Lnet/minecraft/tileentity/TileEntity;)V", false));
                    method.instructions.insertBefore(instruction, code); changes++;
                }
            }
        }
        if (changes != 1) throw new IllegalStateException("Unsupported Forge lifecycle layout in " + name + ": " + changes);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
    private byte[] fluxEu(byte[] data) {
        ClassNode node = new ClassNode(); new ClassReader(data).accept(node, 0);
        int changed = 0;
        for (MethodNode method : node.methods) {
            boolean demand = method.name.equals("getDemandedEnergy") && method.desc.equals("()D");
            boolean inject = method.name.equals("injectEnergy") && method.desc.equals("(Lnet/minecraft/util/EnumFacing;DD)D");
            if (!demand && !inject) continue;
            method.instructions.clear(); method.tryCatchBlocks.clear();
            if (method.localVariables != null) method.localVariables.clear();
            if (method.visibleLocalVariableAnnotations != null) method.visibleLocalVariableAnnotations.clear();
            if (method.invisibleLocalVariableAnnotations != null) method.invisibleLocalVariableAnnotations.clear();
            method.instructions.add(new VarInsnNode(ALOAD, 0));
            if (inject) {
                method.instructions.add(new VarInsnNode(ALOAD, 1));
                method.instructions.add(new VarInsnNode(DLOAD, 2));
                method.instructions.add(new VarInsnNode(DLOAD, 4));
            }
            method.instructions.add(new MethodInsnNode(INVOKESTATIC,"dev/mcmod/zhenghuocore/compat/FluxCompatibility",
                    demand ? "demandedEnergy" : "injectEnergy", demand ? "(Lnet/minecraft/tileentity/TileEntity;)D"
                    : "(Lnet/minecraft/tileentity/TileEntity;Lnet/minecraft/util/EnumFacing;DD)D",false));
            method.instructions.add(new InsnNode(DRETURN)); changed++;
        }
        if (changed != 2) throw new IllegalStateException("Unsupported Flux Networks EU bridge layout: " + changed);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
    private byte[] manaConsumers(String name, byte[] data) {
        ClassNode node = new ClassNode(); new ClassReader(data).accept(node, 0);
        String hooks = "dev/mcmod/zhenghuocore/compat/ManaReceiverHooks";
        String burst = "vazkii/botania/common/entity/EntityManaBurst";
        String receiver = "vazkii/botania/api/mana/IManaReceiver";
        int changes = 0, burstReceive = 0, burstDeath = 0, burstLimit = 0, sparkReceive = 0, burstCollision = 0, sparkTick = 0;
        for (MethodNode method : node.methods) for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (name.endsWith(".EntityManaBurst") && (method.name.equals("onImpact") || method.name.equals("func_70184_a"))
                    && instruction instanceof FieldInsnNode && instruction.getOpcode()==PUTFIELD) {
                FieldInsnNode field=(FieldInsnNode)instruction;
                if(field.owner.equals(burst) && field.name.equals("collidedTile") && field.desc.equals("Lnet/minecraft/tileentity/TileEntity;")) {
                    InsnList code=new InsnList(); code.add(new VarInsnNode(ALOAD,0)); code.add(new VarInsnNode(ALOAD,1));
                    code.add(new MethodInsnNode(INVOKESTATIC,hooks,"collisionView","(Lnet/minecraft/tileentity/TileEntity;L"+burst+";Lnet/minecraft/util/math/RayTraceResult;)Lnet/minecraft/tileentity/TileEntity;",false));
                    method.instructions.insertBefore(field,code); burstCollision++;
                }
            }
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                if (name.endsWith(".EntitySpark") && (method.name.equals("onUpdate") || method.name.equals("func_70071_h_"))
                        && call.name.equals("getAttachedTile") && sparkTick == 0) {
                    InsnList code = new InsnList(); code.add(new VarInsnNode(ALOAD, 0));
                    code.add(new MethodInsnNode(INVOKESTATIC, hooks, "tickSpark",
                            "(Lvazkii/botania/api/mana/spark/ISparkAttachable;Lvazkii/botania/common/entity/EntitySpark;)Lvazkii/botania/api/mana/spark/ISparkAttachable;", false));
                    method.instructions.insert(call, code); sparkTick++;
                } else if (name.endsWith(".TileSpreader") && method.name.equals("tryShootBurst") && call.name.equals("getBurst")
                        && call.desc.equals("(Z)L"+burst+";")) {
                    InsnList code=new InsnList(); code.add(new VarInsnNode(ALOAD,0));
                    code.add(new FieldInsnNode(GETFIELD,node.name,"receiver","L"+receiver+";"));
                    code.add(new MethodInsnNode(INVOKESTATIC,hooks,"limitBurst","(L"+burst+";L"+receiver+";)L"+burst+";",false));
                    method.instructions.insert(call,code); burstLimit++;
                } else if (name.endsWith(".EntityManaBurst") && method.name.equals("onRecieverImpact")
                        && call.name.equals("recieveMana") && call.desc.equals("(I)V")) {
                    method.instructions.insertBefore(call,new VarInsnNode(ALOAD,0));
                    method.instructions.set(call,new MethodInsnNode(INVOKESTATIC,hooks,"receiveBurst","(L"+receiver+";IL"+burst+";)V",false));
                    burstReceive++;
                } else if (name.endsWith(".EntityManaBurst") && (method.name.equals("onImpact") || method.name.equals("func_70184_a"))
                        && (call.name.equals("setDead") || call.name.equals("func_70106_y")) && call.desc.equals("()V")) {
                    method.instructions.insertBefore(call,new VarInsnNode(ALOAD,1));
                    method.instructions.set(call,new MethodInsnNode(INVOKESTATIC,hooks,"finishBurstImpact","(L"+burst+";Lnet/minecraft/util/math/RayTraceResult;)V",false));
                    burstDeath++;
                } else if (name.endsWith(".EntitySpark") && call.name.equals("recieveMana") && call.desc.equals("(I)V")) {
                    AbstractInsnNode amount=call.getPrevious();
                    while(amount!=null && amount.getOpcode()<0) amount=amount.getPrevious();
                    // Positive receiver transfer has an ILOAD; the native source debits have INEG.
                    if(amount instanceof VarInsnNode && amount.getOpcode()==ILOAD) {
                        MethodInsnNode actual=new MethodInsnNode(INVOKESTATIC,hooks,"receiveSpark","(L"+receiver+";I)I",false);
                        method.instructions.set(call,actual);
                        method.instructions.insert(actual,new VarInsnNode(ISTORE,((VarInsnNode)amount).var)); sparkReceive++;
                    }
                }
            }
            if (!(instruction instanceof TypeInsnNode)) continue;
            TypeInsnNode type = (TypeInsnNode) instruction;
            boolean mana = type.desc.equals("vazkii/botania/api/mana/IManaReceiver");
            boolean spark = type.desc.equals("vazkii/botania/api/mana/spark/ISparkAttachable");
            if (!mana && !spark) continue;
            if (type.getOpcode() == INSTANCEOF) {
                method.instructions.set(type, new MethodInsnNode(INVOKESTATIC, hooks,
                        mana ? "isReceiver" : "isSparkAttachable", "(Ljava/lang/Object;)Z", false));
                changes++;
            } else if (type.getOpcode() == CHECKCAST) {
                boolean impact = mana && (method.name.equals("onImpact") || method.name.equals("func_70184_a"));
                if (impact) method.instructions.insertBefore(type, new VarInsnNode(ALOAD, 1));
                method.instructions.set(type, new MethodInsnNode(INVOKESTATIC, hooks,
                        impact ? "impactReceiver" : mana ? "receiver" : "sparkAttachable",
                        "(Ljava/lang/Object;" + (impact ? "Lnet/minecraft/util/math/RayTraceResult;" : "") + ")L" + type.desc + ";", false));
                changes++;
            }
        }
        if (changes == 0) throw new IllegalStateException("Unsupported Botania receiver layout in " + name);
        if (name.endsWith(".TileSpreader") && burstLimit!=1 || name.endsWith(".EntityManaBurst") && (burstReceive!=1 || burstDeath!=1 || burstCollision!=1)
                || name.endsWith(".EntitySpark") && (sparkReceive!=1 || sparkTick!=1)) throw new IllegalStateException("Unsupported Botania transfer layout in "+name);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
}
