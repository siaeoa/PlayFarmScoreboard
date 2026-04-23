package by.siaroa.playfarmscoreboard.client;

import by.siaroa.playfarmscoreboard.client.hud.CustomHudEditorScreen;
import by.siaroa.playfarmscoreboard.client.hud.CustomHudFlyStorage;
import by.siaroa.playfarmscoreboard.client.hud.CustomHudRenderer;
import by.siaroa.playfarmscoreboard.client.hud.CustomHudState;
import by.siaroa.playfarmscoreboard.client.hud.CustomHudStorage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

public class PlayfarmscoreboardClient implements ClientModInitializer {
    private static final CustomHudState HUD_STATE = new CustomHudState();
    private static boolean comboWasDown;
    private static boolean bossBarHidden;

    @Override
    public void onInitializeClient() {
        CustomHudStorage.load(HUD_STATE);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("보스바")
                        .executes(context -> {
                            boolean hidden = toggleBossBarHidden();
                            context.getSource().sendFeedback(Text.literal(hidden
                                    ? "보스바 숨김: 사용자 화면에서만 숨겨집니다."
                                    : "보스바 표시: 다시 보입니다."));
                            return 1;
                        })
        ));
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> CustomHudFlyStorage.flush());
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null) {
                return;
            }

            if (client.currentScreen instanceof CustomHudEditorScreen) {
                return;
            }

            CustomHudRenderer.renderHud(drawContext, client.textRenderer, HUD_STATE);
        });
    }

    private void onClientTick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            CustomHudFlyStorage.flush();
            comboWasDown = false;
            return;
        }

        boolean jDown = InputUtil.isKeyPressed(client.getWindow(), InputUtil.GLFW_KEY_J);
        boolean kDown = InputUtil.isKeyPressed(client.getWindow(), InputUtil.GLFW_KEY_K);
        boolean comboDown = jDown && kDown;
        boolean canOpenEditor = client.currentScreen == null;

        if (comboDown && !comboWasDown && canOpenEditor) {
            client.setScreen(new CustomHudEditorScreen(HUD_STATE));
        }

        comboWasDown = comboDown;
    }

    public static boolean isBossBarHidden() {
        return bossBarHidden;
    }

    private static boolean toggleBossBarHidden() {
        bossBarHidden = !bossBarHidden;
        return bossBarHidden;
    }
}
