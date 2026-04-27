package by.siaroa.playfarmscoreboard.client.hud;

import org.joml.Matrix3x2fStack;

final class HudMatrixCompat {
    private HudMatrixCompat() {
    }

    static void push(Object matrices) {
        ((Matrix3x2fStack) matrices).pushMatrix();
    }

    static void pop(Object matrices) {
        ((Matrix3x2fStack) matrices).popMatrix();
    }

    static void translate(Object matrices, int x, int y) {
        ((Matrix3x2fStack) matrices).translate((float) x, (float) y);
    }

    static void scale(Object matrices, float scale) {
        ((Matrix3x2fStack) matrices).scale(scale, scale);
    }
}
