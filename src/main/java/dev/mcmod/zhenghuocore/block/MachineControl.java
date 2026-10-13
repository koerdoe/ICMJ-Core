package dev.mcmod.zhenghuocore.block;

public interface MachineControl {
    String status();
    default void beforeBreak() {}
}
