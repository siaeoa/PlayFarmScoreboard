package by.siaroa.playfarmscoreboard.client.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import by.siaroa.playfarmscoreboard.client.GuiScaleCompat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.Desktop;
import java.awt.FileDialog;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class CustomHudEditorScreen extends Screen {
    private static final int TOP_MARGIN = 10;
    private static final int SIDE_MARGIN = 10;
    private static final int MAX_IMAGE_PRESET = 220;
    private static final int RESIZE_HANDLE_SIZE = 4;
    private static final int RESIZE_HANDLE_HIT = 2;
    private static final int MAX_TEXT_LENGTH = 80;
    private static final int ARROW_MOVE_STEP = 1;
    private static final int ARROW_MOVE_STEP_FAST = 8;
    private static final long ARROW_REPEAT_INITIAL_DELAY_MS = 180L;
    private static final long ARROW_REPEAT_INTERVAL_MS = 34L;
    private static final long BUTTON_TOOLTIP_DELAY_MS = 1500L;
    private static final int HUD_SCALE_STEP_PERCENT = 5;
    private static final String EXCHANGE_ZIP_NAME = "custom_hud.zip";
    private static final String CLIPBOARD_ELEMENTS_PREFIX = "PFSHUD_ELEMENTS:";
    private static final int PASTE_OFFSET_STEP = 12;
    private static final int PASTE_OFFSET_WRAP = 8;

    private final CustomHudState state;
    private final Map<HudTool, UiRect> toolButtons = new EnumMap<>(HudTool.class);
    private final Map<SliderType, UiRect> sliders = new EnumMap<>(SliderType.class);
    private final Map<LayerAction, UiRect> layerButtons = new EnumMap<>(LayerAction.class);
    private final List<HudElement.HudPoint> activeBrushPoints = new ArrayList<>();
    private final List<Integer> selectedElementIndices = new ArrayList<>();

    private UiRect topBarRect = UiRect.EMPTY;
    private UiRect leftPanelRect = UiRect.EMPTY;
    private UiRect rightPanelRect = UiRect.EMPTY;
    private UiRect canvasRect = UiRect.EMPTY;

    private UiRect undoButtonRect = UiRect.EMPTY;
    private UiRect redoButtonRect = UiRect.EMPTY;
    private UiRect clearButtonRect = UiRect.EMPTY;
    private UiRect closeButtonRect = UiRect.EMPTY;
    private UiRect exportButtonRect = UiRect.EMPTY;
    private UiRect importButtonRect = UiRect.EMPTY;
    private UiRect hudMoveButtonRect = UiRect.EMPTY;
    private UiRect hudMoveDoneButtonRect = UiRect.EMPTY;
    private UiRect hudScaleDownButtonRect = UiRect.EMPTY;
    private UiRect hudScaleUpButtonRect = UiRect.EMPTY;
    private UiRect importConfirmContinueButtonRect = UiRect.EMPTY;
    private UiRect importConfirmCancelButtonRect = UiRect.EMPTY;
    private UiRect gifConfirmContinueButtonRect = UiRect.EMPTY;
    private UiRect gifConfirmCancelButtonRect = UiRect.EMPTY;
    private UiRect colorPreviewRect = UiRect.EMPTY;
    private UiRect textStatusRect = UiRect.EMPTY;
    private UiRect colorPickerRect = UiRect.EMPTY;
    private UiRect svRect = UiRect.EMPTY;

    private int visibleCanvasWidth;
    private int visibleCanvasHeight;
    private int colorCenterX;
    private int colorCenterY;
    private int hueInnerRadius;
    private int hueOuterRadius;
    private int alphaInnerRadius;
    private int alphaOuterRadius;

    private HudTool selectedTool = HudTool.SELECT;
    private SliderType activeSlider;
    private DragMode dragMode = DragMode.NONE;
    private PickerDragMode pickerDragMode = PickerDragMode.NONE;
    private int selectedElementIndex = -1;
    private int draggingElementIndex = -1;
    private int resizingElementIndex = -1;
    private ResizeHandle activeResizeHandle = ResizeHandle.NONE;
    private int dragLastElementX;
    private int dragLastElementY;
    private boolean hudMoveMode;
    private boolean draggingHud;
    private int hudDragOffsetX;
    private int hudDragOffsetY;
    private int editingTextElementIndex = -1;
    private String editingTextBuffer = "";
    private int editingCaretIndex;
    private int editingSelectionStart = -1;
    private int editingSelectionEnd = -1;
    private boolean draggingTextSelection;

    private int brushSize = 10;
    private int eraserSize = 4;
    private int textFontSize = HudElement.TextLabel.DEFAULT_FONT_SIZE;
    private float hue = 165.0F;
    private float saturation = 0.71F;
    private float value = 0.75F;
    private int alpha = 220;
    private String pendingImagePath = "";
    private int pendingImageWidth = 64;
    private int pendingImageHeight = 64;
    private String imageStatusText = "이미지: 파일을 창에 드래그하세요";

    private int dragStartX;
    private int dragStartY;
    private int dragCurrentX;
    private int dragCurrentY;
    private boolean marqueeAdditiveSelection;
    private int lastBrushX;
    private int lastBrushY;
    private String fallbackClipboardText = "";
    private int pasteSequence;
    private boolean leftArrowHeld;
    private boolean rightArrowHeld;
    private boolean upArrowHeld;
    private boolean downArrowHeld;
    private boolean leftShiftHeld;
    private boolean rightShiftHeld;
    private long nextArrowMoveAtMs;
    private String hoveredTooltipId = "";
    private long hoveredTooltipSinceMs;
    private TooltipSpec hoveredTooltip;
    private boolean importConfirmVisible;
    private boolean gifConfirmVisible;
    private Path pendingDroppedImportZip;
    private GifPendingMode pendingGifMode = GifPendingMode.NONE;
    private String pendingGifSourcePath = "";
    private int pendingGifSourceWidth;
    private int pendingGifSourceHeight;

    public CustomHudEditorScreen(CustomHudState state) {
        super(Text.literal("커스텀 HUD 스튜디오"));
        // 이 화면은 기능이 많아서 덩치가 크다. 오늘은 분리보다 안정성 우선으로 간다.
        this.state = state;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        setHudMoveMode(false);
        clearArrowMoveState();
        stopTextEditing();
        CustomHudStorage.save(state);
        GuiScaleCompat.restoreEditorScale(this.client);
        if (this.client != null) {
            this.client.setScreen(null);
        }
    }

    @Override
    public void removed() {
        setHudMoveMode(false);
        clearArrowMoveState();
        stopTextEditing();
        CustomHudStorage.save(state);
        GuiScaleCompat.restoreEditorScale(this.client);
        super.removed();
    }

    @Override
    public void tick() {
        super.tick();
        updateArrowKeyMovement();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 으아아악 졸림... 그래도 레이아웃 틀어지면 더 피곤해져서 여기만큼은 매 프레임 맞춘다.
        // 프레임마다 레이아웃을 다시 계산: 해상도 바뀌어도 바로 적응하게.
        updateLayout();
        updateTooltipHover(resolveHoveredTooltip(mouseX, mouseY));
        renderBackdrop(context);
        if (hudMoveMode) {
            renderHudPositionPreview(context);
            renderHudMoveOverlay(context, mouseX, mouseY);
            if (importConfirmVisible) {
                renderImportConfirmDialog(context, mouseX, mouseY);
            }
            if (gifConfirmVisible) {
                renderGifConfirmDialog(context, mouseX, mouseY);
            }
            renderDelayedTooltip(context, mouseX, mouseY);
            return;
        }
        renderCanvas(context, mouseX, mouseY);
        renderPanels(context, mouseX, mouseY);
        if (importConfirmVisible) {
            renderImportConfirmDialog(context, mouseX, mouseY);
        }
        if (gifConfirmVisible) {
            renderGifConfirmDialog(context, mouseX, mouseY);
        }
        renderDelayedTooltip(context, mouseX, mouseY);
    }

    @Override
    public void onFilesDropped(List<Path> paths) {
        if (paths == null || paths.isEmpty()) {
            imageStatusText = "이미지 업로드 실패: 파일이 없습니다";
            return;
        }

        // ZIP 드롭은 이미지보다 우선 처리: 바로 받기 확인창으로 연결.
        for (Path path : paths) {
            Path normalized;
            try {
                normalized = path.toAbsolutePath().normalize();
            } catch (Exception ignored) {
                continue;
            }

            String fileName = normalized.getFileName() == null ? "" : normalized.getFileName().toString().toLowerCase();
            if (!fileName.endsWith(".zip") || !Files.isRegularFile(normalized)) {
                continue;
            }

            pendingDroppedImportZip = normalized;
            importConfirmVisible = true;
            imageStatusText = "ZIP을 끌어왔습니다. 받겠습니까?";
            return;
        }

        pendingDroppedImportZip = null;

        // 여러 파일을 떨어뜨려도 첫 성공 파일 기준으로 바로 작업 흐름을 이어간다.
        for (Path path : paths) {
            CustomHudImageTextureManager.TextureInfo info = CustomHudImageTextureManager.preload(path);
            if (info == null) {
                continue;
            }

            if (isGifSourcePath(info.sourcePath())) {
                queueGifConfirm(info, GifPendingMode.PREPARE_IMAGE_TOOL);
                return;
            }

            preparePendingImageTool(info);
            return;
        }

        imageStatusText = "이미지 업로드 실패: 지원 형식을 확인하세요";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean doubleClick = false;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (importConfirmVisible) {
            if (importConfirmContinueButtonRect.contains(mouseX, mouseY)) {
                importConfirmVisible = false;
                handleImportClick();
                return true;
            }
            if (importConfirmCancelButtonRect.contains(mouseX, mouseY)) {
                importConfirmVisible = false;
                pendingDroppedImportZip = null;
                return true;
            }
            return true;
        }

        if (gifConfirmVisible) {
            if (gifConfirmContinueButtonRect.contains(mouseX, mouseY)) {
                gifConfirmVisible = false;
                applyPendingGifAction(true);
                return true;
            }
            if (gifConfirmCancelButtonRect.contains(mouseX, mouseY)) {
                gifConfirmVisible = false;
                applyPendingGifAction(false);
                return true;
            }
            return true;
        }

        if (hudMoveMode) {
            if (hudMoveDoneButtonRect.contains(mouseX, mouseY)) {
                setHudMoveMode(false);
                return true;
            }
            if (hudScaleDownButtonRect.contains(mouseX, mouseY)) {
                adjustHudScale(-HUD_SCALE_STEP_PERCENT);
                return true;
            }
            if (hudScaleUpButtonRect.contains(mouseX, mouseY)) {
                adjustHudScale(HUD_SCALE_STEP_PERCENT);
                return true;
            }
            if (beginHudMoveDrag(mouseX, mouseY)) {
                stopTextEditing();
                resetDragState();
            }
            return true;
        }

        if (closeButtonRect.contains(mouseX, mouseY)) {
            close();
            return true;
        }

        if (exportButtonRect.contains(mouseX, mouseY)) {
            handleExportClick();
            return true;
        }

        if (importButtonRect.contains(mouseX, mouseY)) {
            pendingDroppedImportZip = null;
            importConfirmVisible = true;
            return true;
        }

        if (clearButtonRect.contains(mouseX, mouseY)) {
            boolean bundled = CustomHudStorage.resetToInitialHud(state);
            clearElementSelection();
            stopTextEditing();
            resetDragState();
            imageStatusText = bundled ? "초기화 완료: hud.zip 적용" : "초기화 완료: 기본 HUD 적용";
            return true;
        }

        if (undoButtonRect.contains(mouseX, mouseY)) {
            if (state.undo()) {
                clearElementSelection();
                stopTextEditing();
            }
            return true;
        }

        if (redoButtonRect.contains(mouseX, mouseY)) {
            if (state.redo()) {
                clearElementSelection();
                stopTextEditing();
            }
            return true;
        }

        if (hudMoveButtonRect.contains(mouseX, mouseY)) {
            setHudMoveMode(true);
            activeSlider = null;
            pickerDragMode = PickerDragMode.NONE;
            clearArrowMoveState();
            stopTextEditing();
            resetDragState();
            return true;
        }

        for (Map.Entry<HudTool, UiRect> entry : toolButtons.entrySet()) {
            if (entry.getValue().contains(mouseX, mouseY)) {
                HudTool clickedTool = entry.getKey();
                if (selectedTool == clickedTool && clickedTool != HudTool.SELECT) {
                    selectedTool = HudTool.SELECT;
                } else {
                    selectedTool = clickedTool;
                }
                resetDragState();
                if (selectedTool != HudTool.TEXT) {
                    stopTextEditing();
                }
                return true;
            }
        }

        for (Map.Entry<LayerAction, UiRect> entry : layerButtons.entrySet()) {
            if (entry.getValue().contains(mouseX, mouseY)) {
                applyLayerAction(entry.getKey());
                stopTextEditing();
                return true;
            }
        }

        if (handleColorPickerPointer(mouseX, mouseY, true)) {
            activeSlider = null;
            stopTextEditing();
            return true;
        }

        SliderType clickedSlider = findSlider(mouseX, mouseY);
        if (clickedSlider != null) {
            activeSlider = clickedSlider;
            updateSliderValue(clickedSlider, mouseX);
            stopTextEditing();
            return true;
        }

        if (!canvasRect.contains(mouseX, mouseY)) {
            stopTextEditing();
            return super.mouseClicked(mouseX, mouseY, button);
        }

        int localX = clampLocalX((int) Math.floor(mouseX) - canvasRect.x);
        int localY = clampLocalY((int) Math.floor(mouseY) - canvasRect.y);

        switch (selectedTool) {
            case BRUSH -> {
                dragMode = DragMode.BRUSH;
                activeBrushPoints.clear();
                appendBrushPoint(localX, localY);
                lastBrushX = localX;
                lastBrushY = localY;
                return true;
            }
            case FILL -> {
                state.addElementAtBottom(new HudElement.FillRect(0, 0, state.getCanvasWidth(), state.getCanvasHeight(), currentColor()));
                clearElementSelection();
                return true;
            }
            case RECTANGLE, CIRCLE, LINE -> {
                dragMode = DragMode.SHAPE;
                dragStartX = localX;
                dragStartY = localY;
                dragCurrentX = localX;
                dragCurrentY = localY;
                return true;
            }
            case IMAGE -> {
                return handleImageToolClick(localX, localY);
            }
            case TEXT -> {
                return handleTextToolClick(localX, localY, doubleClick);
            }
            case SELECT -> {
                stopTextEditing();
                return handleSelectModeClick(localX, localY, doubleClick, isShiftHeld());
            }
            case ERASER -> {
                stopTextEditing();
                eraseAt(localX, localY);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }

        if (gifConfirmVisible) {
            return true;
        }

        if (hudMoveMode) {
            if (draggingHud) {
                int nextHudX = (int) Math.floor(mouseX) - hudDragOffsetX;
                int nextHudY = (int) Math.floor(mouseY) - hudDragOffsetY;
                state.setHudPositionDirect(
                        clampHudX(nextHudX, this.width),
                        clampHudY(nextHudY, this.height)
                );
            }
            return true;
        }

        if (draggingTextSelection && editingTextElementIndex >= 0) {
            updateTextSelectionFromMouse((int) Math.floor(mouseX));
            return true;
        }

        if (activeSlider != null) {
            updateSliderValue(activeSlider, mouseX);
            return true;
        }

        if (pickerDragMode != PickerDragMode.NONE) {
            handleColorPickerPointer(mouseX, mouseY, false);
            return true;
        }

        if (!canvasRect.contains(mouseX, mouseY)) {
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }

        int localX = clampLocalX((int) Math.floor(mouseX) - canvasRect.x);
        int localY = clampLocalY((int) Math.floor(mouseY) - canvasRect.y);

        if (selectedTool == HudTool.SELECT && resizingElementIndex >= 0 && activeResizeHandle != ResizeHandle.NONE) {
            resizeSelectedElement(localX, localY);
            return true;
        }

        if (selectedTool == HudTool.SELECT && draggingElementIndex >= 0) {
            int moveDeltaX = localX - dragLastElementX;
            int moveDeltaY = localY - dragLastElementY;
            if ((moveDeltaX != 0 || moveDeltaY != 0) && translateDraggedSelection(moveDeltaX, moveDeltaY)) {
                dragLastElementX = localX;
                dragLastElementY = localY;
                if (editingTextElementIndex == draggingElementIndex) {
                    stopTextEditing();
                }
            }
            return true;
        }

        if (selectedTool == HudTool.SELECT && dragMode == DragMode.SELECT_MARQUEE) {
            dragCurrentX = localX;
            dragCurrentY = localY;
            return true;
        }

        if (dragMode == DragMode.BRUSH) {
            appendInterpolatedBrush(localX, localY);
            return true;
        }

        if (dragMode == DragMode.SHAPE) {
            dragCurrentX = localX;
            dragCurrentY = localY;
            return true;
        }

        if (selectedTool == HudTool.ERASER) {
            eraseAt(localX, localY);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseReleased(mouseX, mouseY, button);
        }

        if (gifConfirmVisible) {
            return true;
        }

        if (hudMoveMode) {
            draggingHud = false;
            return true;
        }

        if (draggingTextSelection) {
            draggingTextSelection = false;
            normalizeSelectionState();
            return true;
        }

        if (activeSlider != null) {
            activeSlider = null;
            return true;
        }

        if (pickerDragMode != PickerDragMode.NONE) {
            pickerDragMode = PickerDragMode.NONE;
            return true;
        }

        if (selectedTool == HudTool.SELECT && dragMode == DragMode.SELECT_MARQUEE) {
            applyMarqueeSelection();
            resetDragState();
            return true;
        }

        if (selectedTool == HudTool.SELECT && (draggingElementIndex >= 0 || resizingElementIndex >= 0)) {
            draggingElementIndex = -1;
            resetResizeState();
            return true;
        }

        if (dragMode == DragMode.BRUSH && !activeBrushPoints.isEmpty()) {
            state.addElement(new HudElement.BrushStroke(HudElement.BrushStroke.copyPoints(activeBrushPoints), brushSize, currentColor()));
            setSingleSelection(state.getElements().size() - 1);
            resetDragState();
            return true;
        }

        if (dragMode == DragMode.SHAPE) {
            HudElement createdElement = switch (selectedTool) {
                case RECTANGLE -> new HudElement.Rectangle(dragStartX, dragStartY, dragCurrentX, dragCurrentY, currentColor());
                case CIRCLE -> new HudElement.Circle(dragStartX, dragStartY, dragCurrentX, dragCurrentY, currentColor());
                case LINE -> new HudElement.Line(dragStartX, dragStartY, dragCurrentX, dragCurrentY, brushSize, currentColor());
                default -> null;
            };
            if (createdElement != null) {
                state.addElement(createdElement);
                setSingleSelection(state.getElements().size() - 1);
            }
            resetDragState();
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean shortcutDown = (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        boolean shiftDown = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;

        if (importConfirmVisible) {
            if (keyCode == InputUtil.GLFW_KEY_ESCAPE) {
                importConfirmVisible = false;
                pendingDroppedImportZip = null;
            }
            return true;
        }

        if (gifConfirmVisible) {
            if (keyCode == InputUtil.GLFW_KEY_ESCAPE) {
                gifConfirmVisible = false;
                applyPendingGifAction(false);
                return true;
            }
            if (keyCode == InputUtil.GLFW_KEY_ENTER || keyCode == InputUtil.GLFW_KEY_KP_ENTER) {
                gifConfirmVisible = false;
                applyPendingGifAction(true);
                return true;
            }
            return true;
        }

        if (keyCode == InputUtil.GLFW_KEY_LEFT_SHIFT) {
            leftShiftHeld = true;
            return true;
        }
        if (keyCode == InputUtil.GLFW_KEY_RIGHT_SHIFT) {
            rightShiftHeld = true;
            return true;
        }

        if (shortcutDown && keyCode == InputUtil.GLFW_KEY_C) {
            if (editingTextElementIndex >= 0) {
                copyEditingTextToClipboard();
            } else {
                copySelectedElementsToClipboard();
            }
            return true;
        }

        if (shortcutDown && keyCode == InputUtil.GLFW_KEY_X) {
            if (editingTextElementIndex >= 0) {
                cutEditingTextToClipboard();
            } else {
                copySelectedElementsToClipboard();
                deleteSelectedElements();
            }
            return true;
        }

        if (shortcutDown && keyCode == InputUtil.GLFW_KEY_V) {
            if (editingTextElementIndex >= 0) {
                pasteTextIntoEditing();
                return true;
            }
            if (pasteElementsFromClipboard()) {
                return true;
            }
            if (pasteImageFromClipboard()) {
                return true;
            }
            if (pasteTextElementFromClipboard()) {
                return true;
            }
            imageStatusText = "붙여넣기 실패: 클립보드를 확인하세요";
            return true;
        }

        if (shortcutDown && shiftDown && keyCode == InputUtil.GLFW_KEY_Z) {
            if (state.redo()) {
                clearElementSelection();
                stopTextEditing();
            }
            return true;
        }

        if (shortcutDown && keyCode == InputUtil.GLFW_KEY_Z) {
            if (state.undo()) {
                clearElementSelection();
                stopTextEditing();
            }
            return true;
        }

        if (shortcutDown && keyCode == InputUtil.GLFW_KEY_Y) {
            if (state.redo()) {
                clearElementSelection();
                stopTextEditing();
            }
            return true;
        }

        if (editingTextElementIndex >= 0) {
            if (shortcutDown && keyCode == InputUtil.GLFW_KEY_A) {
                selectAllEditingText();
                return true;
            }

            if (keyCode == InputUtil.GLFW_KEY_BACKSPACE) {
                deleteBackspaceAtCaret();
                applyTextEditLive();
                return true;
            }

            if (keyCode == InputUtil.GLFW_KEY_DELETE) {
                deleteForwardAtCaret();
                applyTextEditLive();
                return true;
            }

            if (keyCode == InputUtil.GLFW_KEY_ENTER || keyCode == InputUtil.GLFW_KEY_KP_ENTER) {
                stopTextEditing();
                return true;
            }

            if (keyCode == InputUtil.GLFW_KEY_LEFT) {
                moveCaretBy(-1, shiftDown);
                return true;
            }

            if (keyCode == InputUtil.GLFW_KEY_RIGHT) {
                moveCaretBy(1, shiftDown);
                return true;
            }

            if (keyCode == InputUtil.GLFW_KEY_HOME) {
                moveCaretTo(0, shiftDown);
                return true;
            }

            if (keyCode == InputUtil.GLFW_KEY_END) {
                moveCaretTo(editingTextBuffer.length(), shiftDown);
                return true;
            }
        }

        if (!hudMoveMode && editingTextElementIndex < 0 && shortcutDown && keyCode == InputUtil.GLFW_KEY_A) {
            selectAllElements();
            return true;
        }

        if (!hudMoveMode && editingTextElementIndex < 0
                && (keyCode == InputUtil.GLFW_KEY_DELETE || keyCode == InputUtil.GLFW_KEY_BACKSPACE)) {
            deleteSelectedElements();
            return true;
        }

        if (!hudMoveMode && editingTextElementIndex < 0 && !shortcutDown && handleToolShortcutKey(keyCode)) {
            return true;
        }

        if (hudMoveMode && !shortcutDown) {
            if (keyCode == InputUtil.GLFW_KEY_LEFT_BRACKET
                    || keyCode == InputUtil.GLFW_KEY_MINUS
                    || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) {
                adjustHudScale(-HUD_SCALE_STEP_PERCENT);
                return true;
            }
            if (keyCode == InputUtil.GLFW_KEY_RIGHT_BRACKET
                    || keyCode == InputUtil.GLFW_KEY_EQUAL
                    || keyCode == GLFW.GLFW_KEY_KP_ADD) {
                adjustHudScale(HUD_SCALE_STEP_PERCENT);
                return true;
            }
        }

        if (keyCode == InputUtil.GLFW_KEY_ESCAPE) {
            if (hudMoveMode) {
                setHudMoveMode(false);
                return true;
            }
            if (editingTextElementIndex >= 0) {
                stopTextEditing();
                return true;
            }
            close();
            return true;
        }

        if (editingTextElementIndex < 0 && !hudMoveMode && isArrowKey(keyCode)) {
            boolean wasHeld = isArrowHeld(keyCode);
            setArrowHeld(keyCode, true);
            if (!wasHeld) {
                moveSelectedElementByArrowKeys();
                nextArrowMoveAtMs = System.currentTimeMillis() + ARROW_REPEAT_INITIAL_DELAY_MS;
            }
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {

        if (keyCode == InputUtil.GLFW_KEY_LEFT_SHIFT) {
            leftShiftHeld = false;
            return true;
        }
        if (keyCode == InputUtil.GLFW_KEY_RIGHT_SHIFT) {
            rightShiftHeld = false;
            return true;
        }

        if (isArrowKey(keyCode)) {
            setArrowHeld(keyCode, false);
            if (!isAnyArrowHeld()) {
                nextArrowMoveAtMs = 0L;
            }
            return true;
        }

        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (gifConfirmVisible) {
            return true;
        }

        if (editingTextElementIndex >= 0 && isValidTextChar(chr)) {
            String typed = String.valueOf(chr);
            if (!typed.isEmpty()) {
                insertTextAtCaret(typed);
                applyTextEditLive();
            }
            return true;
        }

        return super.charTyped(chr, modifiers);
    }

    private void updateLayout() {
        int topBarHeight = 26;
        int leftWidth = 116;
        int rightWidth = 228;

        int clampedHudX = clampHudX(state.getHudX(), this.width);
        int clampedHudY = clampHudY(state.getHudY(), this.height);
        if (clampedHudX != state.getHudX() || clampedHudY != state.getHudY()) {
            state.setHudPositionDirect(clampedHudX, clampedHudY);
        }

        topBarRect = new UiRect(SIDE_MARGIN, TOP_MARGIN, this.width - (SIDE_MARGIN * 2), topBarHeight);

        int panelTop = topBarRect.bottom() + 8;
        int panelHeight = this.height - panelTop - SIDE_MARGIN;

        leftPanelRect = new UiRect(SIDE_MARGIN, panelTop, leftWidth, panelHeight);
        rightPanelRect = new UiRect(this.width - rightWidth - SIDE_MARGIN, panelTop, rightWidth, panelHeight);

        int canvasLeft = leftPanelRect.right() + SIDE_MARGIN;
        int canvasRight = rightPanelRect.x - SIDE_MARGIN;
        int canvasWidth = Math.max(150, canvasRight - canvasLeft);
        int canvasHeight = Math.max(100, panelHeight);
        canvasRect = new UiRect(canvasLeft, panelTop, canvasWidth, canvasHeight);

        visibleCanvasWidth = Math.max(80, Math.min(state.getCanvasWidth(), canvasRect.width));
        visibleCanvasHeight = Math.max(60, Math.min(state.getCanvasHeight(), canvasRect.height));

        toolButtons.clear();
        int toolX = leftPanelRect.x + 8;
        int toolY = leftPanelRect.y + 14;
        int toolWidth = leftPanelRect.width - 16;
        for (HudTool tool : HudTool.values()) {
            toolButtons.put(tool, new UiRect(toolX, toolY, toolWidth, 18));
            toolY += 20;
        }

        int actionY = toolY + 6;
        int actionWidth = toolWidth;
        for (LayerAction layerAction : LayerAction.values()) {
            layerButtons.put(layerAction, new UiRect(toolX, actionY, actionWidth, 16));
            actionY += 18;
        }

        sliders.clear();
        int sliderX = rightPanelRect.x + 12;
        int sliderWidth = rightPanelRect.width - 24;
        int pickerSize = Math.min(rightPanelRect.width - 26, 130);
        colorPickerRect = new UiRect(rightPanelRect.x + ((rightPanelRect.width - pickerSize) / 2), rightPanelRect.y + 32, pickerSize, pickerSize);

        colorCenterX = colorPickerRect.x + (colorPickerRect.width / 2);
        colorCenterY = colorPickerRect.y + (colorPickerRect.height / 2);
        hueOuterRadius = Math.max(24, (pickerSize / 2) - 10);
        hueInnerRadius = Math.max(12, hueOuterRadius - 11);
        alphaInnerRadius = hueOuterRadius + 4;
        alphaOuterRadius = hueOuterRadius + 12;

        int svHalf = Math.max(10, (int) Math.floor((hueInnerRadius - 4) / Math.sqrt(2.0D)));
        svRect = new UiRect(colorCenterX - svHalf, colorCenterY - svHalf, svHalf * 2, svHalf * 2);

        int sliderY = colorPickerRect.bottom() + 20;
        for (SliderType sliderType : SliderType.values()) {
            sliders.put(sliderType, new UiRect(sliderX, sliderY, sliderWidth, 12));
            sliderY += 26;
        }

        colorPreviewRect = new UiRect(rightPanelRect.x + 12, sliderY + 2, rightPanelRect.width - 24, 18);
        int moveButtonY = colorPreviewRect.bottom() + 4;
        hudMoveButtonRect = new UiRect(rightPanelRect.x + 12, moveButtonY, rightPanelRect.width - 24, 16);
        int doneButtonWidth = 132;
        int doneButtonHeight = 18;
        hudMoveDoneButtonRect = new UiRect((this.width - doneButtonWidth) / 2, this.height - SIDE_MARGIN - doneButtonHeight, doneButtonWidth, doneButtonHeight);
        int scaleButtonWidth = 54;
        int scaleButtonHeight = 18;
        int scaleButtonGap = 8;
        int scaleButtonsLeft = (this.width - ((scaleButtonWidth * 2) + scaleButtonGap)) / 2;
        int scaleButtonsY = hudMoveDoneButtonRect.y - scaleButtonHeight - 4;
        hudScaleDownButtonRect = new UiRect(scaleButtonsLeft, scaleButtonsY, scaleButtonWidth, scaleButtonHeight);
        hudScaleUpButtonRect = new UiRect(scaleButtonsLeft + scaleButtonWidth + scaleButtonGap, scaleButtonsY, scaleButtonWidth, scaleButtonHeight);
        textStatusRect = new UiRect(rightPanelRect.x + 12, moveButtonY + 22, rightPanelRect.width - 24, 50);
        exportButtonRect = new UiRect(topBarRect.right() - 432, topBarRect.y + 4, 64, 16);
        importButtonRect = new UiRect(topBarRect.right() - 360, topBarRect.y + 4, 64, 16);
        undoButtonRect = new UiRect(topBarRect.right() - 288, topBarRect.y + 4, 64, 16);
        redoButtonRect = new UiRect(topBarRect.right() - 216, topBarRect.y + 4, 64, 16);
        clearButtonRect = new UiRect(topBarRect.right() - 144, topBarRect.y + 4, 64, 16);
        closeButtonRect = new UiRect(topBarRect.right() - 72, topBarRect.y + 4, 64, 16);

        int confirmButtonWidth = 56;
        int confirmButtonHeight = 18;
        int confirmButtonGap = 8;
        int confirmButtonsY = (this.height / 2) + 22;
        int confirmButtonsLeft = (this.width - ((confirmButtonWidth * 2) + confirmButtonGap)) / 2;
        importConfirmContinueButtonRect = new UiRect(confirmButtonsLeft, confirmButtonsY, confirmButtonWidth, confirmButtonHeight);
        importConfirmCancelButtonRect = new UiRect(confirmButtonsLeft + confirmButtonWidth + confirmButtonGap, confirmButtonsY, confirmButtonWidth, confirmButtonHeight);
        gifConfirmContinueButtonRect = new UiRect(confirmButtonsLeft, confirmButtonsY, confirmButtonWidth, confirmButtonHeight);
        gifConfirmCancelButtonRect = new UiRect(confirmButtonsLeft + confirmButtonWidth + confirmButtonGap, confirmButtonsY, confirmButtonWidth, confirmButtonHeight);
    }

    private void renderBackdrop(DrawContext context) {
        context.fillGradient(0, 0, this.width, this.height, 0xA20A141E, 0xA21E3045);
    }

    private void renderHudPositionPreview(DrawContext context) {
        // GUI는 재능이 없어, AI한테 ㅋ 라는 마음으로도 좌표 계산은 냉정하게 간다.
        CustomHudRenderer.renderHud(context, this.textRenderer, state);

        int previewX = clampHudX(state.getHudX(), context.getScaledWindowWidth()) - 1;
        int previewY = clampHudY(state.getHudY(), context.getScaledWindowHeight()) - 1;
        int previewW = scaleCanvasDimension(state.getCanvasWidth()) + 2;
        int previewH = scaleCanvasDimension(state.getCanvasHeight()) + 2;
        int borderColor = hudMoveMode ? 0xFFFFE18A : 0x66E7F6F2;
        HudRenderUtil.drawStrokedRect(context, previewX, previewY, previewW, previewH, borderColor);
        if (hudMoveMode) {
            String hint = "HUD 이동/크기 모드: 박스 드래그, 버튼으로 크기 조절";
            int hintX = Math.max(6, Math.min(this.width - this.textRenderer.getWidth(hint) - 6, previewX));
            int hintY = Math.max(6, previewY - 12);
            context.fill(hintX - 3, hintY - 2, hintX + this.textRenderer.getWidth(hint) + 3, hintY + 10, 0xA0121E29);
            context.drawTextWithShadow(this.textRenderer, hint, hintX, hintY, 0xFFFDF6E2);
        }
    }

    private void renderCanvas(DrawContext context, int mouseX, int mouseY) {
        renderCanvasBackground(context);

        int clipLeft = canvasRect.x;
        int clipTop = canvasRect.y;
        int clipRight = canvasRect.x + visibleCanvasWidth;
        int clipBottom = canvasRect.y + visibleCanvasHeight;

        // 캔버스 바깥으로 그려지는 건 잘라서, 편집 체감이 흐트러지지 않게 유지.
        context.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        CustomHudRenderer.renderHud(
                context,
                this.textRenderer,
                state,
                canvasRect.x,
                canvasRect.y,
                0,
                0,
                visibleCanvasWidth - 1,
                visibleCanvasHeight - 1
        );
        renderPreviewElement(context);
        renderToolCursorPreview(context, mouseX, mouseY);
        renderSelectedBounds(context);
        renderMarqueeOverlay(context);
        renderTextEditingOverlay(context);
        context.disableScissor();

        HudRenderUtil.drawStrokedRect(context, canvasRect.x, canvasRect.y, visibleCanvasWidth, visibleCanvasHeight, 0xFFBFDCD2);
    }

    private void renderToolCursorPreview(DrawContext context, int mouseX, int mouseY) {
        if (!canvasRect.contains(mouseX, mouseY)) {
            return;
        }
        if (selectedTool == HudTool.SELECT || hudMoveMode) {
            return;
        }

        int localX = clampLocalX(mouseX - canvasRect.x);
        int localY = clampLocalY(mouseY - canvasRect.y);
        int centerX = canvasRect.x + localX;
        int centerY = canvasRect.y + localY;

        String sizeText = null;
        switch (selectedTool) {
            case BRUSH, LINE -> {
                int size = Math.max(1, brushSize);
                int left = centerX - (size / 2);
                int top = centerY - (size / 2);
                context.fill(left, top, left + size, top + size, currentColorWithAlpha(70));
                HudRenderUtil.drawStrokedRect(context, left, top, size, size, HudRenderUtil.argb(230, 240, 255, 250));
                sizeText = "브러시 " + size + "px";
            }
            case ERASER -> {
                int radius = Math.max(1, eraserSize);
                HudRenderUtil.drawFilledEllipse(context, centerX - radius, centerY - radius, centerX + radius, centerY + radius, 0x33FFFFFF);
                HudRenderUtil.drawCircleOutline(context, centerX, centerY, radius, 0xFFF4E7C6);
                sizeText = "지우개 " + (radius * 2) + "px";
            }
            case FILL -> {
                context.fill(canvasRect.x, canvasRect.y, canvasRect.x + visibleCanvasWidth, canvasRect.y + visibleCanvasHeight, 0x12000000);
                context.drawVerticalLine(centerX, centerY - 5, centerY + 5, 0xFFE8F6F1);
                context.drawHorizontalLine(centerX - 5, centerX + 5, centerY, 0xFFE8F6F1);
                sizeText = "전체 채우기";
            }
            case IMAGE -> {
                int width = Math.max(1, pendingImageWidth);
                int height = Math.max(1, pendingImageHeight);
                int left = centerX - (width / 2);
                int top = centerY - (height / 2);
                context.fill(left, top, left + width, top + height, 0x42243A4A);
                HudRenderUtil.drawStrokedRect(context, left, top, width, height, 0xFFD4E8E2);
                sizeText = "이미지 " + width + "x" + height;
            }
            default -> {
                context.drawVerticalLine(centerX, centerY - 4, centerY + 4, 0xCCD6ECE6);
                context.drawHorizontalLine(centerX - 4, centerX + 4, centerY, 0xCCD6ECE6);
            }
        }

        if (sizeText != null) {
            int textX = Math.min(canvasRect.x + visibleCanvasWidth - this.textRenderer.getWidth(sizeText) - 6, centerX + 12);
            int textY = Math.max(canvasRect.y + 4, centerY + 10);
            context.fill(textX - 3, textY - 2, textX + this.textRenderer.getWidth(sizeText) + 3, textY + 10, 0xAA111A23);
            context.drawTextWithShadow(this.textRenderer, sizeText, textX, textY, 0xFFF1F9F6);
        }
    }

    private void renderCanvasBackground(DrawContext context) {
        int endX = canvasRect.x + visibleCanvasWidth;
        int endY = canvasRect.y + visibleCanvasHeight;
        context.fillGradient(canvasRect.x, canvasRect.y, endX, endY, 0xE0122230, 0xE01A3142);

        int tile = 10;
        for (int y = canvasRect.y; y < endY; y += tile) {
            for (int x = canvasRect.x; x < endX; x += tile) {
                int color = ((x / tile) + (y / tile)) % 2 == 0 ? 0x1EFFFFFF : 0x10000000;
                context.fill(x, y, Math.min(x + tile, endX), Math.min(y + tile, endY), color);
            }
        }
    }

    private void renderPreviewElement(DrawContext context) {
        if (dragMode == DragMode.BRUSH && !activeBrushPoints.isEmpty()) {
            int color = previewColor();
            for (HudElement.HudPoint point : activeBrushPoints) {
                HudRenderUtil.drawBrushDot(context, canvasRect.x + point.x(), canvasRect.y + point.y(), brushSize, color);
            }
            return;
        }

        if (dragMode == DragMode.SHAPE) {
            int x1 = canvasRect.x + dragStartX;
            int y1 = canvasRect.y + dragStartY;
            int x2 = canvasRect.x + dragCurrentX;
            int y2 = canvasRect.y + dragCurrentY;
            int color = previewColor();
            switch (selectedTool) {
                case RECTANGLE -> HudRenderUtil.drawFilledRect(context, x1, y1, x2, y2, color);
                case CIRCLE -> HudRenderUtil.drawFilledEllipse(context, x1, y1, x2, y2, color);
                case LINE -> HudRenderUtil.drawLine(context, x1, y1, x2, y2, brushSize, color);
                default -> {
                }
            }
        }
    }

    private void renderSelectedBounds(DrawContext context) {
        List<HudElement> elements = state.getElements();
        List<Integer> selectedIndices = getValidSelectedIndices(elements.size());
        if (selectedIndices.isEmpty()) {
            return;
        }

        int unionMinX = Integer.MAX_VALUE;
        int unionMinY = Integer.MAX_VALUE;
        int unionMaxX = Integer.MIN_VALUE;
        int unionMaxY = Integer.MIN_VALUE;

        for (int index : selectedIndices) {
            HudElement.Bounds bounds = elements.get(index).bounds();
            unionMinX = Math.min(unionMinX, bounds.minX());
            unionMinY = Math.min(unionMinY, bounds.minY());
            unionMaxX = Math.max(unionMaxX, bounds.maxX());
            unionMaxY = Math.max(unionMaxY, bounds.maxY());

            int x = canvasRect.x + bounds.minX();
            int y = canvasRect.y + bounds.minY();
            int w = bounds.maxX() - bounds.minX() + 1;
            int h = bounds.maxY() - bounds.minY() + 1;
            int color = index == selectedElementIndex ? 0xFFF7ECA3 : 0xFF98D6C4;
            HudRenderUtil.drawStrokedRect(context, x - 1, y - 1, w + 2, h + 2, color);
        }

        if (selectedIndices.size() > 1) {
            int unionX = canvasRect.x + unionMinX;
            int unionY = canvasRect.y + unionMinY;
            int unionW = unionMaxX - unionMinX + 1;
            int unionH = unionMaxY - unionMinY + 1;
            HudRenderUtil.drawStrokedRect(context, unionX - 2, unionY - 2, unionW + 4, unionH + 4, 0xFFDFEEB8);
        }

        if (selectedIndices.size() == 1) {
            HudElement element = elements.get(selectedIndices.get(0));
            if (isResizableElement(element)) {
                drawResizeHandles(context, element.bounds());
            }
        }
    }

    private void renderMarqueeOverlay(DrawContext context) {
        if (dragMode != DragMode.SELECT_MARQUEE) {
            return;
        }

        int minX = Math.min(dragStartX, dragCurrentX);
        int minY = Math.min(dragStartY, dragCurrentY);
        int maxX = Math.max(dragStartX, dragCurrentX);
        int maxY = Math.max(dragStartY, dragCurrentY);
        int width = Math.max(1, maxX - minX + 1);
        int height = Math.max(1, maxY - minY + 1);

        int x = canvasRect.x + minX;
        int y = canvasRect.y + minY;
        context.fill(x, y, x + width, y + height, 0x254BA9C8);
        HudRenderUtil.drawStrokedRect(context, x, y, width, height, marqueeAdditiveSelection ? 0xFFE1F7AE : 0xFF96DEF6);
    }

    private void renderHudMoveOverlay(DrawContext context, int mouseX, int mouseY) {
        String guide = "HUD 위치/크기를 조정한 뒤 [완료]를 누르세요";
        int guideWidth = this.textRenderer.getWidth(guide) + 12;
        int guideX = (this.width - guideWidth) / 2;
        int guideY = Math.max(8, hudScaleDownButtonRect.y - 18);
        context.fillGradient(guideX, guideY, guideX + guideWidth, guideY + 14, 0xC4213442, 0xC4182732);
        HudRenderUtil.drawStrokedRect(context, guideX, guideY, guideWidth, 14, 0xFFA5C8BF);
        context.drawTextWithShadow(this.textRenderer, guide, guideX + 6, guideY + 3, 0xFFF1F9F6);
        drawButton(context, hudScaleDownButtonRect, "-5%", hudScaleDownButtonRect.contains(mouseX, mouseY), false);
        drawButton(context, hudScaleUpButtonRect, "+5%", hudScaleUpButtonRect.contains(mouseX, mouseY), false);
        String scaleLabel = "HUD 크기 " + state.getHudScalePercent() + "%";
        int scaleLabelX = (this.width - this.textRenderer.getWidth(scaleLabel)) / 2;
        int scaleLabelY = hudScaleDownButtonRect.y - 11;
        context.drawTextWithShadow(this.textRenderer, scaleLabel, scaleLabelX, scaleLabelY, 0xFFEAF8F4);
        drawButton(context, hudMoveDoneButtonRect, "완료", hudMoveDoneButtonRect.contains(mouseX, mouseY), true);
    }

    private void renderImportConfirmDialog(DrawContext context, int mouseX, int mouseY) {
        context.fill(0, 0, this.width, this.height, 0xA0000000);

        int dialogWidth = 316;
        int dialogHeight = 82;
        int dialogX = (this.width - dialogWidth) / 2;
        int dialogY = (this.height - dialogHeight) / 2;
        context.fillGradient(dialogX, dialogY, dialogX + dialogWidth, dialogY + dialogHeight, 0xE0283B49, 0xE01B2B36);
        HudRenderUtil.drawStrokedRect(context, dialogX, dialogY, dialogWidth, dialogHeight, 0xFF9BC0B7);

        boolean droppedZipPending = pendingDroppedImportZip != null;
        String line1 = droppedZipPending ? "끌어온 ZIP을 받겠습니까?" : "지금 당신의 내용이 사라집니다.";
        String line2 = droppedZipPending
                ? "현재 HUD를 덮어씁니다. 계속하면 바로 가져옵니다."
                : "내보내기를 하고, 받기를 하는걸 권장드립니다.";
        int line1X = dialogX + 10;
        context.drawTextWithShadow(this.textRenderer, line1, line1X, dialogY + 12, 0xFFF4FBF8);
        context.drawTextWithShadow(this.textRenderer, line2, line1X, dialogY + 26, 0xFFE0F0EB);

        drawButton(context, importConfirmContinueButtonRect, "계속", importConfirmContinueButtonRect.contains(mouseX, mouseY), false);
        drawButton(context, importConfirmCancelButtonRect, "취소", importConfirmCancelButtonRect.contains(mouseX, mouseY), false);
    }

    private void renderGifConfirmDialog(DrawContext context, int mouseX, int mouseY) {
        context.fill(0, 0, this.width, this.height, 0xA0000000);

        int dialogWidth = 350;
        int dialogHeight = 88;
        int dialogX = (this.width - dialogWidth) / 2;
        int dialogY = (this.height - dialogHeight) / 2;
        context.fillGradient(dialogX, dialogY, dialogX + dialogWidth, dialogY + dialogHeight, 0xE0283B49, 0xE01B2B36);
        HudRenderUtil.drawStrokedRect(context, dialogX, dialogY, dialogWidth, dialogHeight, 0xFF9BC0B7);

        String line1 = "gif 이미지는, 어 많은 자원을 소모할 수 있습니다";
        String line2 = "계속 진행하시겠습니까?";
        int lineX = dialogX + 10;
        context.drawTextWithShadow(this.textRenderer, line1, lineX, dialogY + 12, 0xFFF4FBF8);
        context.drawTextWithShadow(this.textRenderer, line2, lineX, dialogY + 28, 0xFFE0F0EB);

        drawButton(context, gifConfirmContinueButtonRect, "계속", gifConfirmContinueButtonRect.contains(mouseX, mouseY), false);
        drawButton(context, gifConfirmCancelButtonRect, "취소", gifConfirmCancelButtonRect.contains(mouseX, mouseY), false);
    }

    private void renderPanels(DrawContext context, int mouseX, int mouseY) {
        drawPanel(context, topBarRect, 0xD01E3140, 0xD0152430, 0xFF7CA69C);
        drawPanel(context, leftPanelRect, 0xBE1D2F3A, 0xBE15232D, 0xFF59877D);
        drawPanel(context, rightPanelRect, 0xBE1D2F3A, 0xBE15232D, 0xFF59877D);

        context.drawTextWithShadow(this.textRenderer, "HUD 페인터", topBarRect.x + 8, topBarRect.y + 8, 0xFFF1F8F6);
        context.drawTextWithShadow(this.textRenderer, "캔버스 " + state.getCanvasWidth() + "x" + state.getCanvasHeight(), topBarRect.x + 86, topBarRect.y + 8, 0xFFD5E8E3);
        context.drawTextWithShadow(this.textRenderer, "J+K 열기 | Shift+클릭 다중선택", topBarRect.x + 194, topBarRect.y + 8, 0xFFB2CBC4);

        drawButton(context, exportButtonRect, "내보내기", exportButtonRect.contains(mouseX, mouseY), false);
        drawButton(context, importButtonRect, "받기", importButtonRect.contains(mouseX, mouseY), false);
        drawButton(context, undoButtonRect, "실행취소", undoButtonRect.contains(mouseX, mouseY), false);
        drawButton(context, redoButtonRect, "다시실행", redoButtonRect.contains(mouseX, mouseY), false);
        drawButton(context, clearButtonRect, "초기화", clearButtonRect.contains(mouseX, mouseY), false);
        drawButton(context, closeButtonRect, "닫기", closeButtonRect.contains(mouseX, mouseY), false);

        context.drawTextWithShadow(this.textRenderer, "도구", leftPanelRect.x + 8, leftPanelRect.y + 5, 0xFFE3F2EE);
        for (HudTool tool : HudTool.values()) {
            UiRect buttonRect = toolButtons.get(tool);
            drawButton(context, buttonRect, tool.label, buttonRect.contains(mouseX, mouseY), selectedTool == tool);
        }

        int layerTitleY = layerButtons.get(LayerAction.UP).y - 10;
        context.drawTextWithShadow(this.textRenderer, "레이어", leftPanelRect.x + 8, layerTitleY, 0xFFDCEDE8);
        for (LayerAction action : LayerAction.values()) {
            UiRect buttonRect = layerButtons.get(action);
            drawButton(context, buttonRect, action.label, buttonRect.contains(mouseX, mouseY), false);
        }

        int total = state.getElements().size();
        List<Integer> selectedIndices = getValidSelectedIndices(total);
        String selectionText;
        if (selectedIndices.isEmpty()) {
            selectionText = "선택 없음";
        } else if (selectedIndices.size() == 1 && selectedElementIndex >= 0 && selectedElementIndex < total) {
            selectionText = "선택: " + (selectedElementIndex + 1) + " / " + total;
        } else {
            selectionText = "선택: " + selectedIndices.size() + "개 / " + total;
        }
        context.drawTextWithShadow(this.textRenderer, selectionText, leftPanelRect.x + 8, leftPanelRect.bottom() - 12, 0xFFCAE0DB);

        context.drawTextWithShadow(this.textRenderer, "설정", rightPanelRect.x + 8, rightPanelRect.y + 5, 0xFFE3F2EE);
        context.drawTextWithShadow(this.textRenderer, "원형 색상/투명도", rightPanelRect.x + 8, rightPanelRect.y + 17, 0xFF9AB6AE);
        renderCircularColorPicker(context, mouseX, mouseY);

        for (SliderType sliderType : SliderType.values()) {
            drawSlider(context, sliderType, sliders.get(sliderType), mouseX, mouseY);
        }

        drawButton(context, hudMoveButtonRect, "HUD 위치 이동", hudMoveButtonRect.contains(mouseX, mouseY), false);

        drawColorPreview(context);
        drawTextStatus(context);

        context.drawTextWithShadow(this.textRenderer, "Ctrl+Z/Ctrl+Y/Ctrl+Shift+Z | Del 삭제", rightPanelRect.x + 12, rightPanelRect.bottom() - 22, 0xFFBAD3CD);
        context.drawTextWithShadow(this.textRenderer, "V/B/G/R/O/L/I/T, [ ] 크기, Ctrl+A 전체선택", rightPanelRect.x + 12, rightPanelRect.bottom() - 12, 0xFFBAD3CD);
    }

    private TooltipSpec resolveHoveredTooltip(int mouseX, int mouseY) {
        if (importConfirmVisible) {
            if (importConfirmContinueButtonRect.contains(mouseX, mouseY)) {
                return new TooltipSpec("import_continue", "계속", "선택한 ZIP HUD를 가져와 현재 HUD를 덮어씁니다.", "없음");
            }
            if (importConfirmCancelButtonRect.contains(mouseX, mouseY)) {
                return new TooltipSpec("import_cancel", "취소", "가져오기 확인창을 닫고 작업을 유지합니다.", "ESC");
            }
            return null;
        }

        if (gifConfirmVisible) {
            if (gifConfirmContinueButtonRect.contains(mouseX, mouseY)) {
                return new TooltipSpec("gif_continue", "계속", "GIF 경고를 확인하고 이미지를 반영합니다.", "Enter");
            }
            if (gifConfirmCancelButtonRect.contains(mouseX, mouseY)) {
                return new TooltipSpec("gif_cancel", "취소", "GIF 반영을 취소하고 편집기로 돌아갑니다.", "ESC");
            }
            return null;
        }

        if (hudMoveMode) {
            if (hudMoveDoneButtonRect.contains(mouseX, mouseY)) {
                return new TooltipSpec("hud_move_done", "완료", "HUD 위치/크기 조정 모드를 종료합니다.", "없음");
            }
            if (hudScaleDownButtonRect.contains(mouseX, mouseY)) {
                return new TooltipSpec("hud_scale_down", "크기 -5%", "HUD 크기를 5% 줄입니다.", "[ / -");
            }
            if (hudScaleUpButtonRect.contains(mouseX, mouseY)) {
                return new TooltipSpec("hud_scale_up", "크기 +5%", "HUD 크기를 5% 키웁니다.", "] / +");
            }
            return null;
        }

        if (exportButtonRect.contains(mouseX, mouseY)) {
            return new TooltipSpec("export", "내보내기", "현재 HUD를 ZIP 파일로 저장합니다.", "없음");
        }
        if (importButtonRect.contains(mouseX, mouseY)) {
            return new TooltipSpec("import", "받기", "ZIP HUD를 불러와 현재 HUD를 교체합니다.", "없음");
        }
        if (undoButtonRect.contains(mouseX, mouseY)) {
            return new TooltipSpec("undo", "실행취소", "직전 변경을 되돌립니다.", "Ctrl+Z");
        }
        if (redoButtonRect.contains(mouseX, mouseY)) {
            return new TooltipSpec("redo", "다시실행", "되돌린 변경을 다시 적용합니다.", "Ctrl+Y / Ctrl+Shift+Z");
        }
        if (clearButtonRect.contains(mouseX, mouseY)) {
            return new TooltipSpec("clear", "초기화", "HUD를 기본/번들 상태로 초기화합니다.", "없음");
        }
        if (closeButtonRect.contains(mouseX, mouseY)) {
            return new TooltipSpec("close", "닫기", "편집기를 닫고 현재 HUD를 저장합니다.", "ESC");
        }
        if (hudMoveButtonRect.contains(mouseX, mouseY)) {
            return new TooltipSpec("hud_move", "HUD 위치 이동", "HUD 전체 위치 이동과 크기 조정을 엽니다.", "없음");
        }

        for (Map.Entry<HudTool, UiRect> entry : toolButtons.entrySet()) {
            if (entry.getValue().contains(mouseX, mouseY)) {
                return tooltipForTool(entry.getKey());
            }
        }

        for (Map.Entry<LayerAction, UiRect> entry : layerButtons.entrySet()) {
            if (entry.getValue().contains(mouseX, mouseY)) {
                return tooltipForLayer(entry.getKey());
            }
        }

        return null;
    }

    private TooltipSpec tooltipForTool(HudTool tool) {
        return switch (tool) {
            case SELECT -> new TooltipSpec("tool_select", "편집", "요소 선택/다중선택/이동/크기 조절 도구입니다.", "V");
            case BRUSH -> new TooltipSpec("tool_brush", "브러시", "드래그로 자유롭게 그립니다.", "B, [ / ]");
            case FILL -> new TooltipSpec("tool_fill", "페인트", "캔버스 전체를 현재 색상으로 채웁니다.", "G");
            case RECTANGLE -> new TooltipSpec("tool_rectangle", "사각형", "드래그해서 사각형 요소를 생성합니다.", "R");
            case CIRCLE -> new TooltipSpec("tool_circle", "원", "드래그해서 원 요소를 생성합니다.", "O");
            case LINE -> new TooltipSpec("tool_line", "직선", "드래그해서 선 요소를 생성합니다.", "L");
            case IMAGE -> new TooltipSpec("tool_image", "이미지", "드롭/클립보드 이미지로 이미지 요소를 추가합니다.", "I");
            case TEXT -> new TooltipSpec("tool_text", "텍스트", "텍스트 요소를 생성하고 직접 편집합니다.", "T");
            case ERASER -> new TooltipSpec("tool_eraser", "지우개", "요소를 지워서 삭제합니다.", "E, [ / ]");
        };
    }

    private TooltipSpec tooltipForLayer(LayerAction action) {
        return switch (action) {
            case UP -> new TooltipSpec("layer_up", "위로", "선택 요소를 한 레이어 앞으로 올립니다.", "없음");
            case DOWN -> new TooltipSpec("layer_down", "아래로", "선택 요소를 한 레이어 뒤로 내립니다.", "없음");
            case FRONT -> new TooltipSpec("layer_front", "맨앞", "선택 요소를 최상단 레이어로 이동합니다.", "없음");
            case BACK -> new TooltipSpec("layer_back", "맨뒤", "선택 요소를 최하단 레이어로 이동합니다.", "없음");
            case DELETE -> new TooltipSpec("layer_delete", "삭제", "선택 요소를 삭제합니다.", "Delete / Backspace");
        };
    }

    private void updateTooltipHover(TooltipSpec currentTooltip) {
        if (currentTooltip == null) {
            hoveredTooltip = null;
            hoveredTooltipId = "";
            hoveredTooltipSinceMs = 0L;
            return;
        }

        long now = System.currentTimeMillis();
        if (!currentTooltip.id().equals(hoveredTooltipId)) {
            hoveredTooltipId = currentTooltip.id();
            hoveredTooltipSinceMs = now;
        }
        hoveredTooltip = currentTooltip;
    }

    private void renderDelayedTooltip(DrawContext context, int mouseX, int mouseY) {
        if (hoveredTooltip == null || hoveredTooltipSinceMs <= 0L) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - hoveredTooltipSinceMs < BUTTON_TOOLTIP_DELAY_MS) {
            return;
        }

        String line1 = hoveredTooltip.title();
        String line2 = hoveredTooltip.description();
        String line3 = "단축키: " + hoveredTooltip.shortcut();

        int paddingX = 6;
        int paddingY = 4;
        int lineGap = 11;
        int tooltipWidth = Math.max(
                this.textRenderer.getWidth(line1),
                Math.max(this.textRenderer.getWidth(line2), this.textRenderer.getWidth(line3))
        ) + (paddingX * 2);
        int tooltipHeight = (paddingY * 2) + (lineGap * 3) - 1;

        int x = mouseX + 14;
        int y = mouseY + 14;
        if (x + tooltipWidth > this.width - 6) {
            x = this.width - tooltipWidth - 6;
        }
        if (y + tooltipHeight > this.height - 6) {
            y = this.height - tooltipHeight - 6;
        }
        x = Math.max(6, x);
        y = Math.max(6, y);

        context.fillGradient(x, y, x + tooltipWidth, y + tooltipHeight, 0xE42A3947, 0xE41D2A35);
        HudRenderUtil.drawStrokedRect(context, x, y, tooltipWidth, tooltipHeight, 0xFF9FC5BB);
        context.drawTextWithShadow(this.textRenderer, line1, x + paddingX, y + paddingY, 0xFFF2FBF8);
        context.drawTextWithShadow(this.textRenderer, line2, x + paddingX, y + paddingY + lineGap, 0xFFE0EFEA);
        context.drawTextWithShadow(this.textRenderer, line3, x + paddingX, y + paddingY + (lineGap * 2), 0xFFC4DDD6);
    }

    private void drawPanel(DrawContext context, UiRect rect, int topColor, int bottomColor, int borderColor) {
        context.fillGradient(rect.x, rect.y, rect.right(), rect.bottom(), topColor, bottomColor);
        HudRenderUtil.drawStrokedRect(context, rect.x, rect.y, rect.width, rect.height, borderColor);
    }

    private void drawButton(DrawContext context, UiRect rect, String label, boolean hovered, boolean active) {
        int topColor;
        int bottomColor;

        if (active) {
            topColor = hovered ? 0xE066C3A8 : 0xD856B79B;
            bottomColor = hovered ? 0xE03A9076 : 0xD8327C67;
        } else {
            topColor = hovered ? 0xD43C5A72 : 0xC42A4356;
            bottomColor = hovered ? 0xD2273C4E : 0xC41E3040;
        }

        context.fillGradient(rect.x, rect.y, rect.right(), rect.bottom(), topColor, bottomColor);
        HudRenderUtil.drawStrokedRect(context, rect.x, rect.y, rect.width, rect.height, 0xFFA6CAC1);

        int textX = rect.x + (rect.width - this.textRenderer.getWidth(label)) / 2;
        int textY = rect.y + (rect.height - 8) / 2;
        context.drawTextWithShadow(this.textRenderer, label, textX, textY, 0xFFEAF7F3);
    }

    private void drawSlider(DrawContext context, SliderType sliderType, UiRect rect, int mouseX, int mouseY) {
        int minValue = sliderMin(sliderType);
        int maxValue = sliderMax(sliderType);
        int value = getSliderValue(sliderType);
        double ratio = (value - minValue) / (double) Math.max(1, maxValue - minValue);
        int knobX = rect.x + (int) Math.round(ratio * (rect.width - 1));
        int centerY = rect.y + (rect.height / 2);

        context.drawTextWithShadow(this.textRenderer, sliderType.label + " " + value, rect.x, rect.y - 10, 0xFFDCECE8);
        context.fill(rect.x, centerY - 1, rect.right(), centerY + 1, 0xFFA6BBB7);
        context.fill(knobX - 2, rect.y - 1, knobX + 3, rect.bottom() + 1, sliderType.barColor);
        HudRenderUtil.drawStrokedRect(context, rect.x, rect.y, rect.width, rect.height, rect.contains(mouseX, mouseY) ? 0xFFD9F4EC : 0xFF87AAA1);
    }

    private void renderCircularColorPicker(DrawContext context, int mouseX, int mouseY) {
        context.drawTextWithShadow(this.textRenderer, "색상", colorPickerRect.x, colorPickerRect.y - 9, 0xFFDCECE8);
        context.drawTextWithShadow(this.textRenderer, "투명도 " + alpha, colorPickerRect.right() - 58, colorPickerRect.y - 9, 0xFFDCECE8);

        for (int degree = 0; degree < 360; degree += 2) {
            int ringColor = hsvToColor(degree, 1.0F, 1.0F, 255);
            double radians = Math.toRadians(degree);
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);
            for (int radius = hueInnerRadius; radius <= hueOuterRadius; radius += 2) {
                int px = colorCenterX + (int) Math.round(cos * radius);
                int py = colorCenterY + (int) Math.round(sin * radius);
                context.fill(px, py, px + 2, py + 2, ringColor);
            }
        }

        int cell = 3;
        int left = svRect.x;
        int top = svRect.y;
        int right = svRect.right() - 1;
        int bottom = svRect.bottom() - 1;
        for (int y = top; y <= bottom; y += cell) {
            float localValue = 1.0F - ((y - top) / (float) Math.max(1, svRect.height - 1));
            for (int x = left; x <= right; x += cell) {
                float localSaturation = (x - left) / (float) Math.max(1, svRect.width - 1);
                int sampleColor = hsvToColor(hue, localSaturation, localValue, 255);
                context.fill(x, y, Math.min(x + cell, right + 1), Math.min(y + cell, bottom + 1), sampleColor);
            }
        }
        HudRenderUtil.drawStrokedRect(context, svRect.x, svRect.y, svRect.width, svRect.height, 0xFFE3F6F0);

        int[] rgb = hsvToRgb(hue, saturation, value);
        for (int degree = 0; degree < 360; degree += 2) {
            int ringColor = HudRenderUtil.argb(Math.round((degree / 359.0F) * 255.0F), rgb[0], rgb[1], rgb[2]);
            double radians = Math.toRadians(degree);
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);
            for (int radius = alphaInnerRadius; radius <= alphaOuterRadius; radius += 2) {
                int px = colorCenterX + (int) Math.round(cos * radius);
                int py = colorCenterY + (int) Math.round(sin * radius);
                context.fill(px, py, px + 2, py + 2, ringColor);
            }
        }

        drawPickerHandles(context);

        if (colorPickerRect.contains(mouseX, mouseY) || pickerDragMode != PickerDragMode.NONE) {
            String rgbText = "RGB " + rgb[0] + ", " + rgb[1] + ", " + rgb[2];
            context.fill(colorPickerRect.x, colorPickerRect.bottom() + 2, colorPickerRect.x + this.textRenderer.getWidth(rgbText) + 6, colorPickerRect.bottom() + 14, 0xAA101820);
            context.drawTextWithShadow(this.textRenderer, rgbText, colorPickerRect.x + 3, colorPickerRect.bottom() + 4, 0xFFEAF7F3);
        }
    }

    private void drawPickerHandles(DrawContext context) {
        double hueRadians = Math.toRadians(hue);
        int hueRadius = (hueInnerRadius + hueOuterRadius) / 2;
        int hueX = colorCenterX + (int) Math.round(Math.cos(hueRadians) * hueRadius);
        int hueY = colorCenterY + (int) Math.round(Math.sin(hueRadians) * hueRadius);
        context.fill(hueX - 3, hueY - 3, hueX + 4, hueY + 4, 0xFF081014);
        context.fill(hueX - 2, hueY - 2, hueX + 3, hueY + 3, 0xFFF8FFFD);

        int svX = svRect.x + Math.round(saturation * Math.max(1, svRect.width - 1));
        int svY = svRect.y + Math.round((1.0F - value) * Math.max(1, svRect.height - 1));
        context.fill(svX - 3, svY - 3, svX + 4, svY + 4, 0xFF081014);
        context.fill(svX - 2, svY - 2, svX + 3, svY + 3, 0xFFF8FFFD);

        float alphaAngle = (alpha / 255.0F) * 360.0F;
        double alphaRadians = Math.toRadians(alphaAngle);
        int alphaRadius = (alphaInnerRadius + alphaOuterRadius) / 2;
        int alphaX = colorCenterX + (int) Math.round(Math.cos(alphaRadians) * alphaRadius);
        int alphaY = colorCenterY + (int) Math.round(Math.sin(alphaRadians) * alphaRadius);
        context.fill(alphaX - 3, alphaY - 3, alphaX + 4, alphaY + 4, 0xFF081014);
        context.fill(alphaX - 2, alphaY - 2, alphaX + 3, alphaY + 3, 0xFFF8FFFD);
    }

    private void drawColorPreview(DrawContext context) {
        int tile = 6;
        for (int y = colorPreviewRect.y; y < colorPreviewRect.bottom(); y += tile) {
            for (int x = colorPreviewRect.x; x < colorPreviewRect.right(); x += tile) {
                int color = ((x / tile) + (y / tile)) % 2 == 0 ? 0x44FFFFFF : 0x44000000;
                context.fill(x, y, Math.min(x + tile, colorPreviewRect.right()), Math.min(y + tile, colorPreviewRect.bottom()), color);
            }
        }
        context.fill(colorPreviewRect.x, colorPreviewRect.y, colorPreviewRect.right(), colorPreviewRect.bottom(), currentColor());
        HudRenderUtil.drawStrokedRect(context, colorPreviewRect.x, colorPreviewRect.y, colorPreviewRect.width, colorPreviewRect.height, 0xFFC7E0DA);
    }

    private void drawTextStatus(DrawContext context) {
        // 상태 박스는 사용자의 심박수다. 지금 뭘 하는지 항상 보이게.
        context.fillGradient(textStatusRect.x, textStatusRect.y, textStatusRect.right(), textStatusRect.bottom(), 0xB8243A4A, 0xB81A2D38);
        HudRenderUtil.drawStrokedRect(context, textStatusRect.x, textStatusRect.y, textStatusRect.width, textStatusRect.height, 0xFF8BB0A7);

        if (editingTextElementIndex >= 0) {
            context.drawTextWithShadow(this.textRenderer, "텍스트 편집 중 (Enter 종료)", textStatusRect.x + 4, textStatusRect.y + 5, 0xFFF1F9F6);
            String preview = trimToWidth(editingTextBuffer, textStatusRect.width - 8);
            context.drawTextWithShadow(this.textRenderer, preview, textStatusRect.x + 4, textStatusRect.y + 18, 0xFFE6F4F0);
        } else {
            context.drawTextWithShadow(this.textRenderer, "텍스트: 클릭 시 생성/편집", textStatusRect.x + 4, textStatusRect.y + 5, 0xFFDCECE8);
            context.drawTextWithShadow(this.textRenderer, "생성 직후 바로 입력 가능", textStatusRect.x + 4, textStatusRect.y + 18, 0xFFC4DDD8);
        }

        String imageLine = trimToWidth(imageStatusText, textStatusRect.width - 8);
        context.drawTextWithShadow(this.textRenderer, imageLine, textStatusRect.x + 4, textStatusRect.y + 32, 0xFFBED8D2);
    }

    private void copyEditingTextToClipboard() {
        if (editingTextElementIndex < 0) {
            return;
        }

        String copied = hasTextSelection()
                ? editingTextBuffer.substring(selectionMinIndex(), selectionMaxIndex())
                : editingTextBuffer;
        if (copied.isEmpty()) {
            return;
        }
        setClipboardText(copied);
    }

    private void cutEditingTextToClipboard() {
        if (editingTextElementIndex < 0) {
            return;
        }

        if (hasTextSelection()) {
            String copied = editingTextBuffer.substring(selectionMinIndex(), selectionMaxIndex());
            if (!copied.isEmpty()) {
                setClipboardText(copied);
            }
            deleteSelectionAtCaret();
            applyTextEditLive();
            return;
        }

        if (!editingTextBuffer.isEmpty()) {
            setClipboardText(editingTextBuffer);
            editingTextBuffer = "";
            editingCaretIndex = 0;
            clearTextSelection();
            applyTextEditLive();
        }
    }

    private void pasteTextIntoEditing() {
        if (editingTextElementIndex < 0) {
            return;
        }

        String clipboardText = getClipboardText();
        if (clipboardText == null || clipboardText.isEmpty()) {
            return;
        }
        if (clipboardText.startsWith(CLIPBOARD_ELEMENTS_PREFIX)) {
            return;
        }

        String normalized = clipboardText
                .replace("\r\n", " ")
                .replace('\n', ' ')
                .replace('\r', ' ');
        insertTextAtCaret(normalized);
        applyTextEditLive();
    }

    private void copySelectedElementsToClipboard() {
        List<HudElement> elements = state.getElements();
        List<Integer> selectedIndices = getSortedSelectedIndices(elements.size());
        if (selectedIndices.isEmpty()) {
            imageStatusText = "복사 실패: 선택된 요소가 없습니다";
            return;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        for (int index : selectedIndices) {
            HudElement.Bounds bounds = elements.get(index).bounds();
            minX = Math.min(minX, bounds.minX());
            minY = Math.min(minY, bounds.minY());
        }

        JsonArray encodedElements = new JsonArray();
        for (int index : selectedIndices) {
            JsonObject encoded = encodeClipboardElement(elements.get(index), minX, minY);
            if (encoded != null) {
                encodedElements.add(encoded);
            }
        }
        if (encodedElements.isEmpty()) {
            imageStatusText = "복사 실패: 지원되지 않는 요소입니다";
            return;
        }

        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.add("elements", encodedElements);
        setClipboardText(CLIPBOARD_ELEMENTS_PREFIX + root);
        imageStatusText = "요소 복사: " + encodedElements.size() + "개";
    }

    private boolean pasteElementsFromClipboard() {
        String clipboardText = getClipboardText();
        if (clipboardText == null || !clipboardText.startsWith(CLIPBOARD_ELEMENTS_PREFIX)) {
            return false;
        }

        String payload = clipboardText.substring(CLIPBOARD_ELEMENTS_PREFIX.length());
        JsonObject root;
        try {
            root = JsonParser.parseString(payload).getAsJsonObject();
        } catch (Exception ignored) {
            return false;
        }

        JsonArray encodedElements = root.getAsJsonArray("elements");
        if (encodedElements == null || encodedElements.isEmpty()) {
            return false;
        }

        int offset = consumePasteOffset();
        int pasteX = clampLocalX(defaultPasteX() + offset);
        int pasteY = clampLocalY(defaultPasteY() + offset);

        List<HudElement> decodedElements = new ArrayList<>();
        for (JsonElement element : encodedElements) {
            if (!(element instanceof JsonObject object)) {
                continue;
            }
            HudElement decoded = decodeClipboardElement(object, pasteX, pasteY);
            if (decoded != null) {
                decodedElements.add(decoded);
            }
        }
        if (decodedElements.isEmpty()) {
            return false;
        }

        int before = state.getElements().size();
        state.addElements(decodedElements);
        selectNewElements(before, decodedElements.size());
        selectedTool = HudTool.SELECT;
        imageStatusText = "요소 붙여넣기: " + decodedElements.size() + "개";
        return true;
    }

    private boolean pasteImageFromClipboard() {
        Transferable clipboardContents = getClipboardContents();
        if (clipboardContents == null) {
            return false;
        }

        try {
            if (clipboardContents.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                Object data = clipboardContents.getTransferData(DataFlavor.javaFileListFlavor);
                if (data instanceof List<?> files) {
                    for (Object item : files) {
                        if (!(item instanceof java.io.File file)) {
                            continue;
                        }
                        CustomHudImageTextureManager.TextureInfo info = CustomHudImageTextureManager.preload(file.toPath());
                        if (info != null) {
                            if (isGifSourcePath(info.sourcePath())) {
                                queueGifConfirm(info, GifPendingMode.PASTE_IMAGE_ELEMENT);
                                return true;
                            }
                            placePastedImage(info);
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        try {
            if (!clipboardContents.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                return false;
            }
            Object rawImage = clipboardContents.getTransferData(DataFlavor.imageFlavor);
            if (!(rawImage instanceof Image awtImage)) {
                return false;
            }
            BufferedImage buffered = toBufferedImage(awtImage);
            if (buffered == null) {
                return false;
            }
            CustomHudImageTextureManager.TextureInfo info = CustomHudImageTextureManager.preload(buffered);
            if (info == null) {
                return false;
            }
            placePastedImage(info);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean pasteTextElementFromClipboard() {
        String clipboardText = getClipboardText();
        if (clipboardText == null || clipboardText.isBlank()) {
            return false;
        }
        if (clipboardText.startsWith(CLIPBOARD_ELEMENTS_PREFIX)) {
            return false;
        }

        String normalized = clipboardText
                .replace("\r\n", " ")
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();
        if (normalized.isEmpty()) {
            return false;
        }
        if (normalized.length() > MAX_TEXT_LENGTH) {
            normalized = normalized.substring(0, MAX_TEXT_LENGTH);
        }

        int offset = consumePasteOffset();
        int x = clampLocalX(defaultPasteX() + offset);
        int y = clampLocalY(defaultPasteY() + offset);
        state.addElement(new HudElement.TextLabel(x, y, normalized, currentColor(), textFontSize));
        setSingleSelection(state.getElements().size() - 1);
        selectedTool = HudTool.SELECT;
        imageStatusText = "텍스트 붙여넣기 완료";
        return true;
    }

    private void placePastedImage(CustomHudImageTextureManager.TextureInfo info) {
        if (info == null) {
            return;
        }
        placePastedImage(info.sourcePath(), info.sourceWidth(), info.sourceHeight());
    }

    private void placePastedImage(String sourcePath, int sourceWidth, int sourceHeight) {
        int resolvedSourceWidth = Math.max(1, sourceWidth);
        int resolvedSourceHeight = Math.max(1, sourceHeight);
        int targetWidth = resolvedSourceWidth;
        int targetHeight = resolvedSourceHeight;
        int maxDimension = Math.max(resolvedSourceWidth, resolvedSourceHeight);
        if (maxDimension > MAX_IMAGE_PRESET) {
            float scale = MAX_IMAGE_PRESET / (float) maxDimension;
            targetWidth = Math.max(1, Math.round(resolvedSourceWidth * scale));
            targetHeight = Math.max(1, Math.round(resolvedSourceHeight * scale));
        }

        int offset = consumePasteOffset();
        int x = clampLocalX((defaultPasteX() - (targetWidth / 2)) + offset);
        int y = clampLocalY((defaultPasteY() - (targetHeight / 2)) + offset);
        state.addElement(new HudElement.ImageSprite(x, y, targetWidth, targetHeight, sourcePath));
        setSingleSelection(state.getElements().size() - 1);
        selectedTool = HudTool.SELECT;
        imageStatusText = "이미지 붙여넣기 완료";
    }

    private List<Integer> getSortedSelectedIndices(int size) {
        List<Integer> selected = new ArrayList<>(getValidSelectedIndices(size));
        selected.sort(Integer::compareTo);
        return selected;
    }

    private int defaultPasteX() {
        int width = visibleCanvasWidth > 0 ? visibleCanvasWidth : state.getCanvasWidth();
        return Math.max(0, width / 2);
    }

    private int defaultPasteY() {
        int height = visibleCanvasHeight > 0 ? visibleCanvasHeight : state.getCanvasHeight();
        return Math.max(0, height / 2);
    }

    private int consumePasteOffset() {
        int offset = (pasteSequence % PASTE_OFFSET_WRAP) * PASTE_OFFSET_STEP;
        pasteSequence++;
        return offset;
    }

    private void selectNewElements(int startIndex, int count) {
        clearElementSelection();
        if (count <= 0) {
            return;
        }
        for (int i = 0; i < count; i++) {
            selectedElementIndices.add(startIndex + i);
        }
        selectedElementIndex = selectedElementIndices.get(selectedElementIndices.size() - 1);
    }

    private JsonObject encodeClipboardElement(HudElement element, int anchorX, int anchorY) {
        if (element == null) {
            return null;
        }

        JsonObject object = new JsonObject();
        if (element instanceof HudElement.BrushStroke stroke) {
            object.addProperty("type", "brush");
            object.addProperty("size", stroke.size());
            object.addProperty("color", stroke.color());
            JsonArray points = new JsonArray();
            for (HudElement.HudPoint point : stroke.points()) {
                JsonObject encodedPoint = new JsonObject();
                encodedPoint.addProperty("x", point.x() - anchorX);
                encodedPoint.addProperty("y", point.y() - anchorY);
                points.add(encodedPoint);
            }
            object.add("points", points);
            return object;
        }
        if (element instanceof HudElement.FillRect fillRect) {
            object.addProperty("type", "fill");
            object.addProperty("x", fillRect.x() - anchorX);
            object.addProperty("y", fillRect.y() - anchorY);
            object.addProperty("width", fillRect.width());
            object.addProperty("height", fillRect.height());
            object.addProperty("color", fillRect.color());
            return object;
        }
        if (element instanceof HudElement.Rectangle rectangle) {
            object.addProperty("type", "rectangle");
            object.addProperty("x1", rectangle.x1() - anchorX);
            object.addProperty("y1", rectangle.y1() - anchorY);
            object.addProperty("x2", rectangle.x2() - anchorX);
            object.addProperty("y2", rectangle.y2() - anchorY);
            object.addProperty("color", rectangle.color());
            return object;
        }
        if (element instanceof HudElement.Circle circle) {
            object.addProperty("type", "circle");
            object.addProperty("x1", circle.x1() - anchorX);
            object.addProperty("y1", circle.y1() - anchorY);
            object.addProperty("x2", circle.x2() - anchorX);
            object.addProperty("y2", circle.y2() - anchorY);
            object.addProperty("color", circle.color());
            return object;
        }
        if (element instanceof HudElement.Line line) {
            object.addProperty("type", "line");
            object.addProperty("x1", line.x1() - anchorX);
            object.addProperty("y1", line.y1() - anchorY);
            object.addProperty("x2", line.x2() - anchorX);
            object.addProperty("y2", line.y2() - anchorY);
            object.addProperty("size", line.size());
            object.addProperty("color", line.color());
            return object;
        }
        if (element instanceof HudElement.TextLabel textLabel) {
            object.addProperty("type", "text");
            object.addProperty("x", textLabel.x() - anchorX);
            object.addProperty("y", textLabel.y() - anchorY);
            object.addProperty("text", textLabel.text());
            object.addProperty("color", textLabel.color());
            object.addProperty("fontSize", textLabel.fontSize());
            return object;
        }
        if (element instanceof HudElement.ImageSprite imageSprite) {
            object.addProperty("type", "image");
            object.addProperty("x", imageSprite.x() - anchorX);
            object.addProperty("y", imageSprite.y() - anchorY);
            object.addProperty("width", imageSprite.width());
            object.addProperty("height", imageSprite.height());
            object.addProperty("sourcePath", imageSprite.sourcePath());
            object.addProperty("clipOnResizeShrink", imageSprite.clipOnResizeShrink());
            return object;
        }
        return null;
    }

    private HudElement decodeClipboardElement(JsonObject object, int offsetX, int offsetY) {
        if (object == null || !object.has("type")) {
            return null;
        }

        String type = object.get("type").getAsString();
        try {
            return switch (type) {
                case "brush" -> {
                    JsonArray points = object.getAsJsonArray("points");
                    List<HudElement.HudPoint> decoded = new ArrayList<>();
                    if (points != null) {
                        for (JsonElement pointElement : points) {
                            if (!(pointElement instanceof JsonObject point)) {
                                continue;
                            }
                            int x = Math.max(0, point.get("x").getAsInt() + offsetX);
                            int y = Math.max(0, point.get("y").getAsInt() + offsetY);
                            decoded.add(new HudElement.HudPoint(x, y));
                        }
                    }
                    if (decoded.isEmpty()) {
                        yield null;
                    }
                    int size = object.get("size").getAsInt();
                    int color = object.get("color").getAsInt();
                    yield new HudElement.BrushStroke(decoded, size, color);
                }
                case "fill" -> new HudElement.FillRect(
                        Math.max(0, object.get("x").getAsInt() + offsetX),
                        Math.max(0, object.get("y").getAsInt() + offsetY),
                        object.get("width").getAsInt(),
                        object.get("height").getAsInt(),
                        object.get("color").getAsInt()
                );
                case "rectangle" -> new HudElement.Rectangle(
                        Math.max(0, object.get("x1").getAsInt() + offsetX),
                        Math.max(0, object.get("y1").getAsInt() + offsetY),
                        Math.max(0, object.get("x2").getAsInt() + offsetX),
                        Math.max(0, object.get("y2").getAsInt() + offsetY),
                        object.get("color").getAsInt()
                );
                case "circle" -> new HudElement.Circle(
                        Math.max(0, object.get("x1").getAsInt() + offsetX),
                        Math.max(0, object.get("y1").getAsInt() + offsetY),
                        Math.max(0, object.get("x2").getAsInt() + offsetX),
                        Math.max(0, object.get("y2").getAsInt() + offsetY),
                        object.get("color").getAsInt()
                );
                case "line" -> new HudElement.Line(
                        Math.max(0, object.get("x1").getAsInt() + offsetX),
                        Math.max(0, object.get("y1").getAsInt() + offsetY),
                        Math.max(0, object.get("x2").getAsInt() + offsetX),
                        Math.max(0, object.get("y2").getAsInt() + offsetY),
                        object.get("size").getAsInt(),
                        object.get("color").getAsInt()
                );
                case "text" -> new HudElement.TextLabel(
                        Math.max(0, object.get("x").getAsInt() + offsetX),
                        Math.max(0, object.get("y").getAsInt() + offsetY),
                        object.get("text").getAsString(),
                        object.get("color").getAsInt(),
                        object.has("fontSize")
                                ? object.get("fontSize").getAsInt()
                                : HudElement.TextLabel.DEFAULT_FONT_SIZE
                );
                case "image" -> new HudElement.ImageSprite(
                        Math.max(0, object.get("x").getAsInt() + offsetX),
                        Math.max(0, object.get("y").getAsInt() + offsetY),
                        object.get("width").getAsInt(),
                        object.get("height").getAsInt(),
                        object.get("sourcePath").getAsString(),
                        object.has("clipOnResizeShrink") && object.get("clipOnResizeShrink").getAsBoolean()
                );
                default -> null;
            };
        } catch (Exception ignored) {
            return null;
        }
    }

    private BufferedImage toBufferedImage(Image image) {
        if (image == null) {
            return null;
        }
        if (image instanceof BufferedImage bufferedImage) {
            return bufferedImage;
        }

        int width = image.getWidth(null);
        int height = image.getHeight(null);
        if (width <= 0 || height <= 0) {
            return null;
        }

        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = buffered.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return buffered;
    }

    private Transferable getClipboardContents() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            return clipboard.getContents(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void setClipboardText(String text) {
        fallbackClipboardText = text == null ? "" : text;
        if (text == null) {
            return;
        }

        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(text), null);
        } catch (Exception ignored) {
        }
    }

    private String getClipboardText() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable contents = clipboard.getContents(null);
            if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                Object value = contents.getTransferData(DataFlavor.stringFlavor);
                if (value instanceof String text) {
                    fallbackClipboardText = text;
                    return text;
                }
            }
        } catch (Exception ignored) {
        }

        return fallbackClipboardText.isBlank() ? null : fallbackClipboardText;
    }

    private void clearElementSelection() {
        selectedElementIndices.clear();
        selectedElementIndex = -1;
    }

    private void setSingleSelection(int index) {
        selectedElementIndices.clear();
        if (index >= 0) {
            selectedElementIndices.add(index);
            selectedElementIndex = index;
            syncTextFontSizeFromSelection();
            return;
        }
        selectedElementIndex = -1;
    }

    private void addElementSelection(int index) {
        if (index < 0) {
            return;
        }
        if (!selectedElementIndices.contains(index)) {
            selectedElementIndices.add(index);
        }
        selectedElementIndex = index;
        syncTextFontSizeFromSelection();
    }

    private void toggleElementSelection(int index) {
        if (index < 0) {
            return;
        }

        if (selectedElementIndices.contains(index)) {
            selectedElementIndices.remove((Integer) index);
            if (selectedElementIndex == index) {
                selectedElementIndex = selectedElementIndices.isEmpty()
                        ? -1
                        : selectedElementIndices.get(selectedElementIndices.size() - 1);
                syncTextFontSizeFromSelection();
            }
            return;
        }

        addElementSelection(index);
    }

    private List<Integer> getValidSelectedIndices(int size) {
        if (size <= 0) {
            clearElementSelection();
            return List.of();
        }

        List<Integer> valid = new ArrayList<>();
        for (Integer index : selectedElementIndices) {
            if (index == null || index < 0 || index >= size || valid.contains(index)) {
                continue;
            }
            valid.add(index);
        }

        if (valid.size() != selectedElementIndices.size()) {
            selectedElementIndices.clear();
            selectedElementIndices.addAll(valid);
        }

        if (selectedElementIndex >= 0 && !valid.contains(selectedElementIndex)) {
            selectedElementIndex = valid.isEmpty() ? -1 : valid.get(valid.size() - 1);
            syncTextFontSizeFromSelection();
        } else if (selectedElementIndex < 0 && !valid.isEmpty()) {
            selectedElementIndex = valid.get(valid.size() - 1);
            syncTextFontSizeFromSelection();
        }

        return valid;
    }

    private void selectAllElements() {
        int size = state.getElements().size();
        clearElementSelection();
        for (int i = 0; i < size; i++) {
            selectedElementIndices.add(i);
        }
        if (!selectedElementIndices.isEmpty()) {
            selectedElementIndex = selectedElementIndices.get(selectedElementIndices.size() - 1);
            syncTextFontSizeFromSelection();
        }
    }

    private void deleteSelectedElements() {
        List<Integer> selectedIndices = getValidSelectedIndices(state.getElements().size());
        if (selectedIndices.isEmpty()) {
            return;
        }

        if (state.removeElements(selectedIndices)) {
            if (editingTextElementIndex >= 0 && selectedIndices.contains(editingTextElementIndex)) {
                stopTextEditing();
            }
            clearElementSelection();
        }
    }

    private boolean translateDraggedSelection(int deltaX, int deltaY) {
        if (draggingElementIndex < 0) {
            return false;
        }

        List<Integer> selectedIndices = getValidSelectedIndices(state.getElements().size());
        if (selectedIndices.isEmpty() || !selectedIndices.contains(draggingElementIndex)) {
            setSingleSelection(draggingElementIndex);
            return state.translateElement(draggingElementIndex, deltaX, deltaY);
        }

        if (selectedIndices.size() == 1) {
            return state.translateElement(draggingElementIndex, deltaX, deltaY);
        }
        return state.translateElements(selectedIndices, deltaX, deltaY);
    }

    private void applyMarqueeSelection() {
        if (dragMode != DragMode.SELECT_MARQUEE) {
            return;
        }

        int minX = Math.min(dragStartX, dragCurrentX);
        int minY = Math.min(dragStartY, dragCurrentY);
        int maxX = Math.max(dragStartX, dragCurrentX);
        int maxY = Math.max(dragStartY, dragCurrentY);

        boolean draggedEnough = Math.abs(dragCurrentX - dragStartX) > 1 || Math.abs(dragCurrentY - dragStartY) > 1;
        if (!draggedEnough) {
            if (!marqueeAdditiveSelection) {
                clearElementSelection();
            }
            if (editingTextElementIndex >= 0 && !selectedElementIndices.contains(editingTextElementIndex)) {
                stopTextEditing();
            }
            return;
        }

        List<HudElement> elements = state.getElements();
        if (!marqueeAdditiveSelection) {
            clearElementSelection();
        }
        for (int i = 0; i < elements.size(); i++) {
            HudElement.Bounds bounds = elements.get(i).bounds();
            if (intersectsSelection(bounds, minX, minY, maxX, maxY)) {
                addElementSelection(i);
            }
        }

        if (editingTextElementIndex >= 0 && !selectedElementIndices.contains(editingTextElementIndex)) {
            stopTextEditing();
        }
    }

    private boolean intersectsSelection(HudElement.Bounds bounds, int minX, int minY, int maxX, int maxY) {
        return bounds.maxX() >= minX
                && bounds.minX() <= maxX
                && bounds.maxY() >= minY
                && bounds.minY() <= maxY;
    }

    private boolean handleToolShortcutKey(int keyCode) {
        switch (keyCode) {
            case InputUtil.GLFW_KEY_V -> selectedTool = HudTool.SELECT;
            case InputUtil.GLFW_KEY_B -> selectedTool = HudTool.BRUSH;
            case InputUtil.GLFW_KEY_G -> selectedTool = HudTool.FILL;
            case InputUtil.GLFW_KEY_R -> selectedTool = HudTool.RECTANGLE;
            case InputUtil.GLFW_KEY_O -> selectedTool = HudTool.CIRCLE;
            case InputUtil.GLFW_KEY_L -> selectedTool = HudTool.LINE;
            case InputUtil.GLFW_KEY_I -> selectedTool = HudTool.IMAGE;
            case InputUtil.GLFW_KEY_T -> selectedTool = HudTool.TEXT;
            case InputUtil.GLFW_KEY_E -> selectedTool = HudTool.ERASER;
            case InputUtil.GLFW_KEY_LEFT_BRACKET -> {
                if (selectedTool == HudTool.ERASER) {
                    eraserSize = HudRenderUtil.clamp(eraserSize - 1, sliderMin(SliderType.ERASER_SIZE), sliderMax(SliderType.ERASER_SIZE));
                } else {
                    brushSize = HudRenderUtil.clamp(brushSize - 1, sliderMin(SliderType.SIZE), sliderMax(SliderType.SIZE));
                }
                return true;
            }
            case InputUtil.GLFW_KEY_RIGHT_BRACKET -> {
                if (selectedTool == HudTool.ERASER) {
                    eraserSize = HudRenderUtil.clamp(eraserSize + 1, sliderMin(SliderType.ERASER_SIZE), sliderMax(SliderType.ERASER_SIZE));
                } else {
                    brushSize = HudRenderUtil.clamp(brushSize + 1, sliderMin(SliderType.SIZE), sliderMax(SliderType.SIZE));
                }
                return true;
            }
            default -> {
                return false;
            }
        }

        resetDragState();
        if (selectedTool != HudTool.TEXT) {
            stopTextEditing();
        }
        return true;
    }

    private void applyLayerAction(LayerAction layerAction) {
        int size = state.getElements().size();
        List<Integer> selectedIndices = getValidSelectedIndices(size);
        if (selectedIndices.isEmpty()) {
            return;
        }

        if (layerAction == LayerAction.DELETE) {
            if (state.removeElements(selectedIndices)) {
                if (editingTextElementIndex >= 0 && selectedIndices.contains(editingTextElementIndex)) {
                    stopTextEditing();
                }
                clearElementSelection();
            }
            return;
        }

        if (selectedElementIndex < 0 || selectedElementIndex >= size) {
            selectedElementIndex = selectedIndices.get(selectedIndices.size() - 1);
        }

        int originalIndex = selectedElementIndex;
        switch (layerAction) {
            case UP -> setSingleSelection(state.moveElement(selectedElementIndex, selectedElementIndex + 1));
            case DOWN -> setSingleSelection(state.moveElement(selectedElementIndex, selectedElementIndex - 1));
            case FRONT -> setSingleSelection(state.moveElement(selectedElementIndex, size - 1));
            case BACK -> setSingleSelection(state.moveElement(selectedElementIndex, 0));
            case DELETE -> {
            }
        }

        if (editingTextElementIndex == originalIndex) {
            editingTextElementIndex = selectedElementIndex;
        }
    }

    private void eraseAt(int localX, int localY) {
        int eraserRadius = Math.max(1, Math.min(40, eraserSize));
        if (state.eraseAt(localX, localY, eraserRadius)) {
            clearElementSelection();
        }
    }

    private boolean handleColorPickerPointer(double mouseX, double mouseY, boolean beginDrag) {
        double deltaX = mouseX - colorCenterX;
        double deltaY = mouseY - colorCenterY;
        double distance = Math.sqrt((deltaX * deltaX) + (deltaY * deltaY));

        if (beginDrag) {
            if (distance >= hueInnerRadius && distance <= hueOuterRadius) {
                pickerDragMode = PickerDragMode.HUE;
            } else if (distance >= alphaInnerRadius && distance <= alphaOuterRadius) {
                pickerDragMode = PickerDragMode.ALPHA;
            } else if (svRect.contains(mouseX, mouseY)) {
                pickerDragMode = PickerDragMode.SATURATION_VALUE;
            } else {
                return false;
            }
        }

        if (pickerDragMode == PickerDragMode.NONE) {
            return false;
        }

        switch (pickerDragMode) {
            case HUE -> hue = angleFromVector(deltaX, deltaY);
            case ALPHA -> {
                float alphaAngle = angleFromVector(deltaX, deltaY);
                alpha = HudRenderUtil.clamp(Math.round((alphaAngle / 360.0F) * 255.0F), 0, 255);
            }
            case SATURATION_VALUE -> {
                saturation = clamp01((float) ((mouseX - svRect.x) / Math.max(1.0D, svRect.width - 1)));
                float valueProgress = clamp01((float) ((mouseY - svRect.y) / Math.max(1.0D, svRect.height - 1)));
                value = 1.0F - valueProgress;
            }
            case NONE -> {
            }
        }

        applyActiveTextColor();
        return true;
    }

    private boolean handleSelectModeClick(int localX, int localY, boolean doubleClick, boolean shiftDown) {
        List<HudElement> elements = state.getElements();
        List<Integer> selectedIndices = getValidSelectedIndices(elements.size());

        if (!shiftDown && selectedIndices.size() == 1 && selectedElementIndex >= 0 && selectedElementIndex < elements.size()) {
            ResizeHandle selectedHandle = findResizeHandleAt(selectedElementIndex, localX, localY, elements.get(selectedElementIndex));
            if (selectedHandle != ResizeHandle.NONE) {
                beginElementResize(selectedElementIndex, selectedHandle);
                return true;
            }
        }

        int targetIndex = state.findTopElementIndexAt(localX, localY);
        draggingElementIndex = -1;
        resetResizeState();
        dragMode = DragMode.NONE;

        if (targetIndex < 0) {
            if (!shiftDown) {
                clearElementSelection();
                stopTextEditing();
            }
            dragMode = DragMode.SELECT_MARQUEE;
            dragStartX = localX;
            dragStartY = localY;
            dragCurrentX = localX;
            dragCurrentY = localY;
            marqueeAdditiveSelection = shiftDown;
            return true;
        }

        HudElement element = elements.get(targetIndex);

        if (shiftDown) {
            toggleElementSelection(targetIndex);
            if (editingTextElementIndex >= 0 && !selectedElementIndices.contains(editingTextElementIndex)) {
                stopTextEditing();
            }
            return true;
        }

        if (!selectedElementIndices.contains(targetIndex) || selectedElementIndices.size() <= 1) {
            setSingleSelection(targetIndex);
        } else {
            selectedElementIndex = targetIndex;
            syncTextFontSizeFromSelection();
        }

        ResizeHandle handle = findResizeHandleAt(targetIndex, localX, localY, element);
        if (handle != ResizeHandle.NONE && selectedElementIndices.size() == 1) {
            beginElementResize(targetIndex, handle);
            stopTextEditing();
            return true;
        }

        if (doubleClick && selectedElementIndices.size() == 1 && element instanceof HudElement.TextLabel textLabel) {
            startTextEditing(targetIndex, textLabel, textLabel.text().length());
            selectAllEditingText();
            return true;
        }

        draggingElementIndex = targetIndex;
        dragLastElementX = localX;
        dragLastElementY = localY;
        stopTextEditing();
        return true;
    }

    private boolean handleTextToolClick(int localX, int localY, boolean doubleClick) {
        int textIndex = findTopTextElementIndexAt(localX, localY);
        if (textIndex >= 0) {
            setSingleSelection(textIndex);
            draggingElementIndex = -1;
            HudElement element = state.getElements().get(textIndex);
            if (element instanceof HudElement.TextLabel textLabel) {
                int caretIndex = findTextCaretIndex(textIndex, textLabel, localX);
                startTextEditing(textIndex, textLabel, caretIndex);
                draggingTextSelection = true;
                editingSelectionStart = caretIndex;
                editingSelectionEnd = caretIndex;
                if (doubleClick) {
                    selectAllEditingText();
                    draggingTextSelection = false;
                }
            }
            return true;
        }

        stopTextEditing();
        state.addElement(new HudElement.TextLabel(localX, localY, "", currentColor(), textFontSize));
        int createdIndex = state.getElements().size() - 1;
        setSingleSelection(createdIndex);
        HudElement created = state.getElements().get(createdIndex);
        if (created instanceof HudElement.TextLabel textLabel) {
            startTextEditing(createdIndex, textLabel, 0);
        }
        return true;
    }

    private boolean handleImageToolClick(int localX, int localY) {
        if (pendingImagePath.isBlank()) {
            imageStatusText = "이미지 없음: 파일을 먼저 드래그하세요";
            return true;
        }

        // 클릭한 위치를 좌상단으로 쓰는 단순 규칙. 감으로도 예측 가능해야 편하다.
        state.addElement(new HudElement.ImageSprite(localX, localY, pendingImageWidth, pendingImageHeight, pendingImagePath));
        setSingleSelection(state.getElements().size() - 1);
        imageStatusText = "이미지 생성 완료";
        return true;
    }

    private void preparePendingImageTool(CustomHudImageTextureManager.TextureInfo info) {
        if (info == null) {
            return;
        }
        preparePendingImageTool(info.sourcePath(), info.sourceWidth(), info.sourceHeight());
    }

    private void preparePendingImageTool(String sourcePath, int sourceWidth, int sourceHeight) {
        pendingImagePath = sourcePath == null ? "" : sourcePath;
        int[] scaled = scaleForImagePreset(sourceWidth, sourceHeight);
        pendingImageWidth = scaled[0];
        pendingImageHeight = scaled[1];
        selectedTool = HudTool.IMAGE;
        imageStatusText = "업로드 완료: 캔버스를 클릭해 이미지 생성";
    }

    private int[] scaleForImagePreset(int sourceWidth, int sourceHeight) {
        int width = Math.max(1, sourceWidth);
        int height = Math.max(1, sourceHeight);
        int maxDimension = Math.max(width, height);
        if (maxDimension <= MAX_IMAGE_PRESET) {
            return new int[]{width, height};
        }
        float scale = MAX_IMAGE_PRESET / (float) maxDimension;
        return new int[]{
                Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale))
        };
    }

    private boolean isGifSourcePath(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return false;
        }
        return sourcePath.toLowerCase().endsWith(".gif");
    }

    private void queueGifConfirm(CustomHudImageTextureManager.TextureInfo info, GifPendingMode mode) {
        if (info == null || mode == GifPendingMode.NONE) {
            return;
        }
        gifConfirmVisible = true;
        importConfirmVisible = false;
        pendingDroppedImportZip = null;
        pendingGifMode = mode;
        pendingGifSourcePath = info.sourcePath();
        pendingGifSourceWidth = info.sourceWidth();
        pendingGifSourceHeight = info.sourceHeight();
        imageStatusText = "GIF 경고: 계속/취소를 선택하세요";
    }

    private void applyPendingGifAction(boolean proceed) {
        GifPendingMode mode = pendingGifMode;
        String sourcePath = pendingGifSourcePath;
        int sourceWidth = pendingGifSourceWidth;
        int sourceHeight = pendingGifSourceHeight;
        clearPendingGifAction();

        if (!proceed) {
            imageStatusText = "GIF 반영 취소됨";
            return;
        }

        if (sourcePath == null || sourcePath.isBlank()) {
            imageStatusText = "GIF 반영 실패: 이미지 경로를 확인하세요";
            return;
        }

        switch (mode) {
            case PREPARE_IMAGE_TOOL -> preparePendingImageTool(sourcePath, sourceWidth, sourceHeight);
            case PASTE_IMAGE_ELEMENT -> placePastedImage(sourcePath, sourceWidth, sourceHeight);
            case NONE -> {
            }
        }
    }

    private void clearPendingGifAction() {
        pendingGifMode = GifPendingMode.NONE;
        pendingGifSourcePath = "";
        pendingGifSourceWidth = 0;
        pendingGifSourceHeight = 0;
    }

    private void handleExportClick() {
        Path target = chooseExportZipPath();
        if (target == null) {
            imageStatusText = "내보내기 실패: 저장 경로를 열 수 없습니다";
            return;
        }

        try {
            // 좀 어렵구만 AI한테 ㄱㄱ 같은 순간에도, 결과물은 ZIP 하나로 깔끔하게.
            CustomHudExchange.exportToZip(state, target);
            imageStatusText = "내보내기 완료: " + target.getFileName();
            revealExportFolder(target);
        } catch (Exception e) {
            imageStatusText = "내보내기 실패: 이미지/파일을 확인하세요";
        }
    }

    private void handleImportClick() {
        Path source = pendingDroppedImportZip;
        pendingDroppedImportZip = null;
        if (source == null) {
            source = chooseImportZipPath();
        }
        if (source == null) {
            imageStatusText = "받기 실패: ZIP 선택 또는 기본 경로를 확인하세요";
            return;
        }

        try {
            // 가져오기는 통째로 교체한다. 중간 머지 로직은 구현이 어려워서 다음 라운드로.
            CustomHudExchange.ImportedHudData imported = CustomHudExchange.importFromZip(source);
            state.replaceAll(
                    imported.elements(),
                    imported.canvasWidth(),
                    imported.canvasHeight(),
                    imported.hudX(),
                    imported.hudY(),
                    imported.hudScalePercent()
            );
            clearElementSelection();
            selectedTool = HudTool.SELECT;
            stopTextEditing();
            resetDragState();
            CustomHudStorage.save(state);
            imageStatusText = "받기 완료: " + source.getFileName();
        } catch (Exception e) {
            imageStatusText = "받기 실패: ZIP 파일을 확인하세요";
        }
    }

    private Path chooseExportZipPath() {
        try {
            FileDialog dialog = new FileDialog((java.awt.Frame) null, "다른 이름으로 저장", FileDialog.SAVE);
            dialog.setFile(EXCHANGE_ZIP_NAME);
            dialog.setVisible(true);
            String directory = dialog.getDirectory();
            String fileName = dialog.getFile();
            if (directory == null || fileName == null || fileName.isBlank()) {
                return fallbackExportZipPath();
            }
            String normalizedName = fileName.toLowerCase().endsWith(".zip") ? fileName : fileName + ".zip";
            return Path.of(directory).resolve(normalizedName).toAbsolutePath().normalize();
        } catch (Throwable ignored) {
            // 맥에서 AWT 다이얼로그가 실패하는 경우가 있어 자동 경로로 fallback.
            return fallbackExportZipPath();
        }
    }

    private Path chooseImportZipPath() {
        try {
            FileDialog dialog = new FileDialog((java.awt.Frame) null, "ZIP 파일 선택", FileDialog.LOAD);
            dialog.setFile("*.zip");
            dialog.setVisible(true);
            String directory = dialog.getDirectory();
            String fileName = dialog.getFile();
            if (directory == null || fileName == null || fileName.isBlank()) {
                return fallbackImportZipPath();
            }
            return Path.of(directory).resolve(fileName).toAbsolutePath().normalize();
        } catch (Throwable ignored) {
            // 다이얼로그를 못 띄워도 기본 교환 폴더에서 가져오기를 시도한다.
            return fallbackImportZipPath();
        }
    }

    private Path fallbackExportZipPath() {
        try {
            Path exchangeDir = exchangeDir();
            Files.createDirectories(exchangeDir);
            return exchangeDir.resolve(EXCHANGE_ZIP_NAME).toAbsolutePath().normalize();
        } catch (Exception ignored) {
            return null;
        }
    }

    private Path fallbackImportZipPath() {
        try {
            Path exchangeDir = exchangeDir();
            Path defaultZip = exchangeDir.resolve(EXCHANGE_ZIP_NAME).toAbsolutePath().normalize();
            if (Files.isRegularFile(defaultZip)) {
                return defaultZip;
            }
            if (!Files.isDirectory(exchangeDir)) {
                return null;
            }

            try (var stream = Files.list(exchangeDir)) {
                return stream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".zip"))
                        .max(Comparator.comparingLong(CustomHudEditorScreen::lastModifiedMillis))
                        .map(path -> path.toAbsolutePath().normalize())
                        .orElse(null);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception ignored) {
            return Long.MIN_VALUE;
        }
    }

    private static Path exchangeDir() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("playfarmscoreboard")
                .resolve("exchange");
    }

    private void revealExportFolder(Path exportedZip) {
        if (exportedZip == null) {
            return;
        }
        try {
            Path parent = exportedZip.toAbsolutePath().normalize().getParent();
            if (parent == null || !Files.isDirectory(parent)) {
                return;
            }

            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.OPEN)) {
                    desktop.open(parent.toFile());
                    return;
                }
            }

            // Desktop API가 막힌 환경 대비: 맥 Finder로 직접 연다.
            new ProcessBuilder("open", parent.toString()).start();
        } catch (Exception ignored) {
        }
    }

    private void beginElementResize(int index, ResizeHandle handle) {
        resizingElementIndex = index;
        activeResizeHandle = handle;
        draggingElementIndex = -1;
    }

    private void resetResizeState() {
        resizingElementIndex = -1;
        activeResizeHandle = ResizeHandle.NONE;
    }

    private boolean isResizableElement(HudElement element) {
        return element instanceof HudElement.Rectangle || element instanceof HudElement.ImageSprite;
    }

    private ResizeHandle findResizeHandleAt(int elementIndex, int localX, int localY, HudElement element) {
        if (!isResizableElement(element)) {
            return ResizeHandle.NONE;
        }
        HudElement.Bounds bounds = element.bounds();

        int minX = bounds.minX();
        int maxX = bounds.maxX();
        int minY = bounds.minY();
        int maxY = bounds.maxY();

        boolean nearLeft = Math.abs(localX - minX) <= RESIZE_HANDLE_HIT;
        boolean nearRight = Math.abs(localX - maxX) <= RESIZE_HANDLE_HIT;
        boolean nearTop = Math.abs(localY - minY) <= RESIZE_HANDLE_HIT;
        boolean nearBottom = Math.abs(localY - maxY) <= RESIZE_HANDLE_HIT;

        if (nearLeft && nearTop) {
            return ResizeHandle.TOP_LEFT;
        }
        if (nearRight && nearTop) {
            return ResizeHandle.TOP_RIGHT;
        }
        if (nearRight && nearBottom) {
            return ResizeHandle.BOTTOM_RIGHT;
        }
        if (nearLeft && nearBottom) {
            return ResizeHandle.BOTTOM_LEFT;
        }

        boolean withinX = localX >= minX - RESIZE_HANDLE_HIT && localX <= maxX + RESIZE_HANDLE_HIT;
        boolean withinY = localY >= minY - RESIZE_HANDLE_HIT && localY <= maxY + RESIZE_HANDLE_HIT;

        if (nearTop && withinX) {
            return ResizeHandle.TOP;
        }
        if (nearBottom && withinX) {
            return ResizeHandle.BOTTOM;
        }
        if (nearLeft && withinY) {
            return ResizeHandle.LEFT;
        }
        if (nearRight && withinY) {
            return ResizeHandle.RIGHT;
        }
        return ResizeHandle.NONE;
    }

    private void resizeSelectedElement(int localX, int localY) {
        if (resizingElementIndex < 0 || activeResizeHandle == ResizeHandle.NONE) {
            return;
        }

        List<HudElement> elements = state.getElements();
        if (resizingElementIndex >= elements.size()) {
            resetResizeState();
            return;
        }

        HudElement element = elements.get(resizingElementIndex);
        if (!isResizableElement(element)) {
            resetResizeState();
            return;
        }

        HudElement.Bounds bounds = element.bounds();
        int minX = bounds.minX();
        int minY = bounds.minY();
        int maxX = bounds.maxX();
        int maxY = bounds.maxY();

        if (activeResizeHandle.left) {
            minX = localX;
        }
        if (activeResizeHandle.right) {
            maxX = localX;
        }
        if (activeResizeHandle.top) {
            minY = localY;
        }
        if (activeResizeHandle.bottom) {
            maxY = localY;
        }

        if (element instanceof HudElement.ImageSprite) {
            // 일단은 크롭 모드 접고, 리사이즈는 전부 스케일로 통일한다.
            // "왜 잘렸지?" 질문이 너무 많이 와서, 이제는 그냥 확대/축소만 하게 고정.
            if (state.resizeImageElement(resizingElementIndex, minX, minY, maxX, maxY, false)) {
                setSingleSelection(resizingElementIndex);
            }
            return;
        }

        if (state.resizeElementBounds(resizingElementIndex, minX, minY, maxX, maxY)) {
            setSingleSelection(resizingElementIndex);
        }
    }

    private void drawResizeHandles(DrawContext context, HudElement.Bounds bounds) {
        for (ResizeHandle handle : ResizeHandle.values()) {
            if (handle == ResizeHandle.NONE) {
                continue;
            }
            int centerX = canvasRect.x + handleX(bounds, handle);
            int centerY = canvasRect.y + handleY(bounds, handle);
            int half = RESIZE_HANDLE_SIZE / 2;
            context.fill(centerX - half, centerY - half, centerX + half + 1, centerY + half + 1, 0xFFF8F2B8);
            HudRenderUtil.drawStrokedRect(context, centerX - half, centerY - half, RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE, 0xFF4A4231);
        }
    }

    private int handleX(HudElement.Bounds bounds, ResizeHandle handle) {
        if (handle.left) {
            return bounds.minX();
        }
        if (handle.right) {
            return bounds.maxX();
        }
        return (bounds.minX() + bounds.maxX()) / 2;
    }

    private int handleY(HudElement.Bounds bounds, ResizeHandle handle) {
        if (handle.top) {
            return bounds.minY();
        }
        if (handle.bottom) {
            return bounds.maxY();
        }
        return (bounds.minY() + bounds.maxY()) / 2;
    }

    private int findTopTextElementIndexAt(int localX, int localY) {
        List<HudElement> elements = state.getElements();
        for (int i = elements.size() - 1; i >= 0; i--) {
            HudElement element = elements.get(i);
            if (!(element instanceof HudElement.TextLabel)) {
                continue;
            }
            HudElement.Bounds bounds = element.bounds();
            if (localX >= bounds.minX() && localX <= bounds.maxX() && localY >= bounds.minY() && localY <= bounds.maxY()) {
                return i;
            }
        }
        return -1;
    }

    private void setHudMoveMode(boolean enabled) {
        if (hudMoveMode == enabled) {
            return;
        }
        hudMoveMode = enabled;
        draggingHud = false;
        clearArrowMoveState();
        if (enabled) {
            GuiScaleCompat.usePlayerGuiScaleWhileEditing(this.client);
        } else {
            GuiScaleCompat.useEditorGuiScaleWhileEditing(this.client);
        }
    }

    private boolean beginHudMoveDrag(double mouseX, double mouseY) {
        int hudX = clampHudX(state.getHudX(), this.width);
        int hudY = clampHudY(state.getHudY(), this.height);
        int previewLeft = hudX - 3;
        int previewTop = hudY - 3;
        int previewRight = hudX + scaleCanvasDimension(state.getCanvasWidth()) + 3;
        int previewBottom = hudY + scaleCanvasDimension(state.getCanvasHeight()) + 3;
        if (mouseX < previewLeft || mouseX > previewRight || mouseY < previewTop || mouseY > previewBottom) {
            return false;
        }

        draggingHud = true;
        hudDragOffsetX = (int) Math.floor(mouseX) - hudX;
        hudDragOffsetY = (int) Math.floor(mouseY) - hudY;
        return true;
    }

    private int clampHudX(int x, int viewportWidth) {
        HudElement.Bounds content = state.getContentBounds();
        double scale = hudScaleFactor();
        int minX = (int) Math.ceil(-content.minX() * scale);
        int maxX = (int) Math.floor((viewportWidth - 1) - (content.maxX() * scale));
        if (minX <= maxX) {
            return HudRenderUtil.clamp(x, minX, maxX);
        }
        return (minX + maxX) / 2;
    }

    private int clampHudY(int y, int viewportHeight) {
        HudElement.Bounds content = state.getContentBounds();
        double scale = hudScaleFactor();
        int minY = (int) Math.ceil(-content.minY() * scale);
        int maxY = (int) Math.floor((viewportHeight - 1) - (content.maxY() * scale));
        if (minY <= maxY) {
            return HudRenderUtil.clamp(y, minY, maxY);
        }
        return (minY + maxY) / 2;
    }

    private void adjustHudScale(int deltaPercent) {
        int current = state.getHudScalePercent();
        int next = HudRenderUtil.clamp(
                current + deltaPercent,
                CustomHudState.MIN_HUD_SCALE_PERCENT,
                CustomHudState.MAX_HUD_SCALE_PERCENT
        );
        if (next == current) {
            return;
        }

        state.setHudScalePercentDirect(next);
        state.setHudPositionDirect(
                clampHudX(state.getHudX(), this.width),
                clampHudY(state.getHudY(), this.height)
        );
        imageStatusText = "HUD 크기: " + next + "%";
    }

    private int scaleCanvasDimension(int value) {
        return Math.max(1, (int) Math.round(value * hudScaleFactor()));
    }

    private double hudScaleFactor() {
        return Math.max(0.01D, state.getHudScale());
    }

    private SliderType findSlider(double mouseX, double mouseY) {
        for (Map.Entry<SliderType, UiRect> entry : sliders.entrySet()) {
            if (entry.getValue().contains(mouseX, mouseY)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private int sliderMin(SliderType sliderType) {
        return sliderType.min;
    }

    private int sliderMax(SliderType sliderType) {
        return sliderType.max;
    }

    private void updateSliderValue(SliderType sliderType, double mouseX) {
        UiRect sliderRect = sliders.get(sliderType);
        double ratio = (mouseX - sliderRect.x) / (double) Math.max(1, sliderRect.width - 1);
        ratio = Math.max(0.0D, Math.min(1.0D, ratio));

        int minValue = sliderMin(sliderType);
        int maxValue = sliderMax(sliderType);
        int value = minValue + (int) Math.round(ratio * (maxValue - minValue));

        switch (sliderType) {
            case SIZE -> brushSize = value;
            case ERASER_SIZE -> eraserSize = value;
            case TEXT_SIZE -> {
                textFontSize = normalizeTextFontSize(value);
                applyActiveTextFontSize();
            }
        }
    }

    private int getSliderValue(SliderType sliderType) {
        return switch (sliderType) {
            case SIZE -> brushSize;
            case ERASER_SIZE -> eraserSize;
            case TEXT_SIZE -> textFontSize;
        };
    }

    private void startTextEditing(int index, HudElement.TextLabel textLabel, int caretIndex) {
        editingTextElementIndex = index;
        editingTextBuffer = textLabel.text();
        textFontSize = normalizeTextFontSize(textLabel.fontSize());
        editingCaretIndex = HudRenderUtil.clamp(caretIndex, 0, editingTextBuffer.length());
        clearTextSelection();
        draggingTextSelection = false;
        setSingleSelection(index);
    }

    private void applyTextEditLive() {
        if (editingTextElementIndex < 0) {
            return;
        }
        editingCaretIndex = HudRenderUtil.clamp(editingCaretIndex, 0, editingTextBuffer.length());
        normalizeSelectionState();
        if (!state.updateTextElement(editingTextElementIndex, editingTextBuffer)) {
            stopTextEditing();
            return;
        }
        setSingleSelection(editingTextElementIndex);
    }

    private void stopTextEditing() {
        editingTextElementIndex = -1;
        editingTextBuffer = "";
        editingCaretIndex = 0;
        clearTextSelection();
        draggingTextSelection = false;
    }

    private void applyActiveTextColor() {
        if (editingTextElementIndex >= 0) {
            if (!state.updateTextElementColor(editingTextElementIndex, currentColor())) {
                stopTextEditing();
                return;
            }
            setSingleSelection(editingTextElementIndex);
            return;
        }

        if (selectedElementIndex >= 0 && selectedElementIndex < state.getElements().size()) {
            state.updateTextElementColor(selectedElementIndex, currentColor());
        }
    }

    private int normalizeTextFontSize(int value) {
        return HudRenderUtil.clamp(
                value,
                HudElement.TextLabel.MIN_FONT_SIZE,
                HudElement.TextLabel.MAX_FONT_SIZE
        );
    }

    private void applyActiveTextFontSize() {
        textFontSize = normalizeTextFontSize(textFontSize);
        if (editingTextElementIndex >= 0) {
            if (!state.updateTextElementFontSize(editingTextElementIndex, textFontSize)) {
                stopTextEditing();
                return;
            }
            setSingleSelection(editingTextElementIndex);
            return;
        }

        if (selectedElementIndex >= 0 && selectedElementIndex < state.getElements().size()) {
            state.updateTextElementFontSize(selectedElementIndex, textFontSize);
        }
    }

    private void syncTextFontSizeFromSelection() {
        int anchorIndex = editingTextElementIndex >= 0 ? editingTextElementIndex : selectedElementIndex;
        if (anchorIndex < 0 || anchorIndex >= state.getElements().size()) {
            return;
        }
        HudElement element = state.getElements().get(anchorIndex);
        if (element instanceof HudElement.TextLabel textLabel) {
            textFontSize = normalizeTextFontSize(textLabel.fontSize());
        }
    }

    private void selectAllEditingText() {
        if (editingTextElementIndex < 0) {
            return;
        }
        editingSelectionStart = 0;
        editingSelectionEnd = editingTextBuffer.length();
        editingCaretIndex = editingSelectionEnd;
        normalizeSelectionState();
    }

    private void moveCaretBy(int delta, boolean extendSelection) {
        moveCaretTo(editingCaretIndex + delta, extendSelection);
    }

    private void moveCaretTo(int newCaretIndex, boolean extendSelection) {
        int clamped = HudRenderUtil.clamp(newCaretIndex, 0, editingTextBuffer.length());
        if (extendSelection) {
            if (editingSelectionStart < 0 || editingSelectionEnd < 0) {
                editingSelectionStart = editingCaretIndex;
                editingSelectionEnd = editingCaretIndex;
            }
            editingSelectionEnd = clamped;
            editingCaretIndex = clamped;
            normalizeSelectionState();
            return;
        }

        editingCaretIndex = clamped;
        clearTextSelection();
    }

    private void insertTextAtCaret(String typed) {
        if (typed == null || typed.isEmpty()) {
            return;
        }

        int selectionMin = hasTextSelection() ? selectionMinIndex() : editingCaretIndex;
        int selectionMax = hasTextSelection() ? selectionMaxIndex() : editingCaretIndex;
        int textLength = editingTextBuffer.length();
        int remainingLimit = MAX_TEXT_LENGTH - (textLength - (selectionMax - selectionMin));
        if (remainingLimit <= 0) {
            return;
        }

        String insertText = typed.length() > remainingLimit ? typed.substring(0, remainingLimit) : typed;
        editingTextBuffer = editingTextBuffer.substring(0, selectionMin) + insertText + editingTextBuffer.substring(selectionMax);
        editingCaretIndex = selectionMin + insertText.length();
        clearTextSelection();
    }

    private void deleteBackspaceAtCaret() {
        if (deleteSelectionAtCaret()) {
            return;
        }
        if (editingCaretIndex <= 0 || editingTextBuffer.isEmpty()) {
            return;
        }

        int start = editingTextBuffer.offsetByCodePoints(editingCaretIndex, -1);
        editingTextBuffer = editingTextBuffer.substring(0, start) + editingTextBuffer.substring(editingCaretIndex);
        editingCaretIndex = start;
    }

    private void deleteForwardAtCaret() {
        if (deleteSelectionAtCaret()) {
            return;
        }
        if (editingCaretIndex >= editingTextBuffer.length() || editingTextBuffer.isEmpty()) {
            return;
        }

        int end = editingTextBuffer.offsetByCodePoints(editingCaretIndex, 1);
        editingTextBuffer = editingTextBuffer.substring(0, editingCaretIndex) + editingTextBuffer.substring(end);
    }

    private boolean deleteSelectionAtCaret() {
        if (!hasTextSelection()) {
            return false;
        }

        int start = selectionMinIndex();
        int end = selectionMaxIndex();
        editingTextBuffer = editingTextBuffer.substring(0, start) + editingTextBuffer.substring(end);
        editingCaretIndex = start;
        clearTextSelection();
        return true;
    }

    private boolean hasTextSelection() {
        return editingSelectionStart >= 0 && editingSelectionEnd >= 0 && editingSelectionStart != editingSelectionEnd;
    }

    private int selectionMinIndex() {
        return Math.min(editingSelectionStart, editingSelectionEnd);
    }

    private int selectionMaxIndex() {
        return Math.max(editingSelectionStart, editingSelectionEnd);
    }

    private void clearTextSelection() {
        editingSelectionStart = -1;
        editingSelectionEnd = -1;
    }

    private void normalizeSelectionState() {
        if (editingSelectionStart < 0 || editingSelectionEnd < 0) {
            clearTextSelection();
            return;
        }

        editingSelectionStart = HudRenderUtil.clamp(editingSelectionStart, 0, editingTextBuffer.length());
        editingSelectionEnd = HudRenderUtil.clamp(editingSelectionEnd, 0, editingTextBuffer.length());
        if (editingSelectionStart == editingSelectionEnd) {
            clearTextSelection();
        }
    }

    private void updateTextSelectionFromMouse(int mouseX) {
        if (editingTextElementIndex < 0) {
            return;
        }

        List<HudElement> elements = state.getElements();
        if (editingTextElementIndex >= elements.size()) {
            return;
        }
        HudElement element = elements.get(editingTextElementIndex);
        if (!(element instanceof HudElement.TextLabel textLabel)) {
            return;
        }

        int localX = clampLocalX(mouseX - canvasRect.x);
        int caret = findTextCaretIndex(editingTextElementIndex, textLabel, localX);
        if (editingSelectionStart < 0 || editingSelectionEnd < 0) {
            editingSelectionStart = editingCaretIndex;
            editingSelectionEnd = editingCaretIndex;
        }
        editingSelectionEnd = caret;
        editingCaretIndex = caret;
        normalizeSelectionState();
    }

    private int findTextCaretIndex(int textIndex, HudElement.TextLabel textLabel, int localX) {
        String text = textIndex == editingTextElementIndex ? editingTextBuffer : textLabel.text();
        int fontSize = textIndex == editingTextElementIndex ? textFontSize : textLabel.fontSize();

        int relativeX = localX - textLabel.x();
        if (relativeX <= 0 || text.isEmpty()) {
            return 0;
        }

        float scale = textScale(fontSize);
        int textWidth = Math.max(1, Math.round(this.textRenderer.getWidth(text) * scale));
        if (relativeX >= textWidth) {
            return text.length();
        }

        for (int i = 1; i <= text.length(); i++) {
            int previousWidth = Math.round(this.textRenderer.getWidth(text.substring(0, i - 1)) * scale);
            int currentWidth = Math.round(this.textRenderer.getWidth(text.substring(0, i)) * scale);
            int pivot = (previousWidth + currentWidth) / 2;
            if (relativeX < pivot) {
                return i - 1;
            }
        }
        return text.length();
    }

    private void renderTextEditingOverlay(DrawContext context) {
        if (editingTextElementIndex < 0) {
            return;
        }

        List<HudElement> elements = state.getElements();
        if (editingTextElementIndex >= elements.size()) {
            return;
        }
        HudElement element = elements.get(editingTextElementIndex);
        if (!(element instanceof HudElement.TextLabel textLabel)) {
            return;
        }
        int fontSize = normalizeTextFontSize(textLabel.fontSize());
        float scale = textScale(fontSize);

        int baseX = canvasRect.x + textLabel.x();
        int baseY = canvasRect.y + textLabel.y();
        int scaledHeight = Math.max(1, Math.round(this.textRenderer.fontHeight * scale));

        if (hasTextSelection()) {
            int from = textWidthUpTo(editingTextBuffer, selectionMinIndex(), fontSize);
            int to = textWidthUpTo(editingTextBuffer, selectionMaxIndex(), fontSize);
            context.fill(baseX + from, baseY - 1, baseX + Math.max(from + 1, to), baseY + scaledHeight + 1, 0x663A87FF);
        }

        if (!hasTextSelection() && ((System.currentTimeMillis() / 500L) % 2L == 0L)) {
            int caretX = baseX + textWidthUpTo(editingTextBuffer, editingCaretIndex, fontSize);
            context.fill(caretX, baseY - 1, caretX + 1, baseY + scaledHeight + 1, 0xFFF1FCF9);
        }
    }

    private int textWidthUpTo(String text, int index, int fontSize) {
        int safeIndex = HudRenderUtil.clamp(index, 0, text.length());
        if (safeIndex == 0) {
            return 0;
        }
        return Math.round(this.textRenderer.getWidth(text.substring(0, safeIndex)) * textScale(fontSize));
    }

    private float textScale(int fontSize) {
        int normalized = normalizeTextFontSize(fontSize);
        return normalized / (float) HudElement.TextLabel.DEFAULT_FONT_SIZE;
    }

    private int currentColor() {
        return hsvToColor(hue, saturation, value, alpha);
    }

    private int previewColor() {
        int previewAlpha = Math.max(90, Math.min(255, alpha + 25));
        return hsvToColor(hue, saturation, value, previewAlpha);
    }

    private int currentColorWithAlpha(int targetAlpha) {
        return hsvToColor(hue, saturation, value, targetAlpha);
    }

    private int clampLocalX(int localX) {
        return HudRenderUtil.clamp(localX, 0, Math.max(0, visibleCanvasWidth - 1));
    }

    private int clampLocalY(int localY) {
        return HudRenderUtil.clamp(localY, 0, Math.max(0, visibleCanvasHeight - 1));
    }

    private void appendBrushPoint(int localX, int localY) {
        if (activeBrushPoints.isEmpty() || !samePoint(activeBrushPoints.get(activeBrushPoints.size() - 1), localX, localY)) {
            activeBrushPoints.add(new HudElement.HudPoint(localX, localY));
        }
    }

    private void appendInterpolatedBrush(int localX, int localY) {
        int deltaX = localX - lastBrushX;
        int deltaY = localY - lastBrushY;
        double distance = Math.hypot(deltaX, deltaY);
        if (distance == 0.0D) {
            appendBrushPoint(localX, localY);
            return;
        }

        int spacing = Math.max(1, brushSize / 3);
        int steps = Math.max(1, (int) Math.ceil(distance / spacing));

        for (int i = 1; i <= steps; i++) {
            float progress = i / (float) steps;
            int x = Math.round(lastBrushX + deltaX * progress);
            int y = Math.round(lastBrushY + deltaY * progress);
            appendBrushPoint(x, y);
        }

        lastBrushX = localX;
        lastBrushY = localY;
    }

    private boolean samePoint(HudElement.HudPoint point, int x, int y) {
        return point.x() == x && point.y() == y;
    }

    private boolean isShiftHeld() {
        if (this.client == null) {
            return false;
        }
        return InputUtil.isKeyPressed(this.client.getWindow().getHandle(), InputUtil.GLFW_KEY_LEFT_SHIFT)
                || InputUtil.isKeyPressed(this.client.getWindow().getHandle(), InputUtil.GLFW_KEY_RIGHT_SHIFT);
    }

    private static boolean isValidTextChar(char chr) {
        if (Character.isISOControl(chr)) {
            return false;
        }
        return chr != '\u007F';
    }

    private void updateArrowKeyMovement() {
        if (!isAnyArrowHeld()) {
            return;
        }
        if (hudMoveMode || editingTextElementIndex >= 0) {
            nextArrowMoveAtMs = 0L;
            return;
        }

        long now = System.currentTimeMillis();
        if (nextArrowMoveAtMs == 0L) {
            nextArrowMoveAtMs = now + ARROW_REPEAT_INITIAL_DELAY_MS;
            return;
        }
        if (now < nextArrowMoveAtMs) {
            return;
        }

        moveSelectedElementByArrowKeys();
        nextArrowMoveAtMs = now + ARROW_REPEAT_INTERVAL_MS;
    }

    private boolean moveSelectedElementByArrowKeys() {
        List<Integer> selectedIndices = getValidSelectedIndices(state.getElements().size());
        if (selectedIndices.isEmpty()) {
            return false;
        }

        int stepX = (rightArrowHeld ? 1 : 0) - (leftArrowHeld ? 1 : 0);
        int stepY = (downArrowHeld ? 1 : 0) - (upArrowHeld ? 1 : 0);
        if (stepX == 0 && stepY == 0) {
            return false;
        }

        int step = (leftShiftHeld || rightShiftHeld) ? ARROW_MOVE_STEP_FAST : ARROW_MOVE_STEP;
        if (selectedIndices.size() == 1) {
            return state.translateElement(selectedIndices.get(0), stepX * step, stepY * step);
        }
        return state.translateElements(selectedIndices, stepX * step, stepY * step);
    }

    private static boolean isArrowKey(int keyCode) {
        return keyCode == InputUtil.GLFW_KEY_LEFT
                || keyCode == InputUtil.GLFW_KEY_RIGHT
                || keyCode == InputUtil.GLFW_KEY_UP
                || keyCode == InputUtil.GLFW_KEY_DOWN;
    }

    private boolean isArrowHeld(int keyCode) {
        return switch (keyCode) {
            case InputUtil.GLFW_KEY_LEFT -> leftArrowHeld;
            case InputUtil.GLFW_KEY_RIGHT -> rightArrowHeld;
            case InputUtil.GLFW_KEY_UP -> upArrowHeld;
            case InputUtil.GLFW_KEY_DOWN -> downArrowHeld;
            default -> false;
        };
    }

    private void setArrowHeld(int keyCode, boolean held) {
        switch (keyCode) {
            case InputUtil.GLFW_KEY_LEFT -> leftArrowHeld = held;
            case InputUtil.GLFW_KEY_RIGHT -> rightArrowHeld = held;
            case InputUtil.GLFW_KEY_UP -> upArrowHeld = held;
            case InputUtil.GLFW_KEY_DOWN -> downArrowHeld = held;
            default -> {
            }
        }
    }

    private boolean isAnyArrowHeld() {
        return leftArrowHeld || rightArrowHeld || upArrowHeld || downArrowHeld;
    }

    private void clearArrowMoveState() {
        leftArrowHeld = false;
        rightArrowHeld = false;
        upArrowHeld = false;
        downArrowHeld = false;
        leftShiftHeld = false;
        rightShiftHeld = false;
        nextArrowMoveAtMs = 0L;
    }

    private void resetDragState() {
        dragMode = DragMode.NONE;
        marqueeAdditiveSelection = false;
        activeBrushPoints.clear();
        draggingElementIndex = -1;
        resetResizeState();
    }

    private String trimToWidth(String text, int maxWidth) {
        if (this.textRenderer.getWidth(text) <= maxWidth) {
            return text;
        }

        String trimmed = text;
        while (!trimmed.isEmpty() && this.textRenderer.getWidth(trimmed + "...") > maxWidth) {
            int newLength = trimmed.offsetByCodePoints(trimmed.length(), -1);
            trimmed = trimmed.substring(0, newLength);
        }
        return trimmed + "...";
    }

    private static float angleFromVector(double deltaX, double deltaY) {
        double angle = Math.toDegrees(Math.atan2(deltaY, deltaX));
        if (angle < 0.0D) {
            angle += 360.0D;
        }
        return (float) angle;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static int hsvToColor(float hue, float saturation, float value, int alpha) {
        int[] rgb = hsvToRgb(hue, saturation, value);
        return HudRenderUtil.argb(alpha, rgb[0], rgb[1], rgb[2]);
    }

    private static int[] hsvToRgb(float hue, float saturation, float value) {
        float normalizedHue = hue % 360.0F;
        if (normalizedHue < 0.0F) {
            normalizedHue += 360.0F;
        }

        float chroma = value * saturation;
        float hueSection = normalizedHue / 60.0F;
        float secondary = chroma * (1.0F - Math.abs((hueSection % 2.0F) - 1.0F));
        float match = value - chroma;

        float redPrime;
        float greenPrime;
        float bluePrime;

        if (hueSection < 1.0F) {
            redPrime = chroma;
            greenPrime = secondary;
            bluePrime = 0.0F;
        } else if (hueSection < 2.0F) {
            redPrime = secondary;
            greenPrime = chroma;
            bluePrime = 0.0F;
        } else if (hueSection < 3.0F) {
            redPrime = 0.0F;
            greenPrime = chroma;
            bluePrime = secondary;
        } else if (hueSection < 4.0F) {
            redPrime = 0.0F;
            greenPrime = secondary;
            bluePrime = chroma;
        } else if (hueSection < 5.0F) {
            redPrime = secondary;
            greenPrime = 0.0F;
            bluePrime = chroma;
        } else {
            redPrime = chroma;
            greenPrime = 0.0F;
            bluePrime = secondary;
        }

        int red = Math.round((redPrime + match) * 255.0F);
        int green = Math.round((greenPrime + match) * 255.0F);
        int blue = Math.round((bluePrime + match) * 255.0F);
        return new int[]{red, green, blue};
    }

    private enum DragMode {
        NONE,
        BRUSH,
        SHAPE,
        SELECT_MARQUEE
    }

    private enum GifPendingMode {
        NONE,
        PREPARE_IMAGE_TOOL,
        PASTE_IMAGE_ELEMENT
    }

    private enum PickerDragMode {
        NONE,
        HUE,
        SATURATION_VALUE,
        ALPHA
    }

    private enum ResizeHandle {
        NONE(false, false, false, false),
        TOP_LEFT(true, false, true, false),
        TOP(false, false, true, false),
        TOP_RIGHT(false, true, true, false),
        RIGHT(false, true, false, false),
        BOTTOM_RIGHT(false, true, false, true),
        BOTTOM(false, false, false, true),
        BOTTOM_LEFT(true, false, false, true),
        LEFT(true, false, false, false);

        private final boolean left;
        private final boolean right;
        private final boolean top;
        private final boolean bottom;

        ResizeHandle(boolean left, boolean right, boolean top, boolean bottom) {
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
        }
    }

    private enum HudTool {
        SELECT("편집"),
        BRUSH("브러시"),
        FILL("페인트"),
        RECTANGLE("사각형"),
        CIRCLE("원"),
        LINE("직선"),
        IMAGE("이미지"),
        TEXT("텍스트"),
        ERASER("지우개");

        private final String label;

        HudTool(String label) {
            this.label = label;
        }
    }

    private enum SliderType {
        SIZE("브러시", 1, 100, 0xFFE2F2EE),
        ERASER_SIZE("지우개", 1, 40, 0xFFF7D99A),
        TEXT_SIZE("텍스트", HudElement.TextLabel.MIN_FONT_SIZE, HudElement.TextLabel.MAX_FONT_SIZE, 0xFFD0D7FF);

        private final String label;
        private final int min;
        private final int max;
        private final int barColor;

        SliderType(String label, int min, int max, int barColor) {
            this.label = label;
            this.min = min;
            this.max = max;
            this.barColor = barColor;
        }
    }

    private enum LayerAction {
        UP("위로"),
        DOWN("아래로"),
        FRONT("맨앞"),
        BACK("맨뒤"),
        DELETE("삭제");

        private final String label;

        LayerAction(String label) {
            this.label = label;
        }
    }

    private record TooltipSpec(String id, String title, String description, String shortcut) {
    }

    private record UiRect(int x, int y, int width, int height) {
        private static final UiRect EMPTY = new UiRect(0, 0, 0, 0);

        private int right() {
            return x + width;
        }

        private int bottom() {
            return y + height;
        }

        private boolean contains(double px, double py) {
            return px >= x && px <= right() && py >= y && py <= bottom();
        }
    }
}
