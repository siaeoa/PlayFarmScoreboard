package by.siaroa.playfarmscoreboard.client.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.lang.reflect.Method;
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

    record TextLabel(int x, int y, String text, int color, int fontSize) implements HudElement {
        public static final int DEFAULT_FONT_SIZE = 9;
        public static final int MIN_FONT_SIZE = 6;
        public static final int MAX_FONT_SIZE = 48;
        private static final int APPROX_GLYPH_WIDTH = 6;
        private static final int APPROX_TEXT_HEIGHT = 9;
        private static Class<?> matrixClass;
        private static Method matrixPushMethod;
        private static Method matrixPopMethod;
        private static Method matrixTranslate2fMethod;
        private static Method matrixTranslate2dMethod;
        private static Method matrixTranslate3fMethod;
        private static Method matrixTranslate3dMethod;
        private static Method matrixScale2fMethod;
        private static Method matrixScale3fMethod;

        public TextLabel(int x, int y, String text, int color) {
            this(x, y, text, color, DEFAULT_FONT_SIZE);
        }

        public TextLabel {
            text = text == null ? "" : text;
            fontSize = HudRenderUtil.clamp(fontSize, MIN_FONT_SIZE, MAX_FONT_SIZE);
        }

        @Override
        public void render(DrawContext context, TextRenderer textRenderer, int originX, int originY) {
            MinecraftClient client = MinecraftClient.getInstance();
            // 편집 화면에선 값 대신 토큰 원문을 보여준다. 그래야 "뭘 적었는지" 한눈에 보인다.
            boolean editorOpen = client != null && client.currentScreen instanceof CustomHudEditorScreen;
            String resolvedText = editorOpen ? text : CustomHudPlaceholderResolver.resolve(text);
            float scale = fontSize / (float) DEFAULT_FONT_SIZE;
            if (Math.abs(scale - 1.0F) < 0.001F) {
                context.drawText(textRenderer, resolvedText, originX + x, originY + y, color, true);
                return;
            }

            Object matrices = context.getMatrices();
            try {
                pushMatrix(matrices);
                try {
                    translateMatrix(matrices, originX + x, originY + y);
                    scaleMatrix(matrices, scale);
                    context.drawText(textRenderer, resolvedText, 0, 0, color, true);
                } finally {
                    popMatrix(matrices);
                }
            } catch (Exception ignored) {
                // 매트릭스 API가 바뀌어도 텍스트 자체는 보이게 폴백한다.
                context.drawText(textRenderer, resolvedText, originX + x, originY + y, color, true);
            }
        }

        @Override
        public Bounds bounds() {
            // 실제 폰트 폭 측정은 비싸서, 여기선 대략 폭으로 히트박스만 빠르게 계산.
            float scale = fontSize / (float) DEFAULT_FONT_SIZE;
            int estimatedWidth = Math.max(1, (int) Math.ceil(Math.max(1, text.length() * APPROX_GLYPH_WIDTH) * scale));
            int estimatedHeight = Math.max(1, (int) Math.ceil(APPROX_TEXT_HEIGHT * scale));
            return new Bounds(x, y, x + estimatedWidth - 1, y + estimatedHeight - 1);
        }

        private static void pushMatrix(Object matrices) {
            ensureMatrixMethods(matrices);
            invokeNoArg(matrixPushMethod, matrices);
        }

        private static void popMatrix(Object matrices) {
            ensureMatrixMethods(matrices);
            invokeNoArg(matrixPopMethod, matrices);
        }

        private static void translateMatrix(Object matrices, int tx, int ty) {
            ensureMatrixMethods(matrices);
            if (matrixTranslate2fMethod != null) {
                invoke(matrixTranslate2fMethod, matrices, (float) tx, (float) ty);
                return;
            }
            if (matrixTranslate2dMethod != null) {
                invoke(matrixTranslate2dMethod, matrices, (double) tx, (double) ty);
                return;
            }
            if (matrixTranslate3fMethod != null) {
                invoke(matrixTranslate3fMethod, matrices, (float) tx, (float) ty, 0.0F);
                return;
            }
            if (matrixTranslate3dMethod != null) {
                invoke(matrixTranslate3dMethod, matrices, (double) tx, (double) ty, 0.0D);
                return;
            }
            throw new IllegalStateException("Matrix translate method not found");
        }

        private static void scaleMatrix(Object matrices, float scale) {
            ensureMatrixMethods(matrices);
            if (matrixScale2fMethod != null) {
                invoke(matrixScale2fMethod, matrices, scale, scale);
                return;
            }
            if (matrixScale3fMethod != null) {
                invoke(matrixScale3fMethod, matrices, scale, scale, 1.0F);
                return;
            }
            throw new IllegalStateException("Matrix scale method not found");
        }

        private static void ensureMatrixMethods(Object matrices) {
            if (matrices == null) {
                throw new IllegalStateException("DrawContext matrices missing");
            }
            Class<?> cls = matrices.getClass();
            if (cls == matrixClass && matrixPushMethod != null && matrixPopMethod != null) {
                return;
            }

            matrixClass = cls;
            matrixPushMethod = findMethod(cls, "pushMatrix");
            if (matrixPushMethod == null) {
                matrixPushMethod = findMethod(cls, "push");
            }
            matrixPopMethod = findMethod(cls, "popMatrix");
            if (matrixPopMethod == null) {
                matrixPopMethod = findMethod(cls, "pop");
            }

            matrixTranslate2fMethod = findMethod(cls, "translate", float.class, float.class);
            matrixTranslate2dMethod = findMethod(cls, "translate", double.class, double.class);
            matrixTranslate3fMethod = findMethod(cls, "translate", float.class, float.class, float.class);
            matrixTranslate3dMethod = findMethod(cls, "translate", double.class, double.class, double.class);
            matrixScale2fMethod = findMethod(cls, "scale", float.class, float.class);
            matrixScale3fMethod = findMethod(cls, "scale", float.class, float.class, float.class);

            if (matrixPushMethod == null || matrixPopMethod == null) {
                throw new IllegalStateException("Matrix push/pop methods not found");
            }
        }

        private static Method findMethod(Class<?> cls, String name, Class<?>... parameterTypes) {
            try {
                Method method = cls.getMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }

        private static void invokeNoArg(Method method, Object target) {
            if (method == null) {
                throw new IllegalStateException("Missing matrix method");
            }
            invoke(method, target);
        }

        private static void invoke(Method method, Object target, Object... args) {
            try {
                method.invoke(target, args);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to invoke matrix method: " + method.getName(), e);
            }
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
