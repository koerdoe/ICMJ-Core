package dev.mcmod.zhenghuocore.core;

import java.util.Map;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

@IFMLLoadingPlugin.Name("ZhenghuoCore")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions({"dev.mcmod.zhenghuocore.core."})
public final class CoreLoadingPlugin implements IFMLLoadingPlugin {
    @Override public String[] getASMTransformerClass() { return new String[]{CompatibilityTransformer.class.getName()}; }
    @Override public String getModContainerClass() { return null; }
    @Override public String getSetupClass() { return null; }
    @Override public void injectData(Map<String, Object> data) {}
    @Override public String getAccessTransformerClass() { return null; }
}
