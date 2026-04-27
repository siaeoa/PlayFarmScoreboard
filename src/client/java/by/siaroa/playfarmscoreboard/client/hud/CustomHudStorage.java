package by.siaroa.playfarmscoreboard.client.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CustomHudStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String BUNDLED_HUD_ZIP_RESOURCE = "hud.zip";
    private static final Path FILE_PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("playfarmscoreboard")
            .resolve("custom_hud.json");
    private static boolean bossBarHidden;

    private CustomHudStorage() {
    }

    public static boolean resetToInitialHud(CustomHudState state) {
        if (loadBundledHudZip(state)) {
            return true;
        }
        state.resetToDefault();
        save(state);
        return false;
    }

    public static void load(CustomHudState state) {
        if (!Files.exists(FILE_PATH)) {
            bossBarHidden = false;
            // 첫 실행이면 리소스 hud.zip을 우선 적용하고, 없으면 기본 HUD로 간다.
            resetToInitialHud(state);
            return;
        }

        try {
            String raw = Files.readString(FILE_PATH, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            bossBarHidden = getBoolean(root, "bossBarHidden", false);

            int canvasWidth = getInt(root, "canvasWidth", state.getCanvasWidth());
            int canvasHeight = getInt(root, "canvasHeight", state.getCanvasHeight());
            int hudX = getInt(root, "hudX", state.getHudX());
            int hudY = getInt(root, "hudY", state.getHudY());
            int hudScale = getInt(root, "hudScale", state.getHudScalePercent());

            List<HudElement> elements = new ArrayList<>();
            if (root.has("elements") && root.get("elements").isJsonArray()) {
                JsonArray array = root.getAsJsonArray("elements");
                for (JsonElement element : array) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    HudElement hudElement = decodeElement(element.getAsJsonObject());
                    if (hudElement != null) {
                        elements.add(hudElement);
                    }
                }
            }

            if (elements.isEmpty()) {
                // 파일은 있는데 내용이 비면, 사용자는 그냥 "초기화된 상태"로 이해하는 게 편하다.
                resetToInitialHud(state);
            } else {
                state.replaceAll(elements, canvasWidth, canvasHeight, hudX, hudY, hudScale);
            }
        } catch (Exception ignored) {
            bossBarHidden = false;
            // 저장 파일이 깨졌을 때 앱까지 멈출 필요는 없다. 기본값으로 회복하고 지나간다.
            resetToInitialHud(state);
        }
    }

    public static boolean isBossBarHidden() {
        return bossBarHidden;
    }

    public static void setBossBarHidden(CustomHudState state, boolean hidden) {
        if (bossBarHidden == hidden) {
            return;
        }
        bossBarHidden = hidden;
        save(state);
    }

    public static void save(CustomHudState state) {
        try {
            Files.createDirectories(FILE_PATH.getParent());

            JsonObject root = new JsonObject();
            root.addProperty("canvasWidth", state.getCanvasWidth());
            root.addProperty("canvasHeight", state.getCanvasHeight());
            root.addProperty("hudX", state.getHudX());
            root.addProperty("hudY", state.getHudY());
            root.addProperty("hudScale", state.getHudScalePercent());
            root.addProperty("bossBarHidden", bossBarHidden);

            JsonArray array = new JsonArray();
            for (HudElement element : state.getElements()) {
                JsonObject encoded = encodeElement(element);
                if (encoded != null) {
                    array.add(encoded);
                }
            }

            root.add("elements", array);
            Files.writeString(FILE_PATH, GSON.toJson(root), StandardCharsets.UTF_8);
            // 이미지 파일은 점점 쌓이기 쉬워서, 저장 타이밍에 한 번씩 청소해 준다.
            pruneUnusedManagedImages(state.getElements());
        } catch (IOException ignored) {
            // 그냥 오늘은 여기까지 하고 싶어도, 저장 실패는 조용히 넘기고 편집 흐름은 지킨다.
            // 디스크 실패는 여기서 조용히 넘긴다. UI 편집 흐름이 끊기면 체감이 더 나쁘다.
        }
    }

    private static JsonObject encodeElement(HudElement element) {
        JsonObject object = new JsonObject();

        if (element instanceof HudElement.BrushStroke stroke) {
            object.addProperty("type", "brush");
            object.addProperty("size", stroke.size());
            object.addProperty("color", stroke.color());
            JsonArray points = new JsonArray();
            for (HudElement.HudPoint point : stroke.points()) {
                JsonObject pointObject = new JsonObject();
                pointObject.addProperty("x", point.x());
                pointObject.addProperty("y", point.y());
                points.add(pointObject);
            }
            object.add("points", points);
            return object;
        }

        if (element instanceof HudElement.FillRect fillRect) {
            object.addProperty("type", "fill");
            object.addProperty("x", fillRect.x());
            object.addProperty("y", fillRect.y());
            object.addProperty("width", fillRect.width());
            object.addProperty("height", fillRect.height());
            object.addProperty("color", fillRect.color());
            return object;
        }

        if (element instanceof HudElement.Rectangle rectangle) {
            object.addProperty("type", "rectangle");
            object.addProperty("x1", rectangle.x1());
            object.addProperty("y1", rectangle.y1());
            object.addProperty("x2", rectangle.x2());
            object.addProperty("y2", rectangle.y2());
            object.addProperty("color", rectangle.color());
            return object;
        }

        if (element instanceof HudElement.Circle circle) {
            object.addProperty("type", "circle");
            object.addProperty("x1", circle.x1());
            object.addProperty("y1", circle.y1());
            object.addProperty("x2", circle.x2());
            object.addProperty("y2", circle.y2());
            object.addProperty("color", circle.color());
            return object;
        }

        if (element instanceof HudElement.Line line) {
            object.addProperty("type", "line");
            object.addProperty("x1", line.x1());
            object.addProperty("y1", line.y1());
            object.addProperty("x2", line.x2());
            object.addProperty("y2", line.y2());
            object.addProperty("size", line.size());
            object.addProperty("color", line.color());
            return object;
        }

        if (element instanceof HudElement.TextLabel textLabel) {
            object.addProperty("type", "text");
            object.addProperty("x", textLabel.x());
            object.addProperty("y", textLabel.y());
            object.addProperty("text", textLabel.text());
            object.addProperty("color", textLabel.color());
            object.addProperty("fontSize", textLabel.fontSize());
            return object;
        }

        if (element instanceof HudElement.ImageSprite imageSprite) {
            object.addProperty("type", "image");
            object.addProperty("x", imageSprite.x());
            object.addProperty("y", imageSprite.y());
            object.addProperty("width", imageSprite.width());
            object.addProperty("height", imageSprite.height());
            object.addProperty("sourcePath", imageSprite.sourcePath());
            object.addProperty("clipOnResizeShrink", imageSprite.clipOnResizeShrink());
            return object;
        }

        return null;
    }

    private static HudElement decodeElement(JsonObject object) {
        String type = getString(object, "type", "");
        return switch (type) {
            case "brush" -> decodeBrush(object);
            case "fill" -> new HudElement.FillRect(
                    getInt(object, "x", 0),
                    getInt(object, "y", 0),
                    Math.max(1, getInt(object, "width", 1)),
                    Math.max(1, getInt(object, "height", 1)),
                    getInt(object, "color", HudRenderUtil.argb(255, 255, 255, 255))
            );
            case "rectangle" -> new HudElement.Rectangle(
                    getInt(object, "x1", 0),
                    getInt(object, "y1", 0),
                    getInt(object, "x2", 0),
                    getInt(object, "y2", 0),
                    getInt(object, "color", HudRenderUtil.argb(255, 255, 255, 255))
            );
            case "circle" -> new HudElement.Circle(
                    getInt(object, "x1", 0),
                    getInt(object, "y1", 0),
                    getInt(object, "x2", 0),
                    getInt(object, "y2", 0),
                    getInt(object, "color", HudRenderUtil.argb(255, 255, 255, 255))
            );
            case "line" -> new HudElement.Line(
                    getInt(object, "x1", 0),
                    getInt(object, "y1", 0),
                    getInt(object, "x2", 0),
                    getInt(object, "y2", 0),
                    Math.max(1, getInt(object, "size", 1)),
                    getInt(object, "color", HudRenderUtil.argb(255, 255, 255, 255))
            );
            case "text" -> new HudElement.TextLabel(
                    getInt(object, "x", 0),
                    getInt(object, "y", 0),
                    getString(object, "text", ""),
                    getInt(object, "color", HudRenderUtil.argb(255, 255, 255, 255)),
                    getInt(object, "fontSize", HudElement.TextLabel.DEFAULT_FONT_SIZE)
            );
            case "image" -> new HudElement.ImageSprite(
                    getInt(object, "x", 0),
                    getInt(object, "y", 0),
                    Math.max(1, getInt(object, "width", 32)),
                    Math.max(1, getInt(object, "height", 32)),
                    getString(object, "sourcePath", ""),
                    getBoolean(object, "clipOnResizeShrink", false)
            );
            default -> null;
        };
    }

    private static HudElement decodeBrush(JsonObject object) {
        if (!object.has("points") || !object.get("points").isJsonArray()) {
            return null;
        }

        List<HudElement.HudPoint> points = new ArrayList<>();
        JsonArray pointsArray = object.getAsJsonArray("points");
        for (JsonElement pointElement : pointsArray) {
            if (!pointElement.isJsonObject()) {
                continue;
            }
            JsonObject pointObject = pointElement.getAsJsonObject();
            points.add(new HudElement.HudPoint(
                    getInt(pointObject, "x", 0),
                    getInt(pointObject, "y", 0)
            ));
        }

        if (points.isEmpty()) {
            return null;
        }

        return new HudElement.BrushStroke(
                points,
                Math.max(1, getInt(object, "size", 1)),
                getInt(object, "color", HudRenderUtil.argb(255, 255, 255, 255))
        );
    }

    private static int getInt(JsonObject object, String key, int defaultValue) {
        if (!object.has(key)) {
            return defaultValue;
        }
        try {
            return object.get(key).getAsInt();
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static String getString(JsonObject object, String key, String defaultValue) {
        if (!object.has(key)) {
            return defaultValue;
        }
        try {
            return object.get(key).getAsString();
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static boolean getBoolean(JsonObject object, String key, boolean defaultValue) {
        if (!object.has(key)) {
            return defaultValue;
        }
        try {
            return object.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static void pruneUnusedManagedImages(List<HudElement> elements) {
        Path imageDir = CustomHudImageTextureManager.getImageStoreDir();
        try {
            if (!Files.isDirectory(imageDir)) {
                return;
            }
        } catch (Exception ignored) {
            return;
        }

        Set<String> used = new HashSet<>();
        for (HudElement element : elements) {
            if (!(element instanceof HudElement.ImageSprite imageSprite)) {
                continue;
            }
            try {
                Path normalized = Path.of(imageSprite.sourcePath()).toAbsolutePath().normalize();
                if (normalized.startsWith(imageDir)) {
                    used.add(normalized.toString());
                }
            } catch (Exception ignored) {
            }
        }

        try (var stream = Files.list(imageDir)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                try {
                    Path normalized = path.toAbsolutePath().normalize();
                    if (!used.contains(normalized.toString())) {
                        Files.deleteIfExists(normalized);
                    }
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    private static boolean loadBundledHudZip(CustomHudState state) {
        try (InputStream input = CustomHudStorage.class.getClassLoader().getResourceAsStream(BUNDLED_HUD_ZIP_RESOURCE)) {
            if (input == null) {
                return false;
            }

            Path hudDir = FILE_PATH.getParent();
            Files.createDirectories(hudDir);
            Path tempZip = hudDir.resolve("bundled_hud_import.zip");
            Files.copy(input, tempZip, StandardCopyOption.REPLACE_EXISTING);

            try {
                CustomHudExchange.ImportedHudData imported = CustomHudExchange.importFromZip(tempZip);
                state.replaceAll(
                        imported.elements(),
                        imported.canvasWidth(),
                        imported.canvasHeight(),
                        imported.hudX(),
                        imported.hudY(),
                        imported.hudScalePercent()
                );
                save(state);
                return true;
            } finally {
                try {
                    Files.deleteIfExists(tempZip);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
            return false;
        }
    }
}
