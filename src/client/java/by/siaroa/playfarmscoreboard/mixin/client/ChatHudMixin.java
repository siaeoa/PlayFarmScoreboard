package by.siaroa.playfarmscoreboard.mixin.client;

import by.siaroa.playfarmscoreboard.client.hud.CustomHudPlaceholderResolver;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    private void playfarmscoreboard$captureSimpleChat(Text message, CallbackInfo ci) {
        observe(message);
    }

    @Inject(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD")
    )
    private void playfarmscoreboard$captureSignedChat(
            Text message,
            MessageSignatureData signatureData,
            MessageIndicator indicator,
            CallbackInfo ci
    ) {
        observe(message);
    }

    private static void observe(Text message) {
        if (message == null) {
            return;
        }
        // 채팅은 서버 접두 마커가 있는 메시지만 파싱한다.
        CustomHudPlaceholderResolver.observeChatMessage(message.getString());
    }
}
