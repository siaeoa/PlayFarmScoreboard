package by.siaroa.playfarmscoreboard.client.hud;

import net.minecraft.client.util.math.MatrixStack;

final class HudMatrixCompat {
    private HudMatrixCompat() {
    }

    static void push(Object matrices) {
        ((MatrixStack) matrices).push();
    }

    static void pop(Object matrices) {
        ((MatrixStack) matrices).pop();
    }

    static void translate(Object matrices, int x, int y) {
        ((MatrixStack) matrices).translate((float) x, (float) y, 0.0F);
    }

    static void scale(Object matrices, float scale) {
        ((MatrixStack) matrices).scale(scale, scale, 1.0F);
    }
}
