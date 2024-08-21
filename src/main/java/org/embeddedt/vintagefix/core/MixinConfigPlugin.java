package org.embeddedt.vintagefix.core;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.embeddedt.vintagefix.transformercache.TransformerCache;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MixinConfigPlugin implements IMixinConfigPlugin {

    private static final List<MixinConfigPlugin> mixinPlugins = new ArrayList<>();

    public static List<MixinConfigPlugin> getMixinPlugins() {
        return mixinPlugins;
    }

    private String mixinPackage;

    private static final ImmutableMap<String, Consumer<PotentialMixin>> MIXIN_PROCESSING_MAP = ImmutableMap.<String, Consumer<PotentialMixin>>builder()
        .put("Lorg/spongepowered/asm/mixin/Mixin;", p -> p.valid = true)
        .put("Lorg/embeddedt/vintagefix/annotation/ClientOnlyMixin;", p -> p.isClientOnly = true)
        .build();

    static class PotentialMixin {
        String className;
        boolean valid;
        boolean isClientOnly;
    }

    private PotentialMixin considerClass(String pathString) {
        try(InputStream stream = MixinConfigPlugin.class.getClassLoader().getResourceAsStream(pathString)) {
            if(stream == null)
                return null;
            ClassReader reader = new ClassReader(stream);
            ClassNode node = new ClassNode();
            reader.accept(node,  ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
            if(node.invisibleAnnotations == null)
                return null;
            PotentialMixin mixin = new PotentialMixin();
            mixin.className = node.name.replace('/', '.');
            for(AnnotationNode annotation : node.invisibleAnnotations) {
                Consumer<PotentialMixin> consumer = MIXIN_PROCESSING_MAP.get(annotation.desc);
                if(consumer != null)
                    consumer.accept(mixin);
            }
            if(mixin.valid)
                return mixin;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void onLoad(String s) {
        this.mixinPackage = s;
        mixinPlugins.add(this);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetName, String className) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> set, Set<String> set1) {
    }

    private static final List<String> OPTIFINE_DISABLED_PACKAGES = ImmutableList.of("textures", "bugfix.entity_disappearing", "invisible_subchunks");
    private static final List<String> VINTAGIUM_DISABLED_PACKAGES = ImmutableList.of("bugfix.entity_disappearing", "invisible_subchunks");

    public static boolean isMixinClassApplied(String name) {
        // texture optimization causes issues when OF is installed
        if(VintageFixCore.OPTIFINE && OPTIFINE_DISABLED_PACKAGES.stream().anyMatch(name::startsWith)) {
            return false;
        }
        if(VintageFixCore.VINTAGIUM && VINTAGIUM_DISABLED_PACKAGES.stream().anyMatch(name::startsWith)) {
            return false;
        }
        // property optimizations are redundant with Sponge installed
        return !name.startsWith("blockstates.Property") || !VintageFixCore.SPONGE;
    }

    /**
     * Resolves the base class root for a given class URL. This resolves either the JAR root, or the class file root.
     * In either case the return value of this + the class name will resolve back to the original class url, or to other
     * class urls for other classes.
     */
    public URL getBaseUrlForClassUrl(URL classUrl) {
        String string = classUrl.toString();
        if (classUrl.getProtocol().equals("jar")) {
            try {
                return new URL(string.substring(4).split("!")[0]);
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
        if (string.endsWith(".class")) {
            try {
                return new URL(string.replace("\\", "/")
                    .replace(getClass().getCanonicalName()
                        .replace(".", "/") + ".class", ""));
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
        return classUrl;
    }

    /**
     * Get the package that contains all the mixins. This value is set by mixin itself using {@link #onLoad}.
     */
    public String getMixinPackage() {
        return mixinPackage;
    }

    /**
     * Get the path inside the class root to the mixin package
     */
    public String getMixinBaseDir() {
        return mixinPackage.replace(".", "/");
    }

    /**
     * A list of all discovered mixins.
     */
    private List<String> mixins = null;

    /**
     * Try to add mixin class ot the mixins based on the filepath inside of the class root.
     * Removes the {@code .class} file suffix, as well as the base mixin package.
     * <p><b>This method cannot be called after mixin initialization.</p>
     *
     * @param className the name or path of a class to be registered as a mixin.
     */
    public void tryAddMixinClass(String className) {
        String norm = (className.endsWith(".class") ? className.substring(0, className.length() - ".class".length()) : className)
            .replace("\\", "/")
            .replace("/", ".");
        if (norm.startsWith(getMixinPackage() + ".") && !norm.endsWith(".") && className.endsWith(".class")) {
            String mixin = norm.substring(getMixinPackage().length() + 1);
            MixinEnvironment.Side side = MixinEnvironment.getCurrentEnvironment().getSide();
            if (isMixinClassApplied(mixin)) {
                PotentialMixin toConsider = considerClass(className);
                if (toConsider != null && (!toConsider.isClientOnly || side == MixinEnvironment.Side.CLIENT)) {
                    mixins.add(mixin);
                }
            }
        }
    }

    /**
     * Search through the JAR or class directory to find mixins contained in {@link #getMixinPackage()}
     */
    @Override
    public List<String> getMixins() {
        if (mixins != null) return mixins;
        MixinEnvironment.Phase phase = MixinEnvironment.getCurrentEnvironment().getPhase();
        if(phase != MixinEnvironment.Phase.DEFAULT) return null;
        if(Boolean.getBoolean("vintagefix.transformerCache")) {
            TransformerCache.instance.init();
        }
        System.out.println("Trying to discover mixins");
        mixins = new ArrayList<>();
        URL classUrl = getClass().getProtectionDomain().getCodeSource().getLocation();
        System.out.println("Found classes at " + classUrl);
        Path file;
        try {
            file = Paths.get(getBaseUrlForClassUrl(classUrl).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Base directory found at " + file);
        if (Files.isDirectory(file)) {
            walkDir(file);
        } else {
            walkJar(file);
        }

        System.out.println("Found mixins: " + mixins);

        return mixins;
    }

    /**
     * Search through directory for mixin classes based on {@link #getMixinBaseDir}.
     *
     * @param classRoot The root directory in which classes are stored for the default package.
     */
    private void walkDir(Path classRoot) {
        System.out.println("Trying to find mixins from directory");
        try (Stream<Path> classes = Files.walk(classRoot.resolve(getMixinBaseDir()))) {
            classes.map(it -> classRoot.relativize(it).toString())
                .forEach(this::tryAddMixinClass);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Read through a JAR file, trying to find all mixins inside.
     */
    private void walkJar(Path file) {
        System.out.println("Trying to find mixins from jar file");
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(file))) {
            ZipEntry next;
            while ((next = zis.getNextEntry()) != null) {
                tryAddMixinClass(next.getName());
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void preApply(String targetClassName, org.spongepowered.asm.lib.tree.ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, org.spongepowered.asm.lib.tree.ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }
}
