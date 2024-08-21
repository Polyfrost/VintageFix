package org.embeddedt.vintagefix.core;

import com.google.common.cache.CacheBuilder;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.embeddedt.vintagefix.util.DummyList;
import org.spongepowered.asm.mixin.injection.struct.InjectorGroupInfo;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@IFMLLoadingPlugin.Name("VintageFix")
public class VintageFixCore implements IFMLLoadingPlugin {
    public static boolean OPTIFINE;
    public static boolean VINTAGIUM;
    public static boolean SPONGE;
    private static final int MAXIMUM_RESOURCE_CACHE_SIZE = 20 * 1024 * 1024;

    public VintageFixCore() {
        // Force-disable squashBakedQuads in any known *ASM mods
        for (String clz : new String[] { "zone.rong.loliasm.core.LoliTransformer", "mirror.normalasm.core.NormalTransformer" }) {
            try {
                Class<?> transformerClass = Class.forName(clz);
                Field field = transformerClass.getDeclaredField("squashBakedQuads");
                field.setAccessible(true);
                field.setBoolean(null, false);
            } catch(ReflectiveOperationException ignored) {}
        }
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch(ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[] { "org.embeddedt.vintagefix.transformer.ASMModParserTransformer" };
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Nullable
    @Override
    public String getSetupClass() {
        replaceLaunchCLCaches();
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void replaceLaunchCLCaches() {
        LaunchClassLoader cl;
        if(VintageFixCore.class.getClassLoader() instanceof LaunchClassLoader) {
            cl = (LaunchClassLoader)VintageFixCore.class.getClassLoader();
        } else {
            return;
        }
        try {
            // Disable packageManifests cache
            ObfuscationReflectionHelper.findField(LaunchClassLoader.class, "packageManifests").set(cl, new ConcurrentHashMap() {
                @Override
                public Object put(Object key, Object value) {
                    return null;
                }
            });
            Field resourceCacheField = ObfuscationReflectionHelper.findField(LaunchClassLoader.class, "resourceCache");
            Map resourceCacheMap = (Map)resourceCacheField.get(cl);
            if(resourceCacheMap instanceof ConcurrentHashMap) {
                // Not replaced by any other optimization mod, let's take it over ourselves
                resourceCacheField.set(cl, CacheBuilder.newBuilder()
                    .maximumWeight(MAXIMUM_RESOURCE_CACHE_SIZE) // roughly cap maximum class loader cache size
                    .<String, byte[]>weigher((k, v) -> {
                        return k.length() + v.length;
                    })
                    .expireAfterAccess(1, TimeUnit.MINUTES)
                    .build()
                    .asMap());
            }
        } catch(Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    private static boolean mixinFixApplied = false;

    private static void applyMixinFix() {
        /* https://github.com/FabricMC/Mixin/pull/99 */
        try {
            Field groupMembersField = InjectorGroupInfo.class.getDeclaredField("members");
            groupMembersField.setAccessible(true);
            Field noGroupField = InjectorGroupInfo.Map.class.getDeclaredField("NO_GROUP");
            noGroupField.setAccessible(true);
            InjectorGroupInfo noGroup = (InjectorGroupInfo)noGroupField.get(null);
            groupMembersField.set(noGroup, new DummyList<>());
        } catch(RuntimeException | ReflectiveOperationException ignored) {
        }
    }

    //@Override
    //public List<String> getMixinConfigs() {
    //    if(!mixinFixApplied) {
    //        applyMixinFix();
    //        mixinFixApplied = true;
    //    }
    //    return ImmutableList.of("mixins.vintagefix.init.json", "mixins.vintagefix.json");
    //}
}
