package by.siaroa.playfarmscoreboard.client.hud;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

public final class CustomHudRenderer {
    private CustomHudRenderer() {
    }

    public static void renderHud(DrawContext context, TextRenderer textRenderer, CustomHudState state) {
        // HUD가 화면 밖으로 나가버리면 편집하다 멘붕 오니까, 시작점부터 안전하게 조여 둔다.
        HudElement.Bounds contentBounds = state.getContentBounds();
        int minOriginX = -contentBounds.minX();
        int maxOriginX = (context.getScaledWindowWidth() - 1) - contentBounds.maxX();
        int minOriginY = -contentBounds.minY();
        int maxOriginY = (context.getScaledWindowHeight() - 1) - contentBounds.maxY();
        int originX = clampOrigin(state.getHudX(), minOriginX, maxOriginX);
        int originY = clampOrigin(state.getHudY(), minOriginY, maxOriginY);
        int minVisibleX = -originX;
        int minVisibleY = -originY;
        int maxVisibleX = context.getScaledWindowWidth() - originX;
        int maxVisibleY = context.getScaledWindowHeight() - originY;
        renderHud(context, textRenderer, state, originX, originY, minVisibleX, minVisibleY, maxVisibleX, maxVisibleY);
    }

    public static void renderHud(DrawContext context, TextRenderer textRenderer, CustomHudState state, int originX, int originY) {
        renderHud(
                context,
                textRenderer,
                state,
                originX,
                originY,
                Integer.MIN_VALUE,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE
        );
    }

    public static void renderHud(
            DrawContext context,
            TextRenderer textRenderer,
            CustomHudState state,
            int originX,
            int originY,
            int minVisibleX,
            int minVisibleY,
            int maxVisibleX,
            int maxVisibleY
    ) {
        // 보이는 영역만 그리는 게 체감상 꽤 크다. 괜히 전부 그리다 프레임 흔들리면 속상하다.
        for (HudElement element : state.getElements()) {
            HudElement.Bounds bounds = element.bounds();
            if (bounds.maxX() < minVisibleX || bounds.maxY() < minVisibleY || bounds.minX() > maxVisibleX || bounds.minY() > maxVisibleY) {
                continue;
            }

            if (element instanceof HudElement.BrushStroke stroke) {
                stroke.renderClipped(context, originX, originY, minVisibleX, minVisibleY, maxVisibleX, maxVisibleY);
            } else {
                element.render(context, textRenderer, originX, originY);
            }
        }
    }

    private static int clampOrigin(int value, int min, int max) {
        if (min <= max) {
            return HudRenderUtil.clamp(value, min, max);
        }
        // 가끔 min/max가 역전되는데, 여기선 가운데로 타협하는 게 제일 덜 놀란다.
        return (min + max) / 2;
    }
}
