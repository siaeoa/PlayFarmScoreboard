package by.siaroa.playfarmscoreboard.client.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class CustomHudFlyStorage {
    private static final Path FILE_PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("playfarmscoreboard")
            .resolve("fly_cache.json");

    private static boolean loaded;
    private static boolean dirty;
    private static String cachedFly = "";
    private static String cachedAutoPlant = "";

    private CustomHudFlyStorage() {
    }

    public static String getCachedFly() {
        ensureLoaded();
        return cachedFly;
    }

    public static void updateFly(String flyText) {
        if (flyText == null || flyText.isBlank()) {
            return;
        }

        ensureLoaded();
        if (flyText.equals(cachedFly)) {
            return;
        }

        // 값이 달라졌을 때만 dirty 처리. 디스크 IO는 필요한 순간에만.
        cachedFly = flyText;
        dirty = true;
    }

    public static String getCachedAutoPlant() {
        ensureLoaded();
        return cachedAutoPlant;
    }

    public static void updateAutoPlant(String autoPlantText) {
        if (autoPlantText == null || autoPlantText.isBlank()) {
            return;
        }

        ensureLoaded();
        if (autoPlantText.equals(cachedAutoPlant)) {
            return;
        }

        // 자동심기 카운트도 fly랑 같은 규칙으로 캐싱: 단순함이 오래 간다.
        cachedAutoPlant = autoPlantText;
        dirty = true;
    }

    public static void flush() {
        ensureLoaded();
        if (!dirty) {
            return;
        }
        if (save()) {
            dirty = false;
        }
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        // 지연 로딩으로 시작 속도 챙기고, 실제 필요할 때만 파일 접근.
        loaded = true;

        if (!Files.exists(FILE_PATH)) {
            return;
        }

        try {
            String raw = Files.readString(FILE_PATH, StandardCharsets.UTF_8);
            JsonObject object = JsonParser.parseString(raw).getAsJsonObject();
            if (object.has("fly")) {
                cachedFly = object.get("fly").getAsString();
            }
            if (object.has("autoPlant")) {
                cachedAutoPlant = object.get("autoPlant").getAsString();
            }
        } catch (Exception ignored) {
            // 파싱 실패면 과감히 초기화. 여기서 버티려다 더 큰 혼돈을 만든 적이 있다.
            cachedFly = "";
            cachedAutoPlant = "";
            dirty = false;
        }
    }

    private static boolean save() {
        try {
            // 구현이 어려운 건 내일로 미루고, 저장 원자성만큼은 지금 확실히 지킨다.
            Files.createDirectories(FILE_PATH.getParent());
            Path tempFile = FILE_PATH.resolveSibling(FILE_PATH.getFileName() + ".tmp");
            JsonObject object = new JsonObject();
            object.addProperty("fly", cachedFly);
            object.addProperty("autoPlant", cachedAutoPlant);
            byte[] encoded = object.toString().getBytes(StandardCharsets.UTF_8);

            try (FileChannel channel = FileChannel.open(
                    tempFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            )) {
                channel.write(ByteBuffer.wrap(encoded));
                channel.force(true);
            }

            try {
                Files.move(tempFile, FILE_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempFile, FILE_PATH, StandardCopyOption.REPLACE_EXISTING);
            }

            try (FileChannel channel = FileChannel.open(FILE_PATH, StandardOpenOption.WRITE)) {
                channel.force(true);
            }

            try (FileChannel directoryChannel = FileChannel.open(FILE_PATH.getParent(), StandardOpenOption.READ)) {
                directoryChannel.force(true);
            } catch (Exception ignored) {
            }
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }
}
