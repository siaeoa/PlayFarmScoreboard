package by.siaroa.playfarmscoreboard.client.hud;

import net.minecraft.client.gui.DrawContext;

public final class HudRenderUtil {
    private HudRenderUtil() {
    }

    public static int argb(int alpha, int red, int green, int blue) {
        return (clamp(alpha, 0, 255) << 24)
                | (clamp(red, 0, 255) << 16)
                | (clamp(green, 0, 255) << 8)
                | clamp(blue, 0, 255);
    }

    public static void drawFilledRect(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        int left = Math.min(x1, x2);
        int right = Math.max(x1, x2);
        int top = Math.min(y1, y2);
        int bottom = Math.max(y1, y2);
        context.fill(left, top, right + 1, bottom + 1, color);
    }

    public static void drawFilledEllipse(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        int left = Math.min(x1, x2);
        int right = Math.max(x1, x2);
        int top = Math.min(y1, y2);
        int bottom = Math.max(y1, y2);

        double radiusX = Math.max(0.5D, (right - left) / 2.0D);
        double radiusY = Math.max(0.5D, (bottom - top) / 2.0D);
        double centerX = (left + right) / 2.0D;
        double centerY = (top + bottom) / 2.0D;

        for (int y = top; y <= bottom; y++) {
            double normalizedY = (y - centerY) / radiusY;
            double span = Math.sqrt(Math.max(0.0D, 1.0D - normalizedY * normalizedY)) * radiusX;
            int startX = (int) Math.floor(centerX - span);
            int endX = (int) Math.ceil(centerX + span);
            context.fill(startX, y, endX + 1, y + 1, color);
        }
    }

    public static void drawLine(DrawContext context, int x1, int y1, int x2, int y2, int size, int color) {
        int x = x1;
        int y = y1;
        int deltaX = Math.abs(x2 - x1);
        int stepX = x1 < x2 ? 1 : -1;
        int deltaY = -Math.abs(y2 - y1);
        int stepY = y1 < y2 ? 1 : -1;
        int error = deltaX + deltaY;

        while (true) {
            drawBrushDot(context, x, y, size, color);
            if (x == x2 && y == y2) {
                break;
            }

            int doubledError = 2 * error;
            if (doubledError >= deltaY) {
                error += deltaY;
                x += stepX;
            }
            if (doubledError <= deltaX) {
                error += deltaX;
                y += stepY;
            }
        }
    }

    public static void drawBrushDot(DrawContext context, int x, int y, int size, int color) {
        int clampedSize = clamp(size, 1, 100);
        int left = x - (clampedSize / 2);
        int top = y - (clampedSize / 2);
        context.fill(left, top, left + clampedSize, top + clampedSize, color);
    }

    public static void drawStrokedRect(DrawContext context, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }

        context.fill(x, y, x + width, y + 1, color);
        if (height > 1) {
            context.fill(x, y + height - 1, x + width, y + height, color);
        }

        if (height > 2) {
            context.fill(x, y + 1, x + 1, y + height - 1, color);
            if (width > 1) {
                context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
            }
        }
    }

    public static void drawCircleOutline(DrawContext context, int centerX, int centerY, int radius, int color) {
        int r = Math.max(1, radius);
        int x = r;
        int y = 0;
        int decision = 1 - x;

        while (y <= x) {
            drawPixel(context, centerX + x, centerY + y, color);
            drawPixel(context, centerX + y, centerY + x, color);
            drawPixel(context, centerX - y, centerY + x, color);
            drawPixel(context, centerX - x, centerY + y, color);
            drawPixel(context, centerX - x, centerY - y, color);
            drawPixel(context, centerX - y, centerY - x, color);
            drawPixel(context, centerX + y, centerY - x, color);
            drawPixel(context, centerX + x, centerY - y, color);

            y++;
            if (decision <= 0) {
                decision += (2 * y) + 1;
            } else {
                x--;
                decision += (2 * (y - x)) + 1;
            }
        }
    }

    private static void drawPixel(DrawContext context, int x, int y, int color) {
        context.fill(x, y, x + 1, y + 1, color);
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
