package by.siaroa.playfarmscoreboard.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;

public final class GuiScaleCompat {
    private static final int DEFAULT_EDITOR_GUI_SCALE = 2;
    private static final int MIN_EDITOR_GUI_SCALE = 1;
    private static final int MAX_EDITOR_GUI_SCALE = 8;
    private static int editorGuiScale = DEFAULT_EDITOR_GUI_SCALE;
    private static boolean forced;
    private static Integer previousGuiScale;
    private static boolean playerScaleActive;

    private GuiScaleCompat() {
    }

    public static void applyEditorScale(MinecraftClient client) {
        if (client == null || forced) {
            return;
        }

        int current = readGuiScale(client);
        int targetScale = getEditorGuiScale();
        previousGuiScale = current;
        if (current != targetScale) {
            writeGuiScale(client, targetScale);
        }
        forced = true;
        playerScaleActive = false;
    }

    public static void restoreEditorScale(MinecraftClient client) {
        if (client == null || !forced) {
            return;
        }

        Integer restore = previousGuiScale;
        previousGuiScale = null;
        forced = false;
        playerScaleActive = false;
        if (restore != null) {
            writeGuiScale(client, restore);
        }
    }

    public static void usePlayerGuiScaleWhileEditing(MinecraftClient client) {
        if (client == null || !forced || playerScaleActive) {
            return;
        }
        Integer scale = previousGuiScale;
        if (scale == null) {
            return;
        }
        writeGuiScale(client, scale);
        playerScaleActive = true;
    }

    public static void useEditorGuiScaleWhileEditing(MinecraftClient client) {
        if (client == null || !forced || !playerScaleActive) {
            return;
        }
        writeGuiScale(client, getEditorGuiScale());
        playerScaleActive = false;
    }

    private static int readGuiScale(MinecraftClient client) {
        try {
            SimpleOption<Integer> option = client.options.getGuiScale();
            Integer value = option.getValue();
            if (value != null) {
                return value;
            }
        } catch (Exception ignored) {
        }
        return DEFAULT_EDITOR_GUI_SCALE;
    }

    private static void writeGuiScale(MinecraftClient client, int scale) {
        try {
            SimpleOption<Integer> option = client.options.getGuiScale();
            option.setValue(clampEditorGuiScale(scale));
            client.onResolutionChanged();
        } catch (Exception ignored) {
        }
    }

    public static int getEditorGuiScale() {
        return clampEditorGuiScale(editorGuiScale);
    }

    public static int setEditorGuiScale(int scale) {
        editorGuiScale = clampEditorGuiScale(scale);
        return editorGuiScale;
    }

    private static int clampEditorGuiScale(int scale) {
        if (scale < MIN_EDITOR_GUI_SCALE) {
            return MIN_EDITOR_GUI_SCALE;
        }
        if (scale > MAX_EDITOR_GUI_SCALE) {
            return MAX_EDITOR_GUI_SCALE;
        }
        return scale;
    }
}
