package org.embeddedt.vintagefix.mixin.bugfix.exit_freeze;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(IntegratedServer.class)
public class IntegratedServerMixin {
    /**
     * Sometimes the server thread is already gone at this point, try to prevent a freeze.
     */
    @Redirect(method = "initiateShutdown", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/integrated/IntegratedServer;isServerRunning()Z"), require = 0)
    private boolean checkThreadStatus(IntegratedServer instance) {
        return instance.isServerRunning() && ((Thread)ObfuscationReflectionHelper.getPrivateValue(MinecraftServer.class, instance, "field_175590_aa")).isAlive();
    }
}
