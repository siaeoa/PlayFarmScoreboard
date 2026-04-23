package by.siaroa.playfarmscoreboard.client.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public interface HudElement {
    void render(DrawContext context, TextRenderer textRenderer, int originX, int originY);

    Bounds bounds();

    record Bounds(int minX, int minY, int maxX, int maxY) {
        public Bounds {
            if (maxX < minX || maxY < minY) {
                throw new IllegalArgumentException("Invalid bounds");
            }
        }
    }

    record HudPoint(int x, int y) {
    }

    final class BrushStroke implements HudElement {
        private final List<HudPoint> points;
        private final int size;
        private final int color;
        private final Bounds bounds;

        public BrushStroke(List<HudPoint> points, int size, int color) {
            if (points.isEmpty()) {
                throw new IllegalArgumentException("Brush stroke requires at least one point");
            }

            this.points = List.copyOf(points);
            this.size = HudRenderUtil.clamp(size, 1, 100);
            this.color = color;
            this.bounds = computeBounds(this.points, this.size);
        }

        @Override
        public void render(DrawContext context, TextRenderer textRenderer, int originX, int originY) {
            renderClipped(context, originX, originY, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        }

        public void renderClipped(
                DrawContext context,
                int originX,
                int originY,
                int minVisibleX,
                int minVisibleY,
                int maxVisibleX,
                int maxVisibleY
        ) {
            int pad = Math.max(1, (int) Math.ceil(size / 2.0D));
            // 점이 많아질수록 비용이 커지니까, 화면 밖은 과감히 스킵한다.
            for (HudPoint point : points) {
                if (point.x() < minVisibleX - pad || point.x() > maxVisibleX + pad
                        || point.y() < minVisibleY - pad || point.y() > maxVisibleY + pad) {
                    continue;
                }
                HudRenderUtil.drawBrushDot(context, originX + point.x(), originY + point.y(), size, color);
            }
        }

        @Override
        public Bounds bounds() {
            return bounds;
        }

        public List<HudPoint> points() {
            return points;
        }

        public int size() {
            return size;
        }

        public int color() {
            return color;
        }

        private static Bounds computeBounds(List<HudPoint> points, int size) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;

            for (HudPoint point : points) {
                minX = Math.min(minX, point.x());
                minY = Math.min(minY, point.y());
                maxX = Math.max(maxX, point.x());
                maxY = Math.max(maxY, point.y());
            }

            int pad = Math.max(1, (int) Math.ceil(size / 2.0D));
            return new Bounds(
                    Math.max(0, minX - pad),
                    Math.max(0, minY - pad),
                    maxX + pad,
                    maxY + pad
            );
        }

        public static List<HudPoint> copyPoints(List<HudPoint> points) {
            return new ArrayList<>(points);
        }
    }

    record FillRect(int x, int y, int width, int height, int color) implements HudElement {
        public FillRect {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Fill dimensions must be positive");
            }
        }

        @Override
        public void render(DrawContext context, TextRenderer textRenderer, int originX, int originY) {
            context.fill(originX + x, originY + y, originX + x + width, originY + y + height, color);
        }

        @Override
        public Bounds bounds() {
            return new Bounds(x, y, x + width - 1, y + height - 1);
        }
    }

    record Rectangle(int x1, int y1, int x2, int y2, int color) implements HudElement {
        @Override
        public void render(DrawContext context, TextRenderer textRenderer, int originX, int originY) {
            HudRenderUtil.drawFilledRect(context, originX + x1, originY + y1, originX + x2, originY + y2, color);
        }

        @Override
        public Bounds bounds() {
            return new Bounds(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2));
        }
    }

    record Circle(int x1, int y1, int x2, int y2, int color) implements HudElement {
        @Override
        public void render(DrawContext context, TextRenderer textRenderer, int originX, int originY) {
            HudRenderUtil.drawFilledEllipse(context, originX + x1, originY + y1, originX + x2, originY + y2, color);
        }

        @Override
        public Bounds bounds() {
            return new Bounds(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2));
        }
    }

    record Line(int x1, int y1, int x2, int y2, int size, int color) implements HudElement {
        @Override
        public void render(DrawContext context, TextRenderer textRenderer, int originX, int originY) {
            HudRenderUtil.drawLine(context, originX + x1, originY + y1, originX + x2, originY + y2, size, color);
        }

        @Override
        public Bounds bounds() {
            int pad = Math.max(1, (int) Math.ceil(size / 2.0D));
            return new Bounds(
                    Math.max(0, Math.min(x1, x2) - pad),
                    Math.max(0, Math.min(y1, y2) - pad),
                    Math.max(x1, x2) + pad,
                    Math.max(y1, y2) + pad
            );
        }
    }

    record TextLabel(int x, int y, String text, int color) implements HudElement {
        @Override
        public void render(DrawContext context, TextRenderer textRenderer, int originX, int originY) {
            MinecraftClient client = MinecraftClient.getInstance();
            // 편집 화면에선 값 대신 토큰 원문을 보여준다. 그래야 "뭘 적었는지" 한눈에 보인다.
            boolean editorOpen = client != null && client.currentScreen instanceof CustomHudEditorScreen;
            String resolvedText = editorOpen ? text : CustomHudPlaceholderResolver.resolve(text);
            context.drawText(textRenderer, resolvedText, originX + x, originY + y, color, true);
        }

        @Override
        public Bounds bounds() {
            // 실제 폰트 폭 측정은 비싸서, 여기선 대략 폭으로 히트박스만 빠르게 계산.
            int estimatedWidth = Math.max(1, text.length() * 6);
            return new Bounds(x, y, x + estimatedWidth, y + 10);
        }
    }

    record ImageSprite(int x, int y, int width, int height, String sourcePath, boolean clipOnResizeShrink) implements HudElement {
        public ImageSprite(int x, int y, int width, int height, String sourcePath) {
            this(x, y, width, height, sourcePath, false);
        }

        public ImageSprite {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Image dimensions must be positive");
            }
        }

        @Override
        public void render(DrawContext context, TextRenderer textRenderer, int originX, int originY) {
            // GUI는 재능이 없어, AI한테 ㅋ ... 그래도 drawImage 호출만큼은 단순하게 유지한다.
            CustomHudImageTextureManager.drawImage(context, sourcePath, originX + x, originY + y, width, height, clipOnResizeShrink);
        }

        @Override
        public Bounds bounds() {
            return new Bounds(x, y, x + width - 1, y + height - 1);
        }
    }
}
