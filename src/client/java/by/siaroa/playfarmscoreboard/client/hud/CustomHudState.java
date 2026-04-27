package by.siaroa.playfarmscoreboard.client.hud;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class CustomHudState {
    private static final int DEFAULT_CANVAS_WIDTH = 320;
    private static final int DEFAULT_CANVAS_HEIGHT = 180;
    private static final int DEFAULT_HUD_X = 24;
    private static final int DEFAULT_HUD_Y = 24;
    public static final int DEFAULT_HUD_SCALE_PERCENT = 100;
    public static final int MIN_HUD_SCALE_PERCENT = 50;
    public static final int MAX_HUD_SCALE_PERCENT = 300;
    private static final int HISTORY_LIMIT = 80;

    private final List<HudElement> elements = new ArrayList<>();
    private final Deque<Snapshot> undoStack = new ArrayDeque<>();
    private final Deque<Snapshot> redoStack = new ArrayDeque<>();

    private int canvasWidth = DEFAULT_CANVAS_WIDTH;
    private int canvasHeight = DEFAULT_CANVAS_HEIGHT;
    private int hudX = DEFAULT_HUD_X;
    private int hudY = DEFAULT_HUD_Y;
    private int hudScalePercent = DEFAULT_HUD_SCALE_PERCENT;

    public CustomHudState() {
        resetToDefault();
    }

    public List<HudElement> getElements() {
        return List.copyOf(elements);
    }

    public int getCanvasWidth() {
        return canvasWidth;
    }

    public int getCanvasHeight() {
        return canvasHeight;
    }

    public int getHudX() {
        return hudX;
    }

    public int getHudY() {
        return hudY;
    }

    public int getHudScalePercent() {
        return hudScalePercent;
    }

    public float getHudScale() {
        return hudScalePercent / 100.0F;
    }

    public void addElement(HudElement element) {
        mutate(() -> elements.add(element));
    }

    public void addElements(List<HudElement> newElements) {
        if (newElements == null || newElements.isEmpty()) {
            return;
        }
        mutate(() -> elements.addAll(newElements));
    }

    public void addElementAtBottom(HudElement element) {
        mutate(() -> elements.add(0, element));
    }

    public void clear() {
        mutate(elements::clear);
    }

    public void setHudPosition(int x, int y) {
        if (x == hudX && y == hudY) {
            return;
        }
        mutate(() -> {
            hudX = x;
            hudY = y;
        });
    }

    public void setHudPositionDirect(int x, int y) {
        hudX = x;
        hudY = y;
    }

    public void setHudScalePercent(int scalePercent) {
        int normalized = normalizeHudScalePercent(scalePercent);
        if (normalized == hudScalePercent) {
            return;
        }
        mutate(() -> hudScalePercent = normalized);
    }

    public void setHudScalePercentDirect(int scalePercent) {
        hudScalePercent = normalizeHudScalePercent(scalePercent);
    }

    public HudElement.Bounds getContentBounds() {
        if (elements.isEmpty()) {
            return new HudElement.Bounds(0, 0, Math.max(0, canvasWidth - 1), Math.max(0, canvasHeight - 1));
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (HudElement element : elements) {
            HudElement.Bounds bounds = element.bounds();
            minX = Math.min(minX, bounds.minX());
            minY = Math.min(minY, bounds.minY());
            maxX = Math.max(maxX, bounds.maxX());
            maxY = Math.max(maxY, bounds.maxY());
        }

        return new HudElement.Bounds(minX, minY, maxX, maxY);
    }

    public int findTopElementIndexAt(int localX, int localY) {
        for (int i = elements.size() - 1; i >= 0; i--) {
            HudElement.Bounds bounds = elements.get(i).bounds();
            if (localX >= bounds.minX() && localX <= bounds.maxX() && localY >= bounds.minY() && localY <= bounds.maxY()) {
                return i;
            }
        }
        return -1;
    }

    public boolean removeElementAt(int index) {
        if (!isValidIndex(index)) {
            return false;
        }
        mutate(() -> elements.remove(index));
        return true;
    }

    public boolean removeElements(List<Integer> indices) {
        List<Integer> normalized = normalizeIndices(indices);
        if (normalized.isEmpty()) {
            return false;
        }

        mutate(() -> {
            for (int i = normalized.size() - 1; i >= 0; i--) {
                elements.remove((int) normalized.get(i));
            }
        });
        return true;
    }

    public boolean eraseAt(int localX, int localY, int radius) {
        int eraserRadius = Math.max(1, radius);

        // 위 레이어부터 지우는 게 그림판 감성 그대로다. 밑에서 지워지면 사용자만 억울하다.
        for (int i = elements.size() - 1; i >= 0; i--) {
            HudElement element = elements.get(i);
            HudElement.Bounds bounds = element.bounds();
            if (localX < bounds.minX() - eraserRadius || localX > bounds.maxX() + eraserRadius
                    || localY < bounds.minY() - eraserRadius || localY > bounds.maxY() + eraserRadius) {
                continue;
            }

            if (element instanceof HudElement.BrushStroke stroke) {
                if (eraseStrokePoints(i, stroke, localX, localY, eraserRadius)) {
                    return true;
                }
                continue;
            }

            if (element instanceof HudElement.Line line) {
                List<HudElement.HudPoint> linePoints = rasterizeLine(line.x1(), line.y1(), line.x2(), line.y2());
                int lineRadius = eraserRadius + Math.max(0, line.size() / 3);
                if (eraseStrokePoints(i, linePoints, line.size(), line.color(), localX, localY, lineRadius)) {
                    return true;
                }
                continue;
            }

            int index = i;
            mutate(() -> elements.remove(index));
            return true;
        }

        return false;
    }

    public int moveElement(int from, int to) {
        if (!isValidIndex(from)) {
            return -1;
        }
        int targetIndex = Math.max(0, Math.min(elements.size() - 1, to));
        if (targetIndex == from) {
            return from;
        }

        mutate(() -> {
            HudElement element = elements.remove(from);
            elements.add(targetIndex, element);
        });
        return targetIndex;
    }

    public boolean translateElement(int index, int deltaX, int deltaY) {
        if (!isValidIndex(index)) {
            return false;
        }
        if (deltaX == 0 && deltaY == 0) {
            return true;
        }

        HudElement translated = translateElementBy(elements.get(index), deltaX, deltaY);
        if (translated == null) {
            return false;
        }

        mutate(() -> elements.set(index, translated));
        return true;
    }

    public boolean translateElements(List<Integer> indices, int deltaX, int deltaY) {
        List<Integer> normalized = normalizeIndices(indices);
        if (normalized.isEmpty()) {
            return false;
        }
        if (deltaX == 0 && deltaY == 0) {
            return true;
        }

        List<HudElement> translatedElements = new ArrayList<>(normalized.size());
        for (int index : normalized) {
            HudElement translated = translateElementBy(elements.get(index), deltaX, deltaY);
            if (translated == null) {
                return false;
            }
            translatedElements.add(translated);
        }

        mutate(() -> {
            for (int i = 0; i < normalized.size(); i++) {
                elements.set(normalized.get(i), translatedElements.get(i));
            }
        });
        return true;
    }

    public boolean updateTextElement(int index, String newText) {
        if (!isValidIndex(index)) {
            return false;
        }
        HudElement element = elements.get(index);
        if (!(element instanceof HudElement.TextLabel textLabel)) {
            return false;
        }

        String targetText = newText == null ? "" : newText;
        if (targetText.equals(textLabel.text())) {
            return true;
        }

        mutate(() -> elements.set(index, new HudElement.TextLabel(
                textLabel.x(),
                textLabel.y(),
                targetText,
                textLabel.color(),
                textLabel.fontSize()
        )));
        return true;
    }

    public boolean updateTextElementColor(int index, int newColor) {
        if (!isValidIndex(index)) {
            return false;
        }
        HudElement element = elements.get(index);
        if (!(element instanceof HudElement.TextLabel textLabel)) {
            return false;
        }
        if (textLabel.color() == newColor) {
            return true;
        }

        mutate(() -> elements.set(index, new HudElement.TextLabel(
                textLabel.x(),
                textLabel.y(),
                textLabel.text(),
                newColor,
                textLabel.fontSize()
        )));
        return true;
    }

    public boolean updateTextElementFontSize(int index, int newFontSize) {
        if (!isValidIndex(index)) {
            return false;
        }
        HudElement element = elements.get(index);
        if (!(element instanceof HudElement.TextLabel textLabel)) {
            return false;
        }

        int normalized = HudRenderUtil.clamp(
                newFontSize,
                HudElement.TextLabel.MIN_FONT_SIZE,
                HudElement.TextLabel.MAX_FONT_SIZE
        );
        if (textLabel.fontSize() == normalized) {
            return true;
        }

        mutate(() -> elements.set(index, new HudElement.TextLabel(
                textLabel.x(),
                textLabel.y(),
                textLabel.text(),
                textLabel.color(),
                normalized
        )));
        return true;
    }

    public boolean resizeElementBounds(int index, int minX, int minY, int maxX, int maxY) {
        if (!isValidIndex(index)) {
            return false;
        }

        int left = Math.max(0, Math.min(minX, maxX));
        int top = Math.max(0, Math.min(minY, maxY));
        int right = Math.max(left, Math.max(minX, maxX));
        int bottom = Math.max(top, Math.max(minY, maxY));

        HudElement element = elements.get(index);
        if (element instanceof HudElement.Rectangle rectangle) {
            if (rectangle.x1() == left && rectangle.y1() == top && rectangle.x2() == right && rectangle.y2() == bottom) {
                return true;
            }
            mutate(() -> elements.set(index, new HudElement.Rectangle(left, top, right, bottom, rectangle.color())));
            return true;
        }

        if (element instanceof HudElement.ImageSprite imageSprite) {
            int width = Math.max(1, right - left + 1);
            int height = Math.max(1, bottom - top + 1);
            if (imageSprite.x() == left && imageSprite.y() == top && imageSprite.width() == width && imageSprite.height() == height) {
                return true;
            }
            mutate(() -> elements.set(index, new HudElement.ImageSprite(left, top, width, height, imageSprite.sourcePath(), imageSprite.clipOnResizeShrink())));
            return true;
        }

        return false;
    }

    public boolean resizeImageElement(int index, int minX, int minY, int maxX, int maxY, boolean clipOnResizeShrink) {
        if (!isValidIndex(index)) {
            return false;
        }

        HudElement element = elements.get(index);
        if (!(element instanceof HudElement.ImageSprite imageSprite)) {
            return false;
        }

        int left = Math.max(0, Math.min(minX, maxX));
        int top = Math.max(0, Math.min(minY, maxY));
        int right = Math.max(left, Math.max(minX, maxX));
        int bottom = Math.max(top, Math.max(minY, maxY));
        int width = Math.max(1, right - left + 1);
        int height = Math.max(1, bottom - top + 1);

        // 좌표가 뒤집혀 들어와도 여기서 정리해 둔다. 일단 살아남는 게 중요하다.
        if (imageSprite.x() == left
                && imageSprite.y() == top
                && imageSprite.width() == width
                && imageSprite.height() == height
                && imageSprite.clipOnResizeShrink() == clipOnResizeShrink) {
            return true;
        }

        mutate(() -> elements.set(index, new HudElement.ImageSprite(
                left,
                top,
                width,
                height,
                imageSprite.sourcePath(),
                clipOnResizeShrink
        )));
        return true;
    }

    public boolean undo() {
        if (undoStack.isEmpty()) {
            return false;
        }

        redoStack.push(captureSnapshot());
        restoreSnapshot(undoStack.pop());
        return true;
    }

    public boolean redo() {
        if (redoStack.isEmpty()) {
            return false;
        }

        undoStack.push(captureSnapshot());
        restoreSnapshot(redoStack.pop());
        return true;
    }

    public void replaceAll(
            List<HudElement> newElements,
            int newCanvasWidth,
            int newCanvasHeight,
            int newHudX,
            int newHudY,
            int newHudScalePercent
    ) {
        // 통째로 갈아끼울 땐 히스토리도 같이 비우자. 안 그러면 undo가 과거 세계선을 소환한다.
        elements.clear();
        elements.addAll(newElements);
        canvasWidth = Math.max(DEFAULT_CANVAS_WIDTH, newCanvasWidth);
        canvasHeight = Math.max(DEFAULT_CANVAS_HEIGHT, newCanvasHeight);
        hudX = newHudX;
        hudY = newHudY;
        hudScalePercent = normalizeHudScalePercent(newHudScalePercent);
        recomputeCanvasSize();
        clearHistory();
    }

    public void resetToDefault() {
        // 처음 들어온 사람도 바로 뭐가 뭔지 보이게 기본 샘플을 빵빵하게 깔아 둔다.
        elements.clear();
        canvasWidth = DEFAULT_CANVAS_WIDTH;
        canvasHeight = DEFAULT_CANVAS_HEIGHT;
        hudX = DEFAULT_HUD_X;
        hudY = DEFAULT_HUD_Y;
        hudScalePercent = DEFAULT_HUD_SCALE_PERCENT;

        int panelX = 8;
        int panelY = 8;
        int panelWidth = 198;
        int panelHeight = 98;
        int titleBarHeight = 16;
        int labelX = panelX + 8;
        int valueX = panelX + 58;

        elements.add(new HudElement.FillRect(panelX, panelY, panelWidth, panelHeight, HudRenderUtil.argb(150, 0, 0, 0)));
        elements.add(new HudElement.FillRect(panelX, panelY, panelWidth, titleBarHeight, HudRenderUtil.argb(178, 20, 34, 48)));
        elements.add(new HudElement.TextLabel(panelX + 8, panelY + 4, "기초 HUD", HudRenderUtil.argb(255, 232, 244, 241)));

        elements.add(new HudElement.TextLabel(labelX, panelY + 21, "돈", HudRenderUtil.argb(255, 211, 225, 220)));
        elements.add(new HudElement.TextLabel(valueX, panelY + 21, "{Money}", HudRenderUtil.argb(255, 255, 255, 255)));

        elements.add(new HudElement.TextLabel(labelX, panelY + 33, "캐시", HudRenderUtil.argb(255, 211, 225, 220)));
        elements.add(new HudElement.TextLabel(valueX, panelY + 33, "{cash}", HudRenderUtil.argb(255, 255, 255, 255)));

        elements.add(new HudElement.TextLabel(labelX, panelY + 45, "채팅", HudRenderUtil.argb(255, 211, 225, 220)));
        elements.add(new HudElement.TextLabel(valueX, panelY + 45, "{chat}", HudRenderUtil.argb(255, 255, 255, 255)));

        elements.add(new HudElement.TextLabel(labelX, panelY + 57, "채널", HudRenderUtil.argb(255, 211, 225, 220)));
        elements.add(new HudElement.TextLabel(valueX, panelY + 57, "{channel}", HudRenderUtil.argb(255, 255, 255, 255)));

        elements.add(new HudElement.TextLabel(labelX, panelY + 69, "플라이", HudRenderUtil.argb(255, 211, 225, 220)));
        elements.add(new HudElement.TextLabel(valueX, panelY + 69, "{fly}", HudRenderUtil.argb(255, 255, 255, 255)));

        elements.add(new HudElement.TextLabel(labelX, panelY + 81, "자동심기", HudRenderUtil.argb(255, 211, 225, 220)));
        elements.add(new HudElement.TextLabel(valueX, panelY + 81, "{auto plant}", HudRenderUtil.argb(255, 255, 255, 255)));

        recomputeCanvasSize();
        clearHistory();
    }

    private void mutate(Runnable mutator) {
        // 편집 도중에는 "실수해도 복구 가능"이 핵심이다. 그래서 매번 스냅샷 먼저.
        undoStack.push(captureSnapshot());
        trimUndoHistory();
        mutator.run();
        recomputeCanvasSize();
        redoStack.clear();
    }

    private boolean eraseStrokePoints(int index, HudElement.BrushStroke stroke, int localX, int localY, int radius) {
        return eraseStrokePoints(index, stroke.points(), stroke.size(), stroke.color(), localX, localY, radius);
    }

    private HudElement translateElementBy(HudElement element, int deltaX, int deltaY) {
        if (element instanceof HudElement.BrushStroke stroke) {
            List<HudElement.HudPoint> movedPoints = new ArrayList<>(stroke.points().size());
            for (HudElement.HudPoint point : stroke.points()) {
                movedPoints.add(new HudElement.HudPoint(
                        Math.max(0, point.x() + deltaX),
                        Math.max(0, point.y() + deltaY)
                ));
            }
            return new HudElement.BrushStroke(movedPoints, stroke.size(), stroke.color());
        }

        if (element instanceof HudElement.FillRect fillRect) {
            return new HudElement.FillRect(
                    Math.max(0, fillRect.x() + deltaX),
                    Math.max(0, fillRect.y() + deltaY),
                    fillRect.width(),
                    fillRect.height(),
                    fillRect.color()
            );
        }

        if (element instanceof HudElement.Rectangle rectangle) {
            return new HudElement.Rectangle(
                    Math.max(0, rectangle.x1() + deltaX),
                    Math.max(0, rectangle.y1() + deltaY),
                    Math.max(0, rectangle.x2() + deltaX),
                    Math.max(0, rectangle.y2() + deltaY),
                    rectangle.color()
            );
        }

        if (element instanceof HudElement.Circle circle) {
            return new HudElement.Circle(
                    Math.max(0, circle.x1() + deltaX),
                    Math.max(0, circle.y1() + deltaY),
                    Math.max(0, circle.x2() + deltaX),
                    Math.max(0, circle.y2() + deltaY),
                    circle.color()
            );
        }

        if (element instanceof HudElement.Line line) {
            return new HudElement.Line(
                    Math.max(0, line.x1() + deltaX),
                    Math.max(0, line.y1() + deltaY),
                    Math.max(0, line.x2() + deltaX),
                    Math.max(0, line.y2() + deltaY),
                    line.size(),
                    line.color()
            );
        }

        if (element instanceof HudElement.TextLabel textLabel) {
            return new HudElement.TextLabel(
                    Math.max(0, textLabel.x() + deltaX),
                    Math.max(0, textLabel.y() + deltaY),
                    textLabel.text(),
                    textLabel.color(),
                    textLabel.fontSize()
            );
        }

        if (element instanceof HudElement.ImageSprite imageSprite) {
            return new HudElement.ImageSprite(
                    Math.max(0, imageSprite.x() + deltaX),
                    Math.max(0, imageSprite.y() + deltaY),
                    imageSprite.width(),
                    imageSprite.height(),
                    imageSprite.sourcePath(),
                    imageSprite.clipOnResizeShrink()
            );
        }

        return null;
    }

    private boolean eraseStrokePoints(
            int index,
            List<HudElement.HudPoint> sourcePoints,
            int size,
            int color,
            int localX,
            int localY,
            int radius
    ) {
        int radiusSquared = radius * radius;
        List<HudElement.HudPoint> keptPoints = new ArrayList<>(sourcePoints.size());

        for (HudElement.HudPoint point : sourcePoints) {
            int deltaX = point.x() - localX;
            int deltaY = point.y() - localY;
            int distanceSquared = (deltaX * deltaX) + (deltaY * deltaY);
            if (distanceSquared > radiusSquared) {
                keptPoints.add(point);
            }
        }

        if (keptPoints.size() == sourcePoints.size()) {
            return false;
        }

        mutate(() -> {
            elements.remove(index);
            if (!keptPoints.isEmpty()) {
                elements.add(index, new HudElement.BrushStroke(keptPoints, size, color));
            }
        });
        return true;
    }

    private List<HudElement.HudPoint> rasterizeLine(int x1, int y1, int x2, int y2) {
        List<HudElement.HudPoint> points = new ArrayList<>();

        int currentX = x1;
        int currentY = y1;
        int deltaX = Math.abs(x2 - x1);
        int stepX = x1 < x2 ? 1 : -1;
        int deltaY = -Math.abs(y2 - y1);
        int stepY = y1 < y2 ? 1 : -1;
        int error = deltaX + deltaY;

        // 오래된 알고리즘이지만 여전히 튼튼하다. 이런 건 괜히 새로 짜다 사고 난다.
        while (true) {
            points.add(new HudElement.HudPoint(currentX, currentY));
            if (currentX == x2 && currentY == y2) {
                break;
            }

            int doubledError = 2 * error;
            if (doubledError >= deltaY) {
                error += deltaY;
                currentX += stepX;
            }
            if (doubledError <= deltaX) {
                error += deltaX;
                currentY += stepY;
            }
        }

        return points;
    }

    private Snapshot captureSnapshot() {
        return new Snapshot(new ArrayList<>(elements), canvasWidth, canvasHeight, hudX, hudY, hudScalePercent);
    }

    private void restoreSnapshot(Snapshot snapshot) {
        elements.clear();
        elements.addAll(snapshot.elements());
        canvasWidth = snapshot.canvasWidth();
        canvasHeight = snapshot.canvasHeight();
        hudX = snapshot.hudX();
        hudY = snapshot.hudY();
        hudScalePercent = snapshot.hudScalePercent();
    }

    private void recomputeCanvasSize() {
        int computedWidth = DEFAULT_CANVAS_WIDTH;
        int computedHeight = DEFAULT_CANVAS_HEIGHT;

        for (HudElement element : elements) {
            HudElement.Bounds bounds = element.bounds();
            computedWidth = Math.max(computedWidth, bounds.maxX() + 24);
            computedHeight = Math.max(computedHeight, bounds.maxY() + 24);
        }

        canvasWidth = computedWidth;
        canvasHeight = computedHeight;
    }

    private boolean isValidIndex(int index) {
        return index >= 0 && index < elements.size();
    }

    private List<Integer> normalizeIndices(List<Integer> indices) {
        if (indices == null || indices.isEmpty() || elements.isEmpty()) {
            return List.of();
        }

        boolean[] included = new boolean[elements.size()];
        for (Integer index : indices) {
            if (index == null) {
                continue;
            }
            if (index >= 0 && index < elements.size()) {
                included[index] = true;
            }
        }

        List<Integer> normalized = new ArrayList<>();
        for (int i = 0; i < included.length; i++) {
            if (included[i]) {
                normalized.add(i);
            }
        }
        return normalized;
    }

    private void trimUndoHistory() {
        while (undoStack.size() > HISTORY_LIMIT) {
            undoStack.removeLast();
        }
    }

    private void clearHistory() {
        undoStack.clear();
        redoStack.clear();
    }

    private static int normalizeHudScalePercent(int value) {
        return HudRenderUtil.clamp(value, MIN_HUD_SCALE_PERCENT, MAX_HUD_SCALE_PERCENT);
    }

    private record Snapshot(
            List<HudElement> elements,
            int canvasWidth,
            int canvasHeight,
            int hudX,
            int hudY,
            int hudScalePercent
    ) {
    }
}
