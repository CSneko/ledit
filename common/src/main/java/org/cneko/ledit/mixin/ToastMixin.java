package org.cneko.ledit.mixin;

import net.minecraft.advancements.AdvancementType;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.cneko.ledit.client.LedItClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ToastComponent.class)
public class ToastMixin {

    @Inject(method = "addToast", at = @At("HEAD"))
    private void onAddToast(Toast toast, CallbackInfo ci) {
        if (toast instanceof AdvancementToast) {
            var holder = ((AdvancementToastAccessor) toast).getAdvancement();
            AdvancementType type = holder.value().display()
                    .map(d -> (AdvancementType) d.getType())
                    .orElse(AdvancementType.TASK);

            var mgr = LedItClient.getEffectManager();
            if (mgr != null) {
                mgr.triggerAdvancement(type);
            }
        }
    }
}
