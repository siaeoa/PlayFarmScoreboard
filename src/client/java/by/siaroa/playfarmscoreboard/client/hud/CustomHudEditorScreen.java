package by.siaroa.playfarmscoreboard.client.hud;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

import java.awt.FileDialog;
import java.nio.file.Path;
import java.util.ArrayList;
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

    private final CustomHudState state;
    private final Map<HudTool, UiRect> toolButtons = new EnumMap<>(HudTool.class);
    private final Map<SliderType, UiRect> sliders = new EnumMap<>(SliderType.class);
    private final Map<LayerAction, UiRect> layerButtons = new EnumMap<>(LayerAction.class);
    private final List<HudElement.HudPoint> activeBrushPoints = new ArrayList<>();

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
    private UiRect importConfirmContinueButtonRect = UiRect.EMPTY;
    private UiRect importConfirmCancelButtonRect = UiRect.EMPTY;
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
    private int lastBrushX;
    private int lastBrushY;
    private boolean leftArrowHeld;
    private boolean rightArrowHeld;
    private boolean upArrowHeld;
    private boolean downArrowHeld;
    private boolean leftShiftHeld;
    private boolean rightShiftHeld;
    private long nextArrowMoveAtMs;
    private boolean importConfirmVisible;

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
        clearArrowMoveState();
        stopTextEditing();
        CustomHudStorage.save(state);
        if (this.client != null) {
            this.client.setScreen(null);
        }
    }

    @Override
    public void removed() {
        clearArrowMoveState();
        stopTextEditing();
        CustomHudStorage.save(state);
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
        renderBackdrop(context);
        renderHudPositionPreview(context);
        if (hudMoveMode) {
            renderHudMoveOverlay(context, mouseX, mouseY);
            if (importConfirmVisible) {
                renderImportConfirmDialog(context, mouseX, mouseY);
            }
            return;
        }
        renderCanvas(context, mouseX, mouseY);
        renderPanels(context, mouseX, mouseY);
        if (importConfirmVisible) {
            renderImportConfirmDialog(context, mouseX, mouseY);
        }
    }

    @Override
    public void onFilesDropped(List<Path> paths) {
        if (paths == null || paths.isEmpty()) {
            imageStatusText = "이미지 업로드 실패: 파일이 없습니다";
            return;
        }

        // 여러 파일을 떨어뜨려도 첫 성공 파일 기준으로 바로 작업 흐름을 이어간다.
        for (Path path : paths) {
            CustomHudImageTextureManager.TextureInfo info = CustomHudImageTextureManager.preload(path);
            if (info == null) {
                continue;
            }

            pendingImagePath = info.sourcePath();
            int width = info.sourceWidth();
            int height = info.sourceHeight();
            int maxDimension = Math.max(width, height);
            if (maxDimension > MAX_IMAGE_PRESET) {
                // 너무 큰 이미지는 프리뷰만 축소: 원본 파일 자체를 망가뜨리진 않는다.
                float scale = MAX_IMAGE_PRESET / (float) maxDimension;
                pendingImageWidth = Math.max(1, Math.round(width * scale));
                pendingImageHeight = Math.max(1, Math.round(height * scale));
            } else {
                pendingImageWidth = Math.max(1, width);
                pendingImageHeight = Math.max(1, height);
            }

            selectedTool = HudTool.IMAGE;
            imageStatusText = "업로드 완료: 캔버스를 클릭해 이미지 생성";
            return;
        }

        imageStatusText = "이미지 업로드 실패: 지원 형식을 확인하세요";
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (click.button() != InputUtil.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(click, doubleClick);
        }

        double mouseX = click.x();
        double mouseY = click.y();

        if (importConfirmVisible) {
            if (importConfirmContinueButtonRect.contains(mouseX, mouseY)) {
                importConfirmVisible = false;
                handleImportClick();
                return true;
            }
            if (importConfirmCancelButtonRect.contains(mouseX, mouseY)) {
                importConfirmVisible = false;
                return true;
            }
            return true;
        }

        if (hudMoveMode) {
            if (hudMoveDoneButtonRect.contains(mouseX, mouseY)) {
                hudMoveMode = false;
                draggingHud = false;
                clearArrowMoveState();
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
            importConfirmVisible = true;
            return true;
        }

        if (clearButtonRect.contains(mouseX, mouseY)) {
            state.clear();
            selectedElementIndex = -1;
            stopTextEditing();
            resetDragState();
            return true;
        }

        if (undoButtonRect.contains(mouseX, mouseY)) {
            if (state.undo()) {
                selectedElementIndex = -1;
                stopTextEditing();
            }
            return true;
        }

        if (redoButtonRect.contains(mouseX, mouseY)) {
            if (state.redo()) {
                selectedElementIndex = -1;
                stopTextEditing();
            }
            return true;
        }

        if (hudMoveButtonRect.contains(mouseX, mouseY)) {
            hudMoveMode = true;
            draggingHud = false;
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
            return super.mouseClicked(click, doubleClick);
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
                selectedElementIndex = -1;
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
                return handleSelectModeClick(localX, localY, doubleClick);
            }
            case ERASER -> {
                stopTextEditing();
                eraseAt(localX, localY);
                return true;
            }
        }

        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (click.button() != InputUtil.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseDragged(click, deltaX, deltaY);
        }

        double mouseX = click.x();
        double mouseY = click.y();

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
            return super.mouseDragged(click, deltaX, deltaY);
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
            if ((moveDeltaX != 0 || moveDeltaY != 0) && state.translateElement(draggingElementIndex, moveDeltaX, moveDeltaY)) {
                dragLastElementX = localX;
                dragLastElementY = localY;
                selectedElementIndex = draggingElementIndex;
                if (editingTextElementIndex == draggingElementIndex) {
                    stopTextEditing();
                }
            }
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

        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() != InputUtil.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseReleased(click);
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

        if (selectedTool == HudTool.SELECT && (draggingElementIndex >= 0 || resizingElementIndex >= 0)) {
            draggingElementIndex = -1;
            resetResizeState();
            return true;
        }

        if (dragMode == DragMode.BRUSH && !activeBrushPoints.isEmpty()) {
            state.addElement(new HudElement.BrushStroke(HudElement.BrushStroke.copyPoints(activeBrushPoints), brushSize, currentColor()));
            selectedElementIndex = state.getElements().size() - 1;
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
                selectedElementIndex = state.getElements().size() - 1;
            }
            resetDragState();
            return true;
        }

        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        int keyCode = keyInput.key();
        int modifiers = keyInput.modifiers();
        boolean shortcutDown = (modifiers & (InputUtil.GLFW_MOD_CONTROL | InputUtil.GLFW_MOD_SUPER)) != 0;
        boolean shiftDown = (modifiers & InputUtil.GLFW_MOD_SHIFT) != 0;

        if (importConfirmVisible) {
            if (keyCode == InputUtil.GLFW_KEY_ESCAPE) {
                importConfirmVisible = false;
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

        if (shortcutDown && keyCode == InputUtil.GLFW_KEY_Z) {
            if (state.undo()) {
                selectedElementIndex = -1;
                stopTextEditing();
            }
            return true;
        }

        if (shortcutDown && keyCode == InputUtil.GLFW_KEY_Y) {
            if (state.redo()) {
                selectedElementIndex = -1;
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

        if (keyCode == InputUtil.GLFW_KEY_ESCAPE) {
            if (hudMoveMode) {
                hudMoveMode = false;
                draggingHud = false;
                clearArrowMoveState();
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

        return super.keyPressed(keyInput);
    }

    @Override
    public boolean keyReleased(KeyInput keyInput) {
        int keyCode = keyInput.key();

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

        return super.keyReleased(keyInput);
    }

    @Override
    public boolean charTyped(CharInput charInput) {
        if (editingTextElementIndex >= 0 && charInput.isValidChar()) {
            String typed = charInput.asString();
            if (!typed.isEmpty()) {
                insertTextAtCaret(typed);
                applyTextEditLive();
            }
            return true;
        }

        return super.charTyped(charInput);
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
    }

    private void renderBackdrop(DrawContext context) {
        context.fillGradient(0, 0, this.width, this.height, 0xA20A141E, 0xA21E3045);
    }

    private void renderHudPositionPreview(DrawContext context) {
        // GUI는 재능이 없어, AI한테 ㅋ 라는 마음으로도 좌표 계산은 냉정하게 간다.
        CustomHudRenderer.renderHud(context, this.textRenderer, state);

        int previewX = clampHudX(state.getHudX(), context.getScaledWindowWidth()) - 1;
        int previewY = clampHudY(state.getHudY(), context.getScaledWindowHeight()) - 1;
        int previewW = state.getCanvasWidth() + 2;
        int previewH = state.getCanvasHeight() + 2;
        int borderColor = hudMoveMode ? 0xFFFFE18A : 0x66E7F6F2;
        context.drawStrokedRectangle(previewX, previewY, previewW, previewH, borderColor);
        if (hudMoveMode) {
            String hint = "HUD 이동 모드: 박스를 드래그하고 [완료]";
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
        renderTextEditingOverlay(context);
        context.disableScissor();

        context.drawStrokedRectangle(canvasRect.x, canvasRect.y, visibleCanvasWidth, visibleCanvasHeight, 0xFFBFDCD2);
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
                context.drawStrokedRectangle(left, top, size, size, HudRenderUtil.argb(230, 240, 255, 250));
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
                context.drawStrokedRectangle(left, top, width, height, 0xFFD4E8E2);
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
        if (selectedElementIndex < 0 || selectedElementIndex >= elements.size()) {
            return;
        }

        HudElement.Bounds bounds = elements.get(selectedElementIndex).bounds();
        int x = canvasRect.x + bounds.minX();
        int y = canvasRect.y + bounds.minY();
        int w = bounds.maxX() - bounds.minX() + 1;
        int h = bounds.maxY() - bounds.minY() + 1;

        context.drawStrokedRectangle(x - 1, y - 1, w + 2, h + 2, 0xFFF7ECA3);

        HudElement element = elements.get(selectedElementIndex);
        if (isResizableElement(element)) {
            drawResizeHandles(context, bounds);
        }
    }

    private void renderHudMoveOverlay(DrawContext context, int mouseX, int mouseY) {
        String guide = "HUD 위치를 드래그한 뒤 [완료]를 누르세요";
        int guideWidth = this.textRenderer.getWidth(guide) + 12;
        int guideX = (this.width - guideWidth) / 2;
        int guideY = Math.max(8, hudMoveDoneButtonRect.y - 26);
        context.fillGradient(guideX, guideY, guideX + guideWidth, guideY + 14, 0xC4213442, 0xC4182732);
        context.drawStrokedRectangle(guideX, guideY, guideWidth, 14, 0xFFA5C8BF);
        context.drawTextWithShadow(this.textRenderer, guide, guideX + 6, guideY + 3, 0xFFF1F9F6);
        drawButton(context, hudMoveDoneButtonRect, "완료", hudMoveDoneButtonRect.contains(mouseX, mouseY), true);
    }

    private void renderImportConfirmDialog(DrawContext context, int mouseX, int mouseY) {
        context.fill(0, 0, this.width, this.height, 0xA0000000);

        int dialogWidth = 316;
        int dialogHeight = 82;
        int dialogX = (this.width - dialogWidth) / 2;
        int dialogY = (this.height - dialogHeight) / 2;
        context.fillGradient(dialogX, dialogY, dialogX + dialogWidth, dialogY + dialogHeight, 0xE0283B49, 0xE01B2B36);
        context.drawStrokedRectangle(dialogX, dialogY, dialogWidth, dialogHeight, 0xFF9BC0B7);

        String line1 = "지금 당신의 내용이 사라집니다.";
        String line2 = "내보내기를 하고, 받기를 하는걸 권장드립니다.";
        int line1X = dialogX + 10;
        context.drawTextWithShadow(this.textRenderer, line1, line1X, dialogY + 12, 0xFFF4FBF8);
        context.drawTextWithShadow(this.textRenderer, line2, line1X, dialogY + 26, 0xFFE0F0EB);

        drawButton(context, importConfirmContinueButtonRect, "계속", importConfirmContinueButtonRect.contains(mouseX, mouseY), false);
        drawButton(context, importConfirmCancelButtonRect, "취소", importConfirmCancelButtonRect.contains(mouseX, mouseY), false);
    }

    private void renderPanels(DrawContext context, int mouseX, int mouseY) {
        drawPanel(context, topBarRect, 0xD01E3140, 0xD0152430, 0xFF7CA69C);
        drawPanel(context, leftPanelRect, 0xBE1D2F3A, 0xBE15232D, 0xFF59877D);
        drawPanel(context, rightPanelRect, 0xBE1D2F3A, 0xBE15232D, 0xFF59877D);

        context.drawTextWithShadow(this.textRenderer, "HUD 페인터", topBarRect.x + 8, topBarRect.y + 8, 0xFFF1F8F6);
        context.drawTextWithShadow(this.textRenderer, "캔버스 " + state.getCanvasWidth() + "x" + state.getCanvasHeight(), topBarRect.x + 86, topBarRect.y + 8, 0xFFD5E8E3);
        context.drawTextWithShadow(this.textRenderer, "J+K 열기 | ESC 닫기", topBarRect.x + 194, topBarRect.y + 8, 0xFFB2CBC4);

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
        String selectionText = selectedElementIndex >= 0 && selectedElementIndex < total
                ? "선택: " + (selectedElementIndex + 1) + " / " + total
                : "선택 없음";
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

        context.drawTextWithShadow(this.textRenderer, "Ctrl+Z / Ctrl+Y", rightPanelRect.x + 12, rightPanelRect.bottom() - 12, 0xFFBAD3CD);
    }

    private void drawPanel(DrawContext context, UiRect rect, int topColor, int bottomColor, int borderColor) {
        context.fillGradient(rect.x, rect.y, rect.right(), rect.bottom(), topColor, bottomColor);
        context.drawStrokedRectangle(rect.x, rect.y, rect.width, rect.height, borderColor);
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
        context.drawStrokedRectangle(rect.x, rect.y, rect.width, rect.height, 0xFFA6CAC1);

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
        context.drawStrokedRectangle(rect.x, rect.y, rect.width, rect.height, rect.contains(mouseX, mouseY) ? 0xFFD9F4EC : 0xFF87AAA1);
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
        context.drawStrokedRectangle(svRect.x, svRect.y, svRect.width, svRect.height, 0xFFE3F6F0);

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
        context.drawStrokedRectangle(colorPreviewRect.x, colorPreviewRect.y, colorPreviewRect.width, colorPreviewRect.height, 0xFFC7E0DA);
    }

    private void drawTextStatus(DrawContext context) {
        // 상태 박스는 사용자의 심박수다. 지금 뭘 하는지 항상 보이게.
        context.fillGradient(textStatusRect.x, textStatusRect.y, textStatusRect.right(), textStatusRect.bottom(), 0xB8243A4A, 0xB81A2D38);
        context.drawStrokedRectangle(textStatusRect.x, textStatusRect.y, textStatusRect.width, textStatusRect.height, 0xFF8BB0A7);

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

    private void applyLayerAction(LayerAction layerAction) {
        int size = state.getElements().size();
        if (selectedElementIndex < 0 || selectedElementIndex >= size) {
            return;
        }

        int originalIndex = selectedElementIndex;
        switch (layerAction) {
            case UP -> selectedElementIndex = state.moveElement(selectedElementIndex, selectedElementIndex + 1);
            case DOWN -> selectedElementIndex = state.moveElement(selectedElementIndex, selectedElementIndex - 1);
            case FRONT -> selectedElementIndex = state.moveElement(selectedElementIndex, size - 1);
            case BACK -> selectedElementIndex = state.moveElement(selectedElementIndex, 0);
            case DELETE -> {
                if (state.removeElementAt(selectedElementIndex)) {
                    selectedElementIndex = -1;
                    stopTextEditing();
                }
            }
        }

        if (layerAction != LayerAction.DELETE && editingTextElementIndex == originalIndex) {
            editingTextElementIndex = selectedElementIndex;
        }
    }

    private void eraseAt(int localX, int localY) {
        int eraserRadius = Math.max(1, Math.min(40, eraserSize));
        if (state.eraseAt(localX, localY, eraserRadius)) {
            selectedElementIndex = -1;
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

    private boolean handleSelectModeClick(int localX, int localY, boolean doubleClick) {
        List<HudElement> elements = state.getElements();
        if (selectedElementIndex >= 0 && selectedElementIndex < elements.size()) {
            ResizeHandle selectedHandle = findResizeHandleAt(selectedElementIndex, localX, localY, elements.get(selectedElementIndex));
            if (selectedHandle != ResizeHandle.NONE) {
                beginElementResize(selectedElementIndex, selectedHandle);
                return true;
            }
        }

        int targetIndex = state.findTopElementIndexAt(localX, localY);
        selectedElementIndex = targetIndex;
        draggingElementIndex = -1;
        resetResizeState();

        if (targetIndex < 0) {
            stopTextEditing();
            return true;
        }

        HudElement element = elements.get(targetIndex);

        ResizeHandle handle = findResizeHandleAt(targetIndex, localX, localY, element);
        if (handle != ResizeHandle.NONE) {
            beginElementResize(targetIndex, handle);
            stopTextEditing();
            return true;
        }

        if (doubleClick && element instanceof HudElement.TextLabel textLabel) {
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
            selectedElementIndex = textIndex;
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
        state.addElement(new HudElement.TextLabel(localX, localY, "", currentColor()));
        selectedElementIndex = state.getElements().size() - 1;
        HudElement created = state.getElements().get(selectedElementIndex);
        if (created instanceof HudElement.TextLabel textLabel) {
            startTextEditing(selectedElementIndex, textLabel, 0);
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
        selectedElementIndex = state.getElements().size() - 1;
        imageStatusText = "이미지 생성 완료";
        return true;
    }

    private void handleExportClick() {
        Path target = chooseExportZipPath();
        if (target == null) {
            imageStatusText = "내보내기 취소됨";
            return;
        }

        try {
            // 좀 어렵구만 AI한테 ㄱㄱ 같은 순간에도, 결과물은 ZIP 하나로 깔끔하게.
            CustomHudExchange.exportToZip(state, target);
            imageStatusText = "내보내기 완료: " + target.getFileName();
        } catch (Exception e) {
            imageStatusText = "내보내기 실패: 이미지/파일을 확인하세요";
        }
    }

    private void handleImportClick() {
        Path source = chooseImportZipPath();
        if (source == null) {
            imageStatusText = "받기 취소됨";
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
                    imported.hudY()
            );
            selectedElementIndex = -1;
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
            dialog.setFile("custom_hud.zip");
            dialog.setVisible(true);
            String directory = dialog.getDirectory();
            String fileName = dialog.getFile();
            if (directory == null || fileName == null || fileName.isBlank()) {
                return null;
            }
            String normalizedName = fileName.toLowerCase().endsWith(".zip") ? fileName : fileName + ".zip";
            return Path.of(directory).resolve(normalizedName).toAbsolutePath().normalize();
        } catch (Throwable ignored) {
            return null;
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
                return null;
            }
            return Path.of(directory).resolve(fileName).toAbsolutePath().normalize();
        } catch (Throwable ignored) {
            return null;
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
                selectedElementIndex = resizingElementIndex;
            }
            return;
        }

        if (state.resizeElementBounds(resizingElementIndex, minX, minY, maxX, maxY)) {
            selectedElementIndex = resizingElementIndex;
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
            context.drawStrokedRectangle(centerX - half, centerY - half, RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE, 0xFF4A4231);
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

    private boolean beginHudMoveDrag(double mouseX, double mouseY) {
        int hudX = clampHudX(state.getHudX(), this.width);
        int hudY = clampHudY(state.getHudY(), this.height);
        int previewLeft = hudX - 3;
        int previewTop = hudY - 3;
        int previewRight = hudX + state.getCanvasWidth() + 3;
        int previewBottom = hudY + state.getCanvasHeight() + 3;
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
        int minX = -content.minX();
        int maxX = (viewportWidth - 1) - content.maxX();
        if (minX <= maxX) {
            return HudRenderUtil.clamp(x, minX, maxX);
        }
        return (minX + maxX) / 2;
    }

    private int clampHudY(int y, int viewportHeight) {
        HudElement.Bounds content = state.getContentBounds();
        int minY = -content.minY();
        int maxY = (viewportHeight - 1) - content.maxY();
        if (minY <= maxY) {
            return HudRenderUtil.clamp(y, minY, maxY);
        }
        return (minY + maxY) / 2;
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
        }
    }

    private int getSliderValue(SliderType sliderType) {
        return switch (sliderType) {
            case SIZE -> brushSize;
            case ERASER_SIZE -> eraserSize;
        };
    }

    private void startTextEditing(int index, HudElement.TextLabel textLabel, int caretIndex) {
        editingTextElementIndex = index;
        editingTextBuffer = textLabel.text();
        editingCaretIndex = HudRenderUtil.clamp(caretIndex, 0, editingTextBuffer.length());
        clearTextSelection();
        draggingTextSelection = false;
        selectedElementIndex = index;
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
        selectedElementIndex = editingTextElementIndex;
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
            selectedElementIndex = editingTextElementIndex;
            return;
        }

        if (selectedElementIndex >= 0 && selectedElementIndex < state.getElements().size()) {
            state.updateTextElementColor(selectedElementIndex, currentColor());
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

        int relativeX = localX - textLabel.x();
        if (relativeX <= 0 || text.isEmpty()) {
            return 0;
        }

        int textWidth = this.textRenderer.getWidth(text);
        if (relativeX >= textWidth) {
            return text.length();
        }

        for (int i = 1; i <= text.length(); i++) {
            int previousWidth = this.textRenderer.getWidth(text.substring(0, i - 1));
            int currentWidth = this.textRenderer.getWidth(text.substring(0, i));
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

        int baseX = canvasRect.x + textLabel.x();
        int baseY = canvasRect.y + textLabel.y();

        if (hasTextSelection()) {
            int from = textWidthUpTo(editingTextBuffer, selectionMinIndex());
            int to = textWidthUpTo(editingTextBuffer, selectionMaxIndex());
            context.fill(baseX + from, baseY - 1, baseX + Math.max(from + 1, to), baseY + 9, 0x663A87FF);
        }

        if (!hasTextSelection() && ((System.currentTimeMillis() / 500L) % 2L == 0L)) {
            int caretX = baseX + textWidthUpTo(editingTextBuffer, editingCaretIndex);
            context.fill(caretX, baseY - 1, caretX + 1, baseY + 9, 0xFFF1FCF9);
        }
    }

    private int textWidthUpTo(String text, int index) {
        int safeIndex = HudRenderUtil.clamp(index, 0, text.length());
        if (safeIndex == 0) {
            return 0;
        }
        return this.textRenderer.getWidth(text.substring(0, safeIndex));
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
        return InputUtil.isKeyPressed(this.client.getWindow(), InputUtil.GLFW_KEY_LEFT_SHIFT)
                || InputUtil.isKeyPressed(this.client.getWindow(), InputUtil.GLFW_KEY_RIGHT_SHIFT);
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
        int size = state.getElements().size();
        if (selectedElementIndex < 0 || selectedElementIndex >= size) {
            return false;
        }

        int stepX = (rightArrowHeld ? 1 : 0) - (leftArrowHeld ? 1 : 0);
        int stepY = (downArrowHeld ? 1 : 0) - (upArrowHeld ? 1 : 0);
        if (stepX == 0 && stepY == 0) {
            return false;
        }

        int step = (leftShiftHeld || rightShiftHeld) ? ARROW_MOVE_STEP_FAST : ARROW_MOVE_STEP;
        return state.translateElement(selectedElementIndex, stepX * step, stepY * step);
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
        SHAPE
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
        ERASER_SIZE("지우개", 1, 40, 0xFFF7D99A);

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
