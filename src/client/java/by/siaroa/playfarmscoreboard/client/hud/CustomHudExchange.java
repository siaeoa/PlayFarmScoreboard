package by.siaroa.playfarmscoreboard.client.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class CustomHudExchange {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STATE_ENTRY = "custom_hud.json";
    private static final String IMAGES_DIR = "images/";

    private CustomHudExchange() {
    }

    public static void exportToZip(CustomHudState state, Path targetZipPath) throws IOException {
        if (targetZipPath == null) {
            throw new IOException("Invalid target path");
        }
        Path absoluteTarget = targetZipPath.toAbsolutePath().normalize();
        Path parent = absoluteTarget.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Map<String, String> imageEntryBySource = buildImageEntryMap(state.getElements());
        JsonObject root = encodeState(state, imageEntryBySource);
        byte[] rootBytes = GSON.toJson(root).getBytes(StandardCharsets.UTF_8);

        try (OutputStream out = Files.newOutputStream(absoluteTarget);
             ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(STATE_ENTRY));
            zip.write(rootBytes);
            zip.closeEntry();

            for (Map.Entry<String, String> entry : imageEntryBySource.entrySet()) {
                Path source = Path.of(entry.getKey()).toAbsolutePath().normalize();
                zip.putNextEntry(new ZipEntry(entry.getValue()));
                Files.copy(source, zip);
                zip.closeEntry();
            }
        }
    }

    public static ImportedHudData importFromZip(Path sourceZipPath) throws IOException {
        if (sourceZipPath == null || !Files.isRegularFile(sourceZipPath)) {
            throw new IOException("ZIP file not found");
        }

        Path imageDir = CustomHudImageTextureManager.getImageStoreDir();
        Files.createDirectories(imageDir);

        String jsonRaw = null;
        Map<String, String> extractedImages = new HashMap<>();

        try (InputStream in = Files.newInputStream(sourceZipPath.toAbsolutePath().normalize());
             ZipInputStream zip = new ZipInputStream(in, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }

                String entryName = normalizeEntryName(entry.getName());
                if (STATE_ENTRY.equals(entryName)) {
                    jsonRaw = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    zip.closeEntry();
                    continue;
                }

                if (!entryName.startsWith(IMAGES_DIR)) {
                    zip.closeEntry();
                    continue;
                }

                String fileName = sanitizeFileName(entryName.substring(IMAGES_DIR.length()));
                if (fileName.isBlank()) {
                    zip.closeEntry();
                    continue;
                }

                Path outputPath = uniqueImagePath(imageDir, fileName);
                Files.copy(zip, outputPath);
                extractedImages.put(entryName, outputPath.toAbsolutePath().normalize().toString());
                zip.closeEntry();
            }
        }

        if (jsonRaw == null || jsonRaw.isBlank()) {
            throw new IOException("ZIP does not contain custom_hud.json");
        }

        JsonObject root = JsonParser.parseString(jsonRaw).getAsJsonObject();
        int canvasWidth = getInt(root, "canvasWidth", 320);
        int canvasHeight = getInt(root, "canvasHeight", 180);
        int hudX = getInt(root, "hudX", 24);
        int hudY = getInt(root, "hudY", 24);
        int hudScale = getInt(root, "hudScale", CustomHudState.DEFAULT_HUD_SCALE_PERCENT);

        List<HudElement> elements = new ArrayList<>();
        if (root.has("elements") && root.get("elements").isJsonArray()) {
            JsonArray array = root.getAsJsonArray("elements");
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }
                HudElement decoded = decodeElement(element.getAsJsonObject(), extractedImages);
                if (decoded != null) {
                    elements.add(decoded);
                }
            }
        }

        if (elements.isEmpty()) {
            throw new IOException("ZIP does not contain valid HUD elements");
        }

        return new ImportedHudData(elements, canvasWidth, canvasHeight, hudX, hudY, hudScale);
    }

    private static Map<String, String> buildImageEntryMap(List<HudElement> elements) throws IOException {
        Map<String, String> imageEntries = new LinkedHashMap<>();
        Set<String> usedEntryNames = new HashSet<>();

        for (HudElement element : elements) {
            if (!(element instanceof HudElement.ImageSprite image)) {
                continue;
            }

            Path source = Path.of(image.sourcePath()).toAbsolutePath().normalize();
            if (!Files.isRegularFile(source)) {
                throw new IOException("Missing image file: " + source);
            }

            String sourceKey = source.toString();
            if (imageEntries.containsKey(sourceKey)) {
                continue;
            }

            String baseName = sanitizeFileName(source.getFileName().toString());
            if (baseName.isBlank()) {
                baseName = "image.png";
            }
            String entryName = uniqueEntryName(IMAGES_DIR + baseName, usedEntryNames);
            usedEntryNames.add(entryName);
            imageEntries.put(sourceKey, entryName);
        }

        return imageEntries;
    }

    private static JsonObject encodeState(CustomHudState state, Map<String, String> imageEntryBySource) {
        JsonObject root = new JsonObject();
        root.addProperty("canvasWidth", state.getCanvasWidth());
        root.addProperty("canvasHeight", state.getCanvasHeight());
        root.addProperty("hudX", state.getHudX());
        root.addProperty("hudY", state.getHudY());
        root.addProperty("hudScale", state.getHudScalePercent());

        JsonArray array = new JsonArray();
        for (HudElement element : state.getElements()) {
            JsonObject encoded = encodeElement(element, imageEntryBySource);
            if (encoded != null) {
                array.add(encoded);
            }
        }
        root.add("elements", array);
        return root;
    }

    private static JsonObject encodeElement(HudElement element, Map<String, String> imageEntryBySource) {
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
            String sourceKey = Path.of(imageSprite.sourcePath()).toAbsolutePath().normalize().toString();
            object.addProperty("sourcePath", imageEntryBySource.getOrDefault(sourceKey, imageSprite.sourcePath()));
            object.addProperty("clipOnResizeShrink", imageSprite.clipOnResizeShrink());
            return object;
        }

        return null;
    }

    private static HudElement decodeElement(JsonObject object, Map<String, String> extractedImages) {
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
                    resolveImagePath(getString(object, "sourcePath", ""), extractedImages),
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

    private static String resolveImagePath(String storedPath, Map<String, String> extractedImages) {
        if (storedPath == null || storedPath.isBlank()) {
            return "";
        }
        String normalized = normalizeEntryName(storedPath);
        String direct = extractedImages.get(normalized);
        if (direct != null) {
            return direct;
        }
        return storedPath;
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

    private static String uniqueEntryName(String candidate, Set<String> used) {
        if (!used.contains(candidate)) {
            return candidate;
        }
        int dot = candidate.lastIndexOf('.');
        String prefix = dot >= 0 ? candidate.substring(0, dot) : candidate;
        String suffix = dot >= 0 ? candidate.substring(dot) : "";
        int index = 1;
        while (true) {
            String next = prefix + "_" + index + suffix;
            if (!used.contains(next)) {
                return next;
            }
            index++;
        }
    }

    private static Path uniqueImagePath(Path imageDir, String fileName) {
        Path candidate = imageDir.resolve(fileName).toAbsolutePath().normalize();
        if (!Files.exists(candidate)) {
            return candidate;
        }
        int dot = fileName.lastIndexOf('.');
        String prefix = dot >= 0 ? fileName.substring(0, dot) : fileName;
        String suffix = dot >= 0 ? fileName.substring(dot) : "";
        int index = 1;
        while (true) {
            Path next = imageDir.resolve(prefix + "_" + index + suffix).toAbsolutePath().normalize();
            if (!Files.exists(next)) {
                return next;
            }
            index++;
        }
    }

    private static String sanitizeFileName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = normalizeEntryName(value);
        int slash = normalized.lastIndexOf('/');
        String fileName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        StringBuilder out = new StringBuilder(fileName.length());
        for (int i = 0; i < fileName.length(); i++) {
            char ch = fileName.charAt(i);
            boolean letter = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
            boolean digit = ch >= '0' && ch <= '9';
            boolean safe = ch == '.' || ch == '_' || ch == '-';
            out.append(letter || digit || safe ? ch : '_');
        }
        String cleaned = out.toString();
        return cleaned.isBlank() ? "image.png" : cleaned;
    }

    private static String normalizeEntryName(String value) {
        return value.replace('\\', '/');
    }

    public record ImportedHudData(
            List<HudElement> elements,
            int canvasWidth,
            int canvasHeight,
            int hudX,
            int hudY,
            int hudScalePercent
    ) {
    }
}
