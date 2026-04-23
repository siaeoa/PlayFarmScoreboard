package by.siaroa.playfarmscoreboard.client.hud;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.security.MessageDigest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class CustomHudImageTextureManager {
    private static final String NAMESPACE = "playfarmscoreboard";
    private static final String ID_PREFIX = "custom_hud/image_";
    private static final int MAX_SOURCE_WIDTH = 1920;
    private static final int MAX_SOURCE_HEIGHT = 1080;
    private static final Path IMAGE_STORE_DIR = FabricLoader.getInstance().getConfigDir()
            .resolve("playfarmscoreboard")
            .resolve("images");

    private static final Map<String, TextureEntry> TEXTURE_CACHE = new HashMap<>();
    private static final Set<String> FAILED_PATHS = new HashSet<>();
    private static int nextTextureId;

    private CustomHudImageTextureManager() {
    }

    public static TextureInfo preload(Path path) {
        if (path == null) {
            return null;
        }
        Path normalized = path.toAbsolutePath().normalize();
        String managedPath = ensureManagedCopy(normalized);
        if (managedPath != null) {
            return getOrLoad(managedPath);
        }
        return getOrLoad(normalized.toString());
    }

    public static void drawImage(DrawContext context, String sourcePath, int x, int y, int width, int height, boolean clipOnResizeShrink) {
        TextureInfo info = getOrLoad(sourcePath);
        if (info == null) {
            context.fill(x, y, x + width, y + height, 0xAA131D26);
            context.drawStrokedRectangle(x, y, width, height, 0xFF8B5A5A);
            return;
        }

        int targetWidth = Math.max(1, width);
        int targetHeight = Math.max(1, height);
        int sourceWidth = Math.max(1, info.sourceWidth());
        int sourceHeight = Math.max(1, info.sourceHeight());

        // 여기선 타일/잘림 없이 원본 전체를 목표 크기로 스케일한다. 그냥 정직하게 크게/작게.
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                info.textureId(),
                x,
                y,
                0.0F,
                0.0F,
                targetWidth,
                targetHeight,
                sourceWidth,
                sourceHeight,
                sourceWidth,
                sourceHeight
        );
    }

    public static Path getImageStoreDir() {
        return IMAGE_STORE_DIR;
    }

    private static TextureInfo getOrLoad(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return null;
        }

        String normalizedPath;
        try {
            normalizedPath = Path.of(sourcePath).toAbsolutePath().normalize().toString();
        } catch (Exception ignored) {
            return null;
        }
        TextureEntry cached = TEXTURE_CACHE.get(normalizedPath);
        if (cached != null) {
            // 같은 이미지를 매번 다시 디코드하면 손해가 커서, 캐시 히트면 바로 반환.
            return cached.info();
        }
        if (FAILED_PATHS.contains(normalizedPath)) {
            // 이미 실패한 경로는 다시 시도하지 않는다. 로그 지옥보다 차라리 빠른 포기가 낫다.
            return null;
        }

        Path path = Path.of(normalizedPath);
        if (!Files.isRegularFile(path)) {
            FAILED_PATHS.add(normalizedPath);
            return null;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return null;
        }

        try {
            byte[] raw = Files.readAllBytes(path);
            NativeImage image = NativeImage.read(raw);
            image = clampToFhd(image);
            int width = image.getWidth();
            int height = image.getHeight();
            int id = nextTextureId++;
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "custom_hud_image_" + id, image);
            Identifier identifier = Identifier.of(
                    NAMESPACE,
                    ID_PREFIX + id + "_" + Integer.toUnsignedString(normalizedPath.hashCode())
            );
            client.getTextureManager().registerTexture(identifier, texture);

            TextureInfo info = new TextureInfo(normalizedPath, identifier, width, height);
            TEXTURE_CACHE.put(normalizedPath, new TextureEntry(info, texture));
            return info;
        } catch (Exception ignored) {
            // 깨진 파일이 들어와도 전체 HUD가 멈추면 안 된다. 실패 목록에 넣고 지나간다.
            FAILED_PATHS.add(normalizedPath);
            return null;
        }
    }

    private static String ensureManagedCopy(Path sourcePath) {
        try {
            if (!Files.isRegularFile(sourcePath)) {
                return null;
            }

            Path normalizedSource = sourcePath.toAbsolutePath().normalize();
            if (normalizedSource.startsWith(IMAGE_STORE_DIR)) {
                return normalizedSource.toString();
            }

            // 외부 경로 이미지는 관리 폴더로 복사해 두면, 원본 파일 이동돼도 덜 깨진다.
            Files.createDirectories(IMAGE_STORE_DIR);
            String hash = contentHashHex(normalizedSource);
            if (hash == null || hash.isBlank()) {
                return null;
            }

            String extension = fileExtension(normalizedSource.getFileName().toString());
            Path target = IMAGE_STORE_DIR.resolve(hash + extension);
            if (!Files.exists(target)) {
                Files.copy(normalizedSource, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target.toAbsolutePath().normalize().toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String contentHashHex(Path filePath) {
        try (InputStream in = Files.newInputStream(filePath)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                int unsigned = value & 0xFF;
                if (unsigned < 16) {
                    hex.append('0');
                }
                hex.append(Integer.toHexString(unsigned));
            }
            return hex.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String fileExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return ".png";
        }
        String extension = fileName.substring(dot).toLowerCase();
        if (extension.length() > 10) {
            return ".png";
        }
        for (int i = 1; i < extension.length(); i++) {
            char ch = extension.charAt(i);
            boolean letter = ch >= 'a' && ch <= 'z';
            boolean number = ch >= '0' && ch <= '9';
            if (!letter && !number) {
                return ".png";
            }
        }
        return extension;
    }

    private static NativeImage clampToFhd(NativeImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= MAX_SOURCE_WIDTH && height <= MAX_SOURCE_HEIGHT) {
            return source;
        }

        // 슬슬 뇌가 멈추는데, 큰 이미지는 여기서 줄여야 메모리가 안 터진다.
        double scale = Math.min(
                MAX_SOURCE_WIDTH / (double) Math.max(1, width),
                MAX_SOURCE_HEIGHT / (double) Math.max(1, height)
        );
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        NativeImage resized = new NativeImage(targetWidth, targetHeight, false);
        source.resizeSubRectTo(0, 0, width, height, resized);
        source.close();
        return resized;
    }

    public record TextureInfo(String sourcePath, Identifier textureId, int sourceWidth, int sourceHeight) {
    }

    private record TextureEntry(TextureInfo info, NativeImageBackedTexture texture) {
    }
}
