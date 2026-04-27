package by.siaroa.playfarmscoreboard.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlayfarmscoreboardClient implements ClientModInitializer {
    private static final CustomHudState HUD_STATE = new CustomHudState();
    private static boolean comboWasDown;
    private static boolean bossBarHidden;
    private static int lastViewportWidth = -1;
    private static int lastViewportHeight = -1;
    private static final Pattern HUD_SCALE_INPUT_PATTERN = Pattern.compile("-?\\d+");

    @Override
    public void onInitializeClient() {
        CustomHudStorage.load(HUD_STATE);
        bossBarHidden = CustomHudStorage.isBossBarHidden();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("보스바")
                    .executes(context -> {
                        boolean hidden = toggleBossBarHidden();
                        context.getSource().sendFeedback(Text.literal(hidden
                                ? "보스바 숨김: 사용자 화면에서만 숨겨집니다."
                                : "보스바 표시: 다시 보입니다."));
                        return 1;
                    }));

            dispatcher.register(ClientCommandManager.literal("hud")
                    .executes(context -> {
                        int scale = GuiScaleCompat.getEditorGuiScale();
                        context.getSource().sendFeedback(Text.literal(
                                "HUD 에디터 GUI 비율: " + scale + " (기본 2, 범위 1~8)"
                        ));
                        return scale;
                    })
                    .then(ClientCommandManager.argument("비율", IntegerArgumentType.integer(1, 8))
                            .executes(context -> {
                                int requested = IntegerArgumentType.getInteger(context, "비율");
                                int applied = GuiScaleCompat.setEditorGuiScale(requested);
                                context.getSource().sendFeedback(Text.literal(
                                        "HUD 에디터 GUI 비율을 " + applied + "로 설정했습니다. 다음 HUD 에디터 열기부터 적용됩니다."
                                ));
                                return applied;
                            }))
                    .then(ClientCommandManager.argument("입력", StringArgumentType.greedyString())
                            .executes(context -> {
                                String raw = StringArgumentType.getString(context, "입력");
                                Integer parsed = parseHudScale(raw);
                                if (parsed == null) {
                                    context.getSource().sendFeedback(Text.literal("사용법: /hud <1~8>  (예: /hud 3, /hud (3))"));
                                    return 0;
                                }
                                int applied = GuiScaleCompat.setEditorGuiScale(parsed);
                                context.getSource().sendFeedback(Text.literal(
                                        "HUD 에디터 GUI 비율을 " + applied + "로 설정했습니다. 다음 HUD 에디터 열기부터 적용됩니다."
                                ));
                                return applied;
                            })));

            dispatcher.register(ClientCommandManager.literal("hudgui")
                    .executes(context -> {
                        int scale = GuiScaleCompat.getEditorGuiScale();
                        context.getSource().sendFeedback(Text.literal(
                                "HUD 에디터 GUI 비율: " + scale + " (기본 2, 범위 1~8)"
                        ));
                        return scale;
                    })
                    .then(ClientCommandManager.argument("비율", IntegerArgumentType.integer(1, 8))
                            .executes(context -> {
                                int requested = IntegerArgumentType.getInteger(context, "비율");
                                int applied = GuiScaleCompat.setEditorGuiScale(requested);
                                context.getSource().sendFeedback(Text.literal(
                                        "HUD 에디터 GUI 비율을 " + applied + "로 설정했습니다. 다음 HUD 에디터 열기부터 적용됩니다."
                                ));
                                return applied;
                            }))
                    .then(ClientCommandManager.argument("입력", StringArgumentType.greedyString())
                            .executes(context -> {
                                String raw = StringArgumentType.getString(context, "입력");
                                Integer parsed = parseHudScale(raw);
                                if (parsed == null) {
                                    context.getSource().sendFeedback(Text.literal("사용법: /hudgui <1~8>  (예: /hudgui 3, /hudgui (3))"));
                                    return 0;
                                }
                                int applied = GuiScaleCompat.setEditorGuiScale(parsed);
                                context.getSource().sendFeedback(Text.literal(
                                        "HUD 에디터 GUI 비율을 " + applied + "로 설정했습니다. 다음 HUD 에디터 열기부터 적용됩니다."
                                ));
                                return applied;
                            })));
        });
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
            clearViewportTracking();
            return;
        }

        updateHudViewport(client);

        boolean jDown = InputUtil.isKeyPressed(client.getWindow(), InputUtil.GLFW_KEY_J);
        boolean kDown = InputUtil.isKeyPressed(client.getWindow(), InputUtil.GLFW_KEY_K);
        boolean comboDown = jDown && kDown;
        boolean canOpenEditor = client.currentScreen == null;

        if (comboDown && !comboWasDown && canOpenEditor) {
            GuiScaleCompat.applyEditorScale(client);
            client.setScreen(new CustomHudEditorScreen(HUD_STATE));
        }

        comboWasDown = comboDown;
    }

    public static boolean isBossBarHidden() {
        return bossBarHidden;
    }

    private static boolean toggleBossBarHidden() {
        bossBarHidden = !bossBarHidden;
        CustomHudStorage.setBossBarHidden(HUD_STATE, bossBarHidden);
        return bossBarHidden;
    }

    private static void updateHudViewport(MinecraftClient client) {
        if (client.currentScreen instanceof CustomHudEditorScreen) {
            clearViewportTracking();
            return;
        }

        int scaledWidth = client.getWindow().getScaledWidth();
        int scaledHeight = client.getWindow().getScaledHeight();
        if (scaledWidth <= 0 || scaledHeight <= 0) {
            clearViewportTracking();
            return;
        }

        if (lastViewportWidth > 0 && lastViewportHeight > 0
                && (lastViewportWidth != scaledWidth || lastViewportHeight != scaledHeight)) {
            CustomHudRenderer.remapHudPositionForViewportChange(
                    HUD_STATE,
                    lastViewportWidth,
                    lastViewportHeight,
                    scaledWidth,
                    scaledHeight
            );
        }

        lastViewportWidth = scaledWidth;
        lastViewportHeight = scaledHeight;
    }

    private static void clearViewportTracking() {
        lastViewportWidth = -1;
        lastViewportHeight = -1;
    }

    private static Integer parseHudScale(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher matcher = HUD_SCALE_INPUT_PATTERN.matcher(raw);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
