package by.siaroa.playfarmscoreboard.client.hud;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

public final class CustomHudRenderer {
    private CustomHudRenderer() {
    }

    public static void renderHud(DrawContext context, TextRenderer textRenderer, CustomHudState state) {
        float hudScale = Math.max(0.01F, state.getHudScale());
        // HUD가 화면 밖으로 나가버리면 편집하다 멘붕 오니까, 시작점부터 안전하게 조여 둔다.
        HudElement.Bounds contentBounds = state.getContentBounds();
        int minOriginX = scaledMinOrigin(contentBounds.minX(), hudScale);
        int maxOriginX = scaledMaxOrigin(context.getScaledWindowWidth(), contentBounds.maxX(), hudScale);
        int minOriginY = scaledMinOrigin(contentBounds.minY(), hudScale);
        int maxOriginY = scaledMaxOrigin(context.getScaledWindowHeight(), contentBounds.maxY(), hudScale);
        int originX = clampOrigin(state.getHudX(), minOriginX, maxOriginX);
        int originY = clampOrigin(state.getHudY(), minOriginY, maxOriginY);
        int minVisibleX = scaledVisibleMin(originX, hudScale);
        int minVisibleY = scaledVisibleMin(originY, hudScale);
        int maxVisibleX = scaledVisibleMax(context.getScaledWindowWidth(), originX, hudScale);
        int maxVisibleY = scaledVisibleMax(context.getScaledWindowHeight(), originY, hudScale);
        renderHudInternal(context, textRenderer, state, originX, originY, minVisibleX, minVisibleY, maxVisibleX, maxVisibleY, hudScale);
    }

    public static void remapHudPositionForViewportChange(
            CustomHudState state,
            int oldViewportWidth,
            int oldViewportHeight,
            int newViewportWidth,
            int newViewportHeight
    ) {
        if (state == null || oldViewportWidth <= 0 || oldViewportHeight <= 0 || newViewportWidth <= 0 || newViewportHeight <= 0) {
            return;
        }
        if (oldViewportWidth == newViewportWidth && oldViewportHeight == newViewportHeight) {
            return;
        }

        float hudScale = Math.max(0.01F, state.getHudScale());
        HudElement.Bounds contentBounds = state.getContentBounds();

        int oldMinX = scaledMinOrigin(contentBounds.minX(), hudScale);
        int oldMaxX = scaledMaxOrigin(oldViewportWidth, contentBounds.maxX(), hudScale);
        int oldMinY = scaledMinOrigin(contentBounds.minY(), hudScale);
        int oldMaxY = scaledMaxOrigin(oldViewportHeight, contentBounds.maxY(), hudScale);

        int newMinX = scaledMinOrigin(contentBounds.minX(), hudScale);
        int newMaxX = scaledMaxOrigin(newViewportWidth, contentBounds.maxX(), hudScale);
        int newMinY = scaledMinOrigin(contentBounds.minY(), hudScale);
        int newMaxY = scaledMaxOrigin(newViewportHeight, contentBounds.maxY(), hudScale);

        int remappedX = remapWithinRanges(state.getHudX(), oldMinX, oldMaxX, newMinX, newMaxX);
        int remappedY = remapWithinRanges(state.getHudY(), oldMinY, oldMaxY, newMinY, newMaxY);
        state.setHudPositionDirect(remappedX, remappedY);
    }

    public static void renderHud(DrawContext context, TextRenderer textRenderer, CustomHudState state, int originX, int originY) {
        renderHudInternal(context, textRenderer, state, originX, originY, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 1.0F);
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
        renderHudInternal(context, textRenderer, state, originX, originY, minVisibleX, minVisibleY, maxVisibleX, maxVisibleY, 1.0F);
    }

    private static void renderHudInternal(
            DrawContext context,
            TextRenderer textRenderer,
            CustomHudState state,
            int originX,
            int originY,
            int minVisibleX,
            int minVisibleY,
            int maxVisibleX,
            int maxVisibleY,
            float scale
    ) {
        Object matrices = context.getMatrices();
        pushMatrix(matrices);
        translateMatrix(matrices, originX, originY);
        scaleMatrix(matrices, scale);
        // 보이는 영역만 그리는 게 체감상 꽤 크다. 괜히 전부 그리다 프레임 흔들리면 속상하다.
        try {
            for (HudElement element : state.getElements()) {
                HudElement.Bounds bounds = element.bounds();
                if (bounds.maxX() < minVisibleX || bounds.maxY() < minVisibleY || bounds.minX() > maxVisibleX || bounds.minY() > maxVisibleY) {
                    continue;
                }

                if (element instanceof HudElement.BrushStroke stroke) {
                    stroke.renderClipped(context, 0, 0, minVisibleX, minVisibleY, maxVisibleX, maxVisibleY);
                } else {
                    element.render(context, textRenderer, 0, 0);
                }
            }
        } finally {
            popMatrix(matrices);
        }
    }

    private static int clampOrigin(int value, int min, int max) {
        if (min <= max) {
            return HudRenderUtil.clamp(value, min, max);
        }
        // 가끔 min/max가 역전되는데, 여기선 가운데로 타협하는 게 제일 덜 놀란다.
        return (min + max) / 2;
    }

    private static int scaledMinOrigin(int minContent, float scale) {
        return (int) Math.ceil(-minContent * (double) scale);
    }

    private static int scaledMaxOrigin(int viewportSize, int maxContent, float scale) {
        return (int) Math.floor((viewportSize - 1) - (maxContent * (double) scale));
    }

    private static int scaledVisibleMin(int origin, float scale) {
        return (int) Math.ceil((-origin) / (double) scale);
    }

    private static int scaledVisibleMax(int viewportSize, int origin, float scale) {
        return (int) Math.floor(((viewportSize - 1) - origin) / (double) scale);
    }

    private static int remapWithinRanges(int value, int oldMin, int oldMax, int newMin, int newMax) {
        if (newMin > newMax) {
            return (newMin + newMax) / 2;
        }
        if (oldMin > oldMax) {
            return clampOrigin(value, newMin, newMax);
        }
        if (oldMax == oldMin) {
            return HudRenderUtil.clamp(newMin, newMin, newMax);
        }

        double progress = (value - oldMin) / (double) (oldMax - oldMin);
        progress = Math.max(0.0D, Math.min(1.0D, progress));
        int mapped = (int) Math.round(newMin + progress * (newMax - newMin));
        return HudRenderUtil.clamp(mapped, newMin, newMax);
    }

    private static void pushMatrix(Object matrices) {
        HudMatrixCompat.push(matrices);
    }

    private static void popMatrix(Object matrices) {
        HudMatrixCompat.pop(matrices);
    }

    private static void translateMatrix(Object matrices, int x, int y) {
        HudMatrixCompat.translate(matrices, x, y);
    }

    private static void scaleMatrix(Object matrices, float scale) {
        HudMatrixCompat.scale(matrices, scale);
    }
}
