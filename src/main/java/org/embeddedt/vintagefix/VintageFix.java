package org.embeddedt.vintagefix;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.vintagefix.core.MixinConfigPlugin;
import org.embeddedt.vintagefix.dynamicresources.SafeModelBakeWrapper;

import java.io.File;
import java.util.concurrent.ForkJoinPool;

@Mod(modid = "vintagefix", name = "VintageFix", version = "@VER@", dependencies = "after:foamfix@[INCOMPATIBLE];" +
    "after:loliasm@[" + VintageFix.REQUIRED_XASM_VERSION + ",);" +
    "after:normalasm@[" + VintageFix.REQUIRED_XASM_VERSION + ",)", acceptableRemoteVersions = "*")
public class VintageFix {

    public static final Logger LOGGER = LogManager.getLogger("VintageFix");
    public static final String REQUIRED_XASM_VERSION = "5.10";

    public static final File MY_DIR = new File(Launch.minecraftHome, "vintagefix");
    public static final File OUT_DIR = new File(MY_DIR, "out");
    public static final File CACHE_DIR = new File(MY_DIR, "transformerCache");

    public static final ForkJoinPool WORKER_POOL = new ForkJoinPool();

    @Mod.EventHandler
    @SuppressWarnings("unused")
    public void init(FMLConstructionEvent ev) {
        if(FMLCommonHandler.instance().getSide() == Side.CLIENT) {
            MinecraftForge.EVENT_BUS.register(new VintageFixClient());
        }
    }

    @Mod.EventHandler
    public void preinit(FMLPreInitializationEvent ev) {
        if(FMLCommonHandler.instance().getSide() == Side.CLIENT && MixinConfigPlugin.isMixinClassApplied("mixin.dynamic_resources.SafeModelBakeWrapperMixin")) {
            SafeModelBakeWrapper.setup();
        }
    }
}
