package by.siaroa.playfarmscoreboard.mixin.client;

import by.siaroa.playfarmscoreboard.client.hud.CustomHudPlaceholderResolver;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "setOverlayMessage", at = @At("HEAD"))
    private void playfarmscoreboard$captureOverlayMessage(Text message, boolean tinted, CallbackInfo ci) {
        if (message == null) {
            return;
        }
        // 렌더 타이밍에만 기대면 가끔 놓친다. 들어온 즉시 파싱으로 안전하게 간다.
        // 액션바 수명은 생각보다 짧다. 한 틱 늦으면 "아무 일도 없었던 것처럼" 사라진다.
        CustomHudPlaceholderResolver.observeOverlayMessage(message.getString());
    }
}
