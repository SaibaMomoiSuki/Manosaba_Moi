// Copyright (c) 2026 Moi. Proprietary - Internal Use Only.
package com.example.mixin.client;

import com.example.ChatLimit_MoiClient;
import com.example.audio.Recognition;
import com.example.audio.VoiceInputManager;
import com.example.screen.MicrophoneSettingsScreen;
import com.example.state.SpeakModeState;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Renders the speak-controls row above the chat input box:
 *
 *  - a **toggle button** (icon) that flips between 正常 (normal) and 心声
 *    (heart). It shows a tooltip — "你可以使用[V]进行快速切换" — after the mouse
 *    has hovered it for one second. The bound key is configurable in the
 *    controls screen and also toggles while chat is open.
 *  - a continuous **normal-volume slider** spanning 小声 (far left) → 正常
 *    (centre) → 大喊 (far right). It is draggable, clickable, and fine-tunable
 *    with Left/Right while the input box is empty. In 心声 mode it is greyed out
 *    and inert.
 *  - a **microphone** voice-input toggle, which is stopped automatically every
 *    time the chat screen closes (so recording never leaks into a closed UI).
 *
 * Everything is drawn by hand (no widget system) so it never steals focus from
 * the chat input field — typing keeps working.
 *
 * Mode and slider position are persisted statically (see {@link SpeakModeState})
 * so the player's choice survives closing and reopening chat.
 */
@Mixin(value = ChatScreen.class, remap = true)
public abstract class ChatScreenMixin extends Screen {

    // Layout
    @Unique
    private static final int ROW_HEIGHT = 14;
    @Unique
    private static final int TOGGLE_WIDTH = 26;
    @Unique
    private static final int MIC_WIDTH = 22;
    @Unique
    private static final int GAP = 6;
    @Unique
    private static final int MIC_GAP = 2;
    @Unique
    private static final int VISIBILITY_BUTTON_WIDTH = 16;
    @Unique
    private static final long CONTROLS_ANIMATION_MS = 240L;
    @Unique
    private static final int SLIDER_HANDLE_WIDTH = 8;
    /** How far a single Left/Right press changes the audible range, in blocks. */
    @Unique
    private static final double RANGE_KEY_STEP = 0.1;
    /** Hover time (ms) before the toggle-button tooltip appears. */
    @Unique
    private static final long TOOLTIP_HOVER_MS = 1000L;

    // Accent colors (Material-style palette)
    @Unique
    private static final int NORMAL_COLOR = 0xFF81C784;
    @Unique
    private static final int HEART_COLOR = 0xFFFFD54F;

    /** The chat input field, used to insert recognized voice text. */
    @Shadow
    protected EditBox input;

    // Button layout — computed in init(), used in render() and mouse handling
    @Unique
    private int buttonY;
    @Unique
    private static boolean controlsHidden;
    @Unique
    private double controlsOffset;
    @Unique
    private double controlsAnimationFrom;
    @Unique
    private long controlsAnimationStart;
    @Unique
    private int toggleLeft, toggleRight;
    @Unique
    private int sliderLeft, sliderRight;
    @Unique
    private int micLeft, micRight;
    @Unique
    private boolean micAvailable;
    @Unique
    private boolean draggingSlider;
    /** Previous-frame state of the Left/Right arrow keys, for edge-triggered nudging. */
    @Unique
    private boolean leftArrowWasDown;
    @Unique
    private boolean rightArrowWasDown;
    @Unique
    private String inputBeforeToggle;
    @Unique
    private int cursorBeforeToggle;
    @Unique
    private String textAfterInputRestore;
    /** Epoch ms when the cursor began hovering the toggle button (0 = not hovering). */
    @Unique
    private long toggleHoverStartMs;
    @Unique
    private boolean restrictedSliderHovered;
    @Unique
    private String restrictedSliderTooltip;
    @Unique private boolean attitudeMenuOpen;
    @Unique private boolean modeMenuOpen;
    @Unique private int attitudeScroll;
    @Unique private int modeScroll;
    @Unique private double attitudeScrollVisual;
    @Unique private double modeScrollVisual;
    @Unique private double attitudeMenuVisual;
    @Unique private double modeMenuVisual;
    @Unique private int attitudeLeft, attitudeRight, modeLeft, modeRight, menuTop;

    @Unique
    private static final String[] RESTRICTION_TOOLTIPS = {
        "需要休息一会...", "喉咙有点疼...", "休息一下再喊吧...", "稍微缓一下吧...",
        "现在应该不需要我大喊吧...", "喉咙好难受..."
    };

    /** Committed (final) text already applied to the input box this session. */
    @Unique
    private String committedApplied = "";
    /** Tentative partial tail currently shown at the end of the input box. */
    @Unique
    private String partialApplied = "";

    protected ChatScreenMixin(Component title) {
        super(title);
    }

    // ==================== Layout ====================

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        // Overlap slightly into the top of the input box so controls are far from the chat text area.
        buttonY = this.height - 40 - ROW_HEIGHT + 15;

        toggleLeft = 4 + VISIBILITY_BUTTON_WIDTH + GAP;
        toggleRight = toggleLeft + TOGGLE_WIDTH;

        int selectorWidth = 62;
        int selectorGap = 4;
        attitudeLeft = toggleRight + GAP;
        attitudeRight = attitudeLeft + selectorWidth;
        modeLeft = attitudeRight + selectorGap;
        modeRight = modeLeft + selectorWidth;
        menuTop = buttonY + ROW_HEIGHT + 4;

        micLeft = modeRight + GAP;
        micRight = micLeft + MIC_WIDTH;
        sliderLeft = micRight + MIC_GAP;
        sliderRight = Math.min(sliderLeft + this.width * 25 / 64, this.width - 4);
        controlsOffset = controlsHidden ? -Math.max(micRight, sliderRight) - 8.0 : 0.0;
        controlsAnimationFrom = controlsOffset;
        controlsAnimationStart = 0;

        micAvailable = VoiceInputManager.INSTANCE.isMicrophoneAvailable();

        // Preload the speech model on a background thread so the first mic click
        // doesn't freeze the game while the (potentially large) model loads.
        VoiceInputManager.INSTANCE.preloadModel();
    }

    // ==================== Render ====================

    @Inject(method = "removed", at = @At("HEAD"))
    private void onRemoved(CallbackInfo ci) {
        // Stop voice recording when the chat screen closes, so the microphone
        // is not left open and no text is appended to a detached input field.
        VoiceInputManager.INSTANCE.stop();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (inputBeforeToggle != null && this.input != null) {
            this.input.setValue(inputBeforeToggle);
            this.input.setCursorPosition(cursorBeforeToggle);
            if (textAfterInputRestore != null) {
                this.input.insertText(textAfterInputRestore);
            }
            inputBeforeToggle = null;
            textAfterInputRestore = null;
        }

        updateControlsAnimation();
        updateMenuScrollAnimation();
        int controlsMouseX = (int) Math.round(mouseX - controlsOffset);
        if (controlsOffset > -this.width + VISIBILITY_BUTTON_WIDTH + 1) {
            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().translate((float) controlsOffset, 0.0F);
            renderAttitudeButton(guiGraphics, controlsMouseX, mouseY);
            renderModeButton(guiGraphics, controlsMouseX, mouseY);
            renderToggleButton(guiGraphics, controlsMouseX, mouseY);
            renderSlider(guiGraphics, controlsMouseX, mouseY);
            renderMicButton(guiGraphics, controlsMouseX, mouseY);
            guiGraphics.pose().popMatrix();
        }
        renderVisibilityButton(guiGraphics, mouseX, mouseY);

        // Tooltips (drawn last so they sit above everything).
        boolean controlsInteractive = !controlsHidden && controlsOffset > -1.0;
        boolean toggleHovered = controlsInteractive
                && isHovered(controlsMouseX, mouseY, toggleLeft, toggleRight);
        if (toggleHovered) {
            if (toggleHoverStartMs == 0) {
                toggleHoverStartMs = Util.getMillis();
            } else if (Util.getMillis() - toggleHoverStartMs >= TOOLTIP_HOVER_MS) {
                drawTooltip(guiGraphics,
                    "你可以使用[" + ChatLimit_MoiClient.INSTANCE.getToggleKeyName() + "]进行快速切换",
                    mouseX, mouseY);
            }
        } else {
            toggleHoverStartMs = 0;
        }

        // ── Slider drag continuation ──────────────────────────────
        // mouseDragged isn't injectable in 1.21.8, so keep updating the slider
        // every frame while the left button is held, and end the drag on release.
        if (draggingSlider && this.minecraft != null && this.minecraft.getWindow() != null) {
            long window = this.minecraft.getWindow().getWindow();
            boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            if (!leftDown) {
                draggingSlider = false;
            } else if (SpeakModeState.INSTANCE.isNormalMode()) {
                setSliderFromMouse(controlsMouseX);
            }
        }

        if (controlsInteractive && SpeakModeState.INSTANCE.isNormalMode()
                && isSliderHovered(controlsMouseX, mouseY)) {
            double mousePosition = (double) (controlsMouseX - sliderLeft) / Math.max(1, sliderRight - sliderLeft);
            boolean overRestricted = SpeakModeState.INSTANCE.isRestricted()
                    && mousePosition > SpeakModeState.INSTANCE.maxAllowedPosition();
            if (overRestricted) {
                if (!restrictedSliderHovered) restrictedSliderTooltip = randomRestrictionTooltip();
                restrictedSliderHovered = true;
                drawTooltip(guiGraphics, restrictedSliderTooltip, mouseX, mouseY);
            } else if (draggingSlider) {
                restrictedSliderHovered = false;
                drawTooltip(guiGraphics,
                        volumeState(SpeakModeState.INSTANCE.getSliderPosition()),
                        mouseX, mouseY);
            } else if (this.minecraft != null && this.minecraft.player != null
                    && this.minecraft.player.isCreative()) {
                restrictedSliderHovered = false;
                drawTooltip(guiGraphics,
                        "声音范围: " + formatRange(SpeakModeState.INSTANCE.clearRangeBlocks()) + " 格",
                        mouseX, mouseY);
            } else {
                restrictedSliderHovered = false;
            }
        } else {
            restrictedSliderHovered = false;
        }

        // ── Left/Right fine-tune (normal mode, empty input only) ──
        if (this.minecraft != null && this.minecraft.getWindow() != null) {
            long window = this.minecraft.getWindow().getWindow();
            boolean leftDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT) == GLFW.GLFW_PRESS;
            boolean rightDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT) == GLFW.GLFW_PRESS;

            boolean canNudge = controlsInteractive && SpeakModeState.INSTANCE.isNormalMode()
                    && this.input != null && this.input.getValue().isEmpty();

            if (canNudge) {
                if (leftDown && !leftArrowWasDown) {
                    SpeakModeState.INSTANCE.nudgeRangeBlocks(-RANGE_KEY_STEP);
                }
                if (rightDown && !rightArrowWasDown) {
                    SpeakModeState.INSTANCE.nudgeRangeBlocks(RANGE_KEY_STEP);
                }
            }
            // Remember the raw key states (not gated on canNudge) so that pressing
            // Left/Right while typing doesn't fire a stale nudge on the next frame.
            leftArrowWasDown = leftDown;
            rightArrowWasDown = rightDown;
        }
    }

    /** Draw menus last so their expanded panels sit above the vanilla chat UI. */
    @Inject(method = "render", at = @At("TAIL"))
    private void renderSelectorMenusLast(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                         float delta, CallbackInfo ci) {
        if (controlsHidden || controlsOffset <= -this.width + VISIBILITY_BUTTON_WIDTH) return;
        int controlsMouseX = (int) Math.round(mouseX - controlsOffset);
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate((float) controlsOffset, 0.0F);
        renderSelectorMenu(guiGraphics, controlsMouseX, mouseY);
        guiGraphics.pose().popMatrix();
    }

    @Unique
    private void updateControlsAnimation() {
        long now = Util.getMillis();
        if (controlsAnimationStart == 0) return;
        double target = controlsHidden ? -Math.max(micRight, sliderRight) - 8.0 : 0.0;
        double progress = Math.min(1.0, (double) (now - controlsAnimationStart) / CONTROLS_ANIMATION_MS);
        double eased = progress * progress * (3.0 - 2.0 * progress);
        controlsOffset = controlsAnimationFrom + (target - controlsAnimationFrom) * eased;
        if (progress >= 1.0) controlsAnimationStart = 0;
    }

    @Unique
    private void updateMenuScrollAnimation() {
        attitudeScrollVisual = approach(attitudeScrollVisual, attitudeScroll, 0.28);
        modeScrollVisual = approach(modeScrollVisual, modeScroll, 0.28);
        attitudeMenuVisual = approach(attitudeMenuVisual, attitudeMenuOpen ? 1.0 : 0.0, 0.32);
        modeMenuVisual = approach(modeMenuVisual, modeMenuOpen ? 1.0 : 0.0, 0.32);
    }

    @Unique
    private double approach(double current, double target, double amount) {
        double next = current + (target - current) * amount;
        return Math.abs(target - next) < 0.01 ? target : next;
    }

    @Unique
    private boolean isSliderHovered(int mouseX, int mouseY) {
        int trackTop = buttonY + ROW_HEIGHT / 2 - 2;
        int trackBottom = trackTop + 4;
        return mouseX >= sliderLeft && mouseX <= sliderRight
                && mouseY >= trackTop && mouseY <= trackBottom;
    }

    @Unique
    private void renderVisibilityButton(GuiGraphics g, int mouseX, int mouseY) {
        boolean hovered = isRectHovered(mouseX, mouseY, 4, 4 + VISIBILITY_BUTTON_WIDTH,
                buttonY, buttonY + ROW_HEIGHT);
        int color = hovered ? 0xFF50565E : 0xFF30343A;
        g.fill(5, buttonY, 4 + VISIBILITY_BUTTON_WIDTH, buttonY + ROW_HEIGHT, color);
        g.renderOutline(4, buttonY, VISIBILITY_BUTTON_WIDTH, ROW_HEIGHT,
                hovered ? 0xFFE0E4E8 : 0xFF777D85);
        String icon = controlsHidden || controlsOffset < -1.0 ? ">" : "<";
        g.drawCenteredString(this.font, icon, 4 + VISIBILITY_BUTTON_WIDTH / 2,
                buttonY + 3, 0xFFFFFFFF);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void onMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY,
                                 CallbackInfoReturnable<Boolean> cir) {
        int listTop = menuFullTopFor(SpeakModeState.INSTANCE.getAttitudes());
        int listBottom = buttonY - 2;
        boolean overAttitudeMenu = attitudeMenuOpen
            && isRectHovered((int) mouseX, (int) mouseY, attitudeLeft, attitudeRight, listTop, listBottom);
        boolean overModeMenu = modeMenuOpen
            && isRectHovered((int) mouseX, (int) mouseY, modeLeft, modeRight, listTop, listBottom);
        if (overAttitudeMenu || overModeMenu) {
            if (scrollY != 0) {
                if (overAttitudeMenu) attitudeScroll = scrollIndex(attitudeScroll, scrollY, SpeakModeState.INSTANCE.getAttitudes().size());
                else modeScroll = scrollIndex(modeScroll, scrollY, SpeakModeState.INSTANCE.getModes().size());
            }
            cir.setReturnValue(true);
        }
    }

    @Unique
    private int scrollIndex(int current, double amount, int size) {
        int max = Math.max(0, size - 5);
        return Math.max(0, Math.min(max, current - (int) Math.signum(amount)));
    }

    @Unique
    private boolean isHovered(int mouseX, int mouseY, int left, int right) {
        return mouseX >= left && mouseX <= right && mouseY >= buttonY && mouseY <= buttonY + ROW_HEIGHT;
    }

    @Unique
    private boolean isRectHovered(int mx, int my, int left, int right, int top, int bottom) {
        return mx >= left && mx <= right && my >= top && my <= bottom;
    }

    @Unique
    private void renderAttitudeButton(GuiGraphics g, int mx, int my) {
        renderSelectorButton(g, attitudeLeft, attitudeRight, "语气", SpeakModeState.INSTANCE.getAttitudeName(),
            isRectHovered(mx, my, attitudeLeft, attitudeRight, buttonY, buttonY + ROW_HEIGHT));
    }

    @Unique
    private void renderModeButton(GuiGraphics g, int mx, int my) {
        renderSelectorButton(g, modeLeft, modeRight, "模式", SpeakModeState.INSTANCE.getModeName(),
            isRectHovered(mx, my, modeLeft, modeRight, buttonY, buttonY + ROW_HEIGHT));
    }

    @Unique
    private void renderSelectorButton(GuiGraphics g, int left, int right, String title, String value, boolean hovered) {
        int body = hovered ? 0xFF51575F : 0xFF343940;
        g.fill(left, buttonY, right, buttonY + ROW_HEIGHT, body);
        g.renderOutline(left, buttonY, right - left, ROW_HEIGHT, hovered ? 0xFFE0E4E8 : 0xFF777D85);
        String text = title + ":" + value;
        g.drawString(this.font, this.font.plainSubstrByWidth(text, right - left - 9), left + 3, buttonY + 3, 0xFFFFFFFF, false);
        g.drawString(this.font, "^", right - 8, buttonY + 2, 0xFFB8BEC6, false);
    }

    @Unique
    private void renderSelectorMenu(GuiGraphics g, int mx, int my) {
        if (attitudeMenuVisual > 0.001) renderMenu(g, mx, my, attitudeLeft, attitudeRight, attitudeScrollVisual, attitudeMenuVisual, SpeakModeState.INSTANCE.getAttitudes());
        if (modeMenuVisual > 0.001) renderMenu(g, mx, my, modeLeft, modeRight, modeScrollVisual, modeMenuVisual, SpeakModeState.INSTANCE.getModes());
    }

    @Unique
    private int menuTopFor(List<String> values) {
        return buttonY + ROW_HEIGHT + 4;
    }

    @Unique
    private int menuFullTopFor(List<String> values) {
        int bottom = buttonY - 4;
        int optionHeight = values == SpeakModeState.INSTANCE.getAttitudes() ? ROW_HEIGHT : 0;
        int optionGap = values == SpeakModeState.INSTANCE.getAttitudes() ? 3 : 0;
        return bottom - ROW_HEIGHT / 2 - optionHeight - optionGap
                - ROW_HEIGHT * Math.min(5, values.size());
    }

    @Unique
    private int menuListBottomFor(List<String> values) {
        int bottom = buttonY - 4;
        int optionHeight = values == SpeakModeState.INSTANCE.getAttitudes() ? ROW_HEIGHT : 0;
        int optionGap = values == SpeakModeState.INSTANCE.getAttitudes() ? 3 : 0;
        return bottom - ROW_HEIGHT / 2 - optionHeight - optionGap;
    }

    @Unique
    private void renderMenu(GuiGraphics g, int mx, int my, int left, int right, double scroll, double openness, List<String> values) {
        int bottom = buttonY - 4;
        int blankHeight = ROW_HEIGHT / 2;
        int optionHeight = values == SpeakModeState.INSTANCE.getAttitudes() ? ROW_HEIGHT : 0;
        int optionGap = values == SpeakModeState.INSTANCE.getAttitudes() ? 3 : 0;
        int listBottom = bottom - blankHeight - optionHeight - optionGap;
        int visibleRows = Math.min(5, values.size());
        int fullTop = listBottom - ROW_HEIGHT * visibleRows;
        int top = bottom - (int) ((bottom - fullTop) * openness);
        g.fill(left - 2, top - 2, right + 2, bottom + 2, 0xF0181B20);
        g.renderOutline(left - 2, top - 2, right - left + 4, bottom - top + 4, 0xFF9BA4AE);
        int visibleListBottom = Math.min(listBottom, top + ROW_HEIGHT * visibleRows);
        g.enableScissor(left, top, right, visibleListBottom);
        int first = Math.max(0, (int) Math.floor(scroll) - 1);
        int last = Math.min(values.size() - 1, first + visibleRows + 1);
        for (int index = first; index <= last; index++) {
            float y = (float) (listBottom - (visibleRows - index + scroll) * ROW_HEIGHT);
            boolean hovered = my >= y && my <= y + ROW_HEIGHT && mx >= left && mx <= right;
            if (hovered) g.fill(left, (int) y, right, (int) y + ROW_HEIGHT, 0xFF3F6D9C);
            g.drawString(this.font, values.get(index), left + 4, (int) y + 3, 0xFFFFFFFF, false);
        }
        g.disableScissor();
        if (values.size() > 5) {
            int trackLeft = right - 4;
            int trackTop = top + 2;
            int trackHeight = ROW_HEIGHT * visibleRows - 4;
            int thumbHeight = Math.max(8, trackHeight * 5 / values.size());
            int thumbTop = trackTop + (int) ((trackHeight - thumbHeight) * scroll / Math.max(1, values.size() - 5));
            g.fill(trackLeft, trackTop, trackLeft + 2, trackTop + trackHeight, 0xFF3B4149);
            g.fill(trackLeft, thumbTop, trackLeft + 2, thumbTop + thumbHeight, 0xFFB5C0CB);
        }
        if (values == SpeakModeState.INSTANCE.getAttitudes()) {
            int optionTop = listBottom + optionGap;
            int mid = (left + right) / 2;
            boolean naturalHovered = isRectHovered(mx, my, left, mid - 1, optionTop, optionTop + ROW_HEIGHT);
            boolean transformHovered = isRectHovered(mx, my, mid + 1, right, optionTop, optionTop + ROW_HEIGHT);
            if (naturalHovered) g.fill(left, optionTop, mid - 1, optionTop + ROW_HEIGHT, 0xFF3F6D9C);
            if (transformHovered) g.fill(mid + 1, optionTop, right, optionTop + ROW_HEIGHT, 0xFF3F6D9C);
            g.drawString(this.font, SpeakModeState.INSTANCE.isTransformAttitude() ? "自然" : "自然",
                left + 4, optionTop + 3, 0xFFFFFFFF, false);
            g.drawString(this.font, "变换", mid + 4, optionTop + 3, 0xFFFFFFFF, false);
            if (!SpeakModeState.INSTANCE.isTransformAttitude()) {
                g.renderOutline(left, optionTop, mid - left - 1, ROW_HEIGHT, 0xFFB9D8F5);
            } else {
                g.renderOutline(mid + 1, optionTop, right - mid - 1, ROW_HEIGHT, 0xFFB9D8F5);
            }
        }
    }

    /** The toggle button — shows the current mode as an icon (volume = normal, heart = 心声). */
    @Unique
    private void renderToggleButton(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        boolean normal = SpeakModeState.INSTANCE.isNormalMode();
        boolean book = SpeakModeState.INSTANCE.isBookMode();
        boolean admin = SpeakModeState.INSTANCE.getModeName().equals("管理组");
        boolean hovered = isHovered(mouseX, mouseY, toggleLeft, toggleRight);

        int accent = normal ? NORMAL_COLOR : book ? 0xFF90CAF9 : admin ? 0xFFCE93D8 : HEART_COLOR;
        int body = hovered ? lighten(accent, 22) : accent;
        int border = hovered ? 0xFFFFFFFF : 0xFF6F6F6F;

        guiGraphics.fill(toggleLeft + 1, buttonY, toggleRight - 1, buttonY + ROW_HEIGHT, body);
        guiGraphics.fill(toggleLeft, buttonY + 1, toggleRight, buttonY + ROW_HEIGHT - 1, body);
        guiGraphics.fill(toggleLeft + 1, buttonY, toggleRight - 1, buttonY + 2, lighten(body, 18));
        guiGraphics.fill(toggleLeft + 1, buttonY + ROW_HEIGHT - 2, toggleRight - 1, buttonY + ROW_HEIGHT, darken(body, 18));
        guiGraphics.fill(toggleLeft, buttonY, toggleRight, buttonY + 1, border);
        guiGraphics.fill(toggleLeft, buttonY + ROW_HEIGHT - 1, toggleRight, buttonY + ROW_HEIGHT, border);
        guiGraphics.fill(toggleLeft, buttonY, toggleLeft + 1, buttonY + ROW_HEIGHT, border);
        guiGraphics.fill(toggleRight - 1, buttonY, toggleRight, buttonY + ROW_HEIGHT, border);

        int cx = (toggleLeft + toggleRight) / 2;
        int iconColor = 0xFF1B1B1B;
        if (normal) {
            drawVolumeIcon(guiGraphics, cx, buttonY, iconColor);
        } else if (book) {
            drawBookIcon(guiGraphics, cx, buttonY, iconColor);
        } else if (admin) {
            drawAdminIcon(guiGraphics, cx, buttonY, iconColor);
        } else {
            drawHeartIcon(guiGraphics, cx - 3, buttonY + 4, iconColor);
        }
    }

    /** The normal-volume slider: a track + handle. Greyed out when in 心声 mode. */
    @Unique
    private void renderSlider(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        boolean active = SpeakModeState.INSTANCE.isNormalMode();
        boolean hovered = isHovered(mouseX, mouseY, sliderLeft, sliderRight);

        int cy = buttonY + ROW_HEIGHT / 2;
        int trackTop = cy - 2;
        int trackBottom = cy + 2;

        double position = SpeakModeState.INSTANCE.getSliderPosition();
        int handleCenterX = sliderLeft + (int) (position * (sliderRight - sliderLeft));
        int handleLeft = handleCenterX - SLIDER_HANDLE_WIDTH / 2;
        int handleRight = handleCenterX + SLIDER_HANDLE_WIDTH / 2;

        // Track background + filled portion
        guiGraphics.fill(sliderLeft, trackTop, sliderRight, trackBottom, 0xFF1E1E1E);
        int fillColor = active
            ? (hovered || draggingSlider ? lighten(NORMAL_COLOR, 22) : NORMAL_COLOR)
            : 0xFF555555;
        if (handleCenterX > sliderLeft) {
            guiGraphics.fill(sliderLeft, trackTop, handleCenterX, trackBottom, fillColor);
        }
        if (active && SpeakModeState.INSTANCE.isRestricted()) {
            int limitX = sliderLeft + (int) (SpeakModeState.INSTANCE.maxAllowedPosition() * (sliderRight - sliderLeft));
            guiGraphics.fill(limitX, trackTop, sliderRight, trackBottom, 0xFF555555);
        }
        // Track border
        guiGraphics.fill(sliderLeft, trackTop, sliderRight, trackTop + 1, 0xFF6F6F6F);
        guiGraphics.fill(sliderLeft, trackBottom - 1, sliderRight, trackBottom, 0xFF6F6F6F);

        // Handle knob
        int knobColor = active ? 0xFFE8F5E9 : 0xFF6E6E6E;
        guiGraphics.fill(handleLeft, buttonY + 1, handleRight, buttonY + ROW_HEIGHT - 1, knobColor);
        guiGraphics.fill(handleLeft, buttonY + 1, handleRight, buttonY + 3, lighten(knobColor, 18));
        guiGraphics.fill(handleLeft, buttonY + ROW_HEIGHT - 3, handleRight, buttonY + ROW_HEIGHT - 1, darken(knobColor, 18));
        guiGraphics.fill(handleLeft, buttonY + 1, handleLeft + 1, buttonY + ROW_HEIGHT - 1, 0xFF9E9E9E);
        guiGraphics.fill(handleRight - 1, buttonY + 1, handleRight, buttonY + ROW_HEIGHT - 1, 0xFF9E9E9E);
    }

    @Unique
    private void renderMicButton(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        boolean hovered = isHovered(mouseX, mouseY, micLeft, micRight);
        boolean recording = VoiceInputManager.INSTANCE.isRecording();
        int body;
        int border;
        if (!micAvailable) {
            body = 0xFF1F1F1F;
            border = 0xFF3F3F3F;
        } else if (recording) {
            body = hovered ? 0xFFEF5350 : 0xFFE53935;
            border = 0xFFFFCDD2;
        } else {
            body = hovered ? 0xFF474747 : 0xFF2B2B2B;
            border = 0xFF6F6F6F;
        }

        int x = micLeft, y = buttonY, w = MIC_WIDTH, h = ROW_HEIGHT;
        guiGraphics.fill(x + 1, y, x + w - 1, y + h, body);
        guiGraphics.fill(x, y + 1, x + w, y + h - 1, body);
        guiGraphics.fill(x + 1, y, x + w - 1, y + 2, lighten(body, 18));
        guiGraphics.fill(x, y, x + w, y + 1, border);
        guiGraphics.fill(x, y + h - 1, x + w, y + h, border);
        guiGraphics.fill(x, y, x + 1, y + h, border);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, border);

        if (!micAvailable) {
            renderMicIcon(guiGraphics, x + w / 2, y, 0xFF6E6E6E);
            drawSlash(guiGraphics, x, y, w, h, 0xFFFF3B30);
        } else {
            renderMicIcon(guiGraphics, x + w / 2, y, recording ? 0xFFFFFFFF : 0xFFE4E4E4);
        }
    }

    /** Draws a simple microphone glyph centered horizontally at [cx], starting at [y]. */
    @Unique
    private void renderMicIcon(GuiGraphics guiGraphics, int cx, int y, int color) {
        guiGraphics.fill(cx - 3, y + 1, cx + 3, y + 7, color);
        guiGraphics.fill(cx - 1, y + 7, cx + 1, y + 10, color);
        guiGraphics.fill(cx - 4, y + 10, cx + 4, y + 12, color);
    }

    /** A "volume" glyph (three rising bars) for the normal-mode toggle state. */
    @Unique
    private void drawVolumeIcon(GuiGraphics guiGraphics, int cx, int y, int color) {
        guiGraphics.fill(cx - 5, y + 5, cx - 4, y + 8, color);
        guiGraphics.fill(cx - 1, y + 3, cx, y + 10, color);
        guiGraphics.fill(cx + 3, y + 1, cx + 4, y + 12, color);
    }

    /** A 7×6 heart glyph whose top-left corner is at [x0, y0]. */
    @Unique
    private void drawHeartIcon(GuiGraphics guiGraphics, int x0, int y0, int color) {
        guiGraphics.fill(x0 + 1, y0, x0 + 3, y0 + 1, color);
        guiGraphics.fill(x0 + 4, y0, x0 + 6, y0 + 1, color);
        guiGraphics.fill(x0, y0 + 1, x0 + 7, y0 + 3, color);
        guiGraphics.fill(x0 + 1, y0 + 3, x0 + 6, y0 + 4, color);
        guiGraphics.fill(x0 + 2, y0 + 4, x0 + 5, y0 + 5, color);
        guiGraphics.fill(x0 + 3, y0 + 5, x0 + 4, y0 + 6, color);
    }

    @Unique
    private void drawBookIcon(GuiGraphics guiGraphics, int cx, int y, int color) {
        guiGraphics.fill(cx - 6, y + 3, cx - 1, y + 11, color);
        guiGraphics.fill(cx + 1, y + 3, cx + 6, y + 11, color);
        guiGraphics.fill(cx - 1, y + 4, cx + 1, y + 12, color);
    }

    @Unique
    private void drawAdminIcon(GuiGraphics guiGraphics, int cx, int y, int color) {
        guiGraphics.fill(cx - 6, y + 3, cx + 6, y + 10, color);
        guiGraphics.fill(cx - 3, y + 1, cx + 3, y + 3, color);
        guiGraphics.fill(cx - 2, y + 5, cx + 2, y + 7, 0xFFCE93D8);
        guiGraphics.fill(cx - 7, y + 10, cx + 7, y + 12, color);
    }

    /** Draws a diagonal red "disabled" slash from top-left to bottom-right. */
    @Unique
    private void drawSlash(GuiGraphics guiGraphics, int x, int y, int w, int h, int color) {
        int x1 = x + w - 1;
        int y1 = y + h - 1;
        for (int xx = x; xx <= x1; xx++) {
            int yy = y + (xx - x) * (y1 - y) / Math.max(1, x1 - x);
            guiGraphics.fill(xx, yy, xx + 1, yy + 2, color);
        }
    }

    /** Draws a small dark tooltip box near the cursor. */
    @Unique
    private void drawTooltip(GuiGraphics guiGraphics, String text, int mouseX, int mouseY) {
        int w = this.font.width(text) + 8;
        int h = 14;
        int tx = Math.min(mouseX + 12, this.width - w - 2);
        int ty = Math.max(mouseY - 12 - h, 2);
        guiGraphics.fill(tx, ty, tx + w, ty + h, 0xFF141414);
        guiGraphics.fill(tx, ty, tx + w, ty + 1, 0xFFAAAAAA);
        guiGraphics.fill(tx, ty + h - 1, tx + w, ty + h, 0xFFAAAAAA);
        guiGraphics.fill(tx, ty, tx + 1, ty + h, 0xFFAAAAAA);
        guiGraphics.fill(tx + w - 1, ty, tx + w, ty + h, 0xFFAAAAAA);
        guiGraphics.drawString(this.font, text, tx + 4, ty + 3, 0xFFFFFFFF, false);
    }

    /** Formats a block count, dropping the ".0" when it is a whole number. */
    @Unique
    private String formatRange(double blocks) {
        if (Math.abs(blocks - Math.round(blocks)) < 0.05) {
            return String.valueOf(Math.round(blocks));
        }
        return String.format(Locale.ROOT, "%.1f", blocks);
    }

    @Unique
    private String volumeState(double position) {
        if (position < 0.2) return "低语";
        if (position < 0.4) return "小声";
        if (position < 0.6) return "正常";
        if (position < 0.8) return "微昂";
        return "大喊";
    }

    @Unique
    private String randomRestrictionTooltip() {
        if (ThreadLocalRandom.current().nextInt(100) < 4) return "你喉咙炸了";
        return RESTRICTION_TOOLTIPS[ThreadLocalRandom.current().nextInt(RESTRICTION_TOOLTIPS.length)];
    }

    @Unique
    private static int lighten(int color, int amount) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + amount);
        int g = Math.min(255, ((color >> 8) & 0xFF) + amount);
        int b = Math.min(255, (color & 0xFF) + amount);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    @Unique
    private static int darken(int color, int amount) {
        int r = Math.max(0, ((color >> 16) & 0xFF) - amount);
        int g = Math.max(0, ((color >> 8) & 0xFF) - amount);
        int b = Math.max(0, (color & 0xFF) - amount);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    // ==================== Mouse handling ====================

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        // Any click ends a previous drag. The drag itself is driven from render()
        // (mouseDragged/mouseReleased aren't injectable in 1.21.8), so a click is
        // the one place left to reset the flag if it ever gets stuck.
        draggingSlider = false;

        int mx = (int) mouseX;
        int my = (int) mouseY;

        if (button == 0 && isRectHovered(mx, my, 4, 4 + VISIBILITY_BUTTON_WIDTH,
                buttonY, buttonY + ROW_HEIGHT)) {
            controlsHidden = !controlsHidden;
            controlsAnimationFrom = controlsOffset;
            controlsAnimationStart = Util.getMillis();
            attitudeMenuOpen = false;
            modeMenuOpen = false;
            cir.setReturnValue(true);
            return;
        }

        if (controlsHidden || controlsOffset < -this.width + VISIBILITY_BUTTON_WIDTH) return;
        mx = (int) Math.round(mx - controlsOffset);

        // Right-click on the mic button opens microphone settings directly.
        if (button == 1) {
            if (isRectHovered(mx, my, micLeft, micRight, buttonY, buttonY + ROW_HEIGHT)) {
                openMicSettings();
                cir.setReturnValue(true);
            }
            return;
        }

        if (button != 0) return;

        int attitudeTop = menuFullTopFor(SpeakModeState.INSTANCE.getAttitudes());
        int modeTop = menuFullTopFor(SpeakModeState.INSTANCE.getModes());
        if (attitudeMenuOpen && isRectHovered(mx, my, attitudeLeft, attitudeRight, attitudeTop,
                attitudeTop + ROW_HEIGHT * Math.min(5, SpeakModeState.INSTANCE.getAttitudes().size()) + 30)) {
            int visible = Math.min(5, SpeakModeState.INSTANCE.getAttitudes().size());
            int optionTop = menuListBottomFor(SpeakModeState.INSTANCE.getAttitudes()) + 3;
            if (my >= optionTop && my <= optionTop + ROW_HEIGHT) {
                int mid = (attitudeLeft + attitudeRight) / 2;
                SpeakModeState.INSTANCE.setTransformAttitude(mx >= mid);
                cir.setReturnValue(true);
                return;
            } else {
                int index = attitudeScroll + (int) Math.floor(
                        (double) (my - menuListBottomFor(SpeakModeState.INSTANCE.getAttitudes())) / ROW_HEIGHT
                        + visible - attitudeScrollVisual);
                if (index < SpeakModeState.INSTANCE.getAttitudes().size()) SpeakModeState.INSTANCE.setAttitude(index);
            }
            cir.setReturnValue(true);
        } else if (modeMenuOpen && isRectHovered(mx, my, modeLeft, modeRight, modeTop,
                modeTop + ROW_HEIGHT * Math.min(5, SpeakModeState.INSTANCE.getModes().size()))) {
            int visible = Math.min(5, SpeakModeState.INSTANCE.getModes().size());
            int index = modeScroll + (int) Math.floor(
                    (double) (my - menuListBottomFor(SpeakModeState.INSTANCE.getModes())) / ROW_HEIGHT
                    + visible - modeScrollVisual);
            if (index < SpeakModeState.INSTANCE.getModes().size()) SpeakModeState.INSTANCE.setMode(index);
            cir.setReturnValue(true);
        } else if (isRectHovered(mx, my, attitudeLeft, attitudeRight, buttonY, buttonY + ROW_HEIGHT)) {
            attitudeMenuOpen = !attitudeMenuOpen;
            attitudeScroll = Math.min(attitudeScroll, Math.max(0, SpeakModeState.INSTANCE.getAttitudes().size() - 5));
            cir.setReturnValue(true);
        } else if (isRectHovered(mx, my, modeLeft, modeRight, buttonY, buttonY + ROW_HEIGHT)) {
            modeMenuOpen = !modeMenuOpen;
            modeScroll = Math.min(modeScroll, Math.max(0, SpeakModeState.INSTANCE.getModes().size() - 5));
            cir.setReturnValue(true);
        } else if (isRectHovered(mx, my, toggleLeft, toggleRight, buttonY, buttonY + ROW_HEIGHT)) {
            SpeakModeState.INSTANCE.toggle();
            cir.setReturnValue(true);
        } else if (isSliderHovered(mx, my)) {
            // The slider is inert in 心声 mode, but still swallows the click.
            if (SpeakModeState.INSTANCE.isNormalMode()) {
                setSliderFromMouse(mx);
                draggingSlider = true;
            }
            cir.setReturnValue(true);
        } else if (isRectHovered(mx, my, micLeft, micRight, buttonY, buttonY + ROW_HEIGHT)) {
            toggleMic();
            cir.setReturnValue(true);
        }
    }

    @Unique
    private void setSliderFromMouse(int mouseX) {
        double span = Math.max(1, sliderRight - sliderLeft);
        double p = (double) (mouseX - sliderLeft) / span;
        SpeakModeState.INSTANCE.setSliderPosition(p);
    }

    @Unique
    private void openMicSettings() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new MicrophoneSettingsScreen(this));
        }
    }

    // ==================== Keyboard & drag handling ====================
    //
    // In 1.21.8 the drag/release/keyboard events (mouseDragged, mouseReleased,
    // keyPressed, charTyped) live only as members of the GuiEventListener interface
    // and are no longer injectable on ChatScreen. We drive the slider drag, the
    // V-key toggle and the Left/Right fine-tune from render() by polling
    // GLFW state directly.

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers,
                              CallbackInfoReturnable<Boolean> cir) {
        if (keyCode == GLFW.GLFW_KEY_ENTER && SpeakModeState.INSTANCE.isBookMode()
                && this.input != null && !this.input.getValue().isBlank()) {
            if (!openSketchbookWithText(this.input.getValue()) && this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(Component.literal("物品栏中没有夏目安安的素描本"), true);
            }
            cir.setReturnValue(true);
            return;
        }
        if (ChatLimit_MoiClient.INSTANCE.isToggleKey(keyCode, scanCode)) {
            boolean controlDown = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
            if (controlDown && isStandardEditShortcut(keyCode)) {
                return;
            }
            if (this.input != null) {
                inputBeforeToggle = this.input.getValue();
                cursorBeforeToggle = this.input.getCursorPosition();
            }
            boolean typeKey = controlDown || (modifiers & GLFW.GLFW_MOD_ALT) != 0;
            if (typeKey) {
                textAfterInputRestore = printableKeyName(keyCode, scanCode);
            } else {
                textAfterInputRestore = null;
                SpeakModeState.INSTANCE.toggle();
            }
            cir.setReturnValue(true);
        }
    }

    @Unique
    private boolean openSketchbookWithText(String text) {
        try {
            Class<?> screen = Class.forName("com.example.client.SketchbookScreen");
            Method method = screen.getMethod("openWithText", String.class);
            Object result = method.invoke(null, text);
            return result instanceof Boolean && (Boolean) result;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    @Unique
    private boolean isStandardEditShortcut(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_V
                || keyCode == GLFW.GLFW_KEY_C
                || keyCode == GLFW.GLFW_KEY_X
                || keyCode == GLFW.GLFW_KEY_Z;
    }

    @Unique
    private String printableKeyName(int keyCode, int scanCode) {
        String name = GLFW.glfwGetKeyName(keyCode, scanCode);
        if (name != null && !name.isEmpty()) return name;
        String translated = ChatLimit_MoiClient.INSTANCE.getToggleKeyName();
        return translated.length() == 1 ? translated : null;
    }

    // ==================== Voice input ====================

    @Unique
    private void toggleMic() {
        if (!micAvailable) return;
        if (VoiceInputManager.INSTANCE.isRecording()) {
            VoiceInputManager.INSTANCE.stop();
        } else {
            committedApplied = "";
            partialApplied = "";
            VoiceInputManager.INSTANCE.start(this::onRecognition);
        }
    }

    /**
     * Applies a recognition snapshot to the chat input box. Only newly-committed
     * text is appended (as a delta) so manual edits — e.g. deleting a word — are
     * never overwritten, and re-speaking does not re-insert already-finalized text.
     * The unstable partial tail is replaced in place, and punctuation is added
     * between utterances and at the very end.
     */
    @Unique
    private void onRecognition(Recognition r) {
        if (this.input == null) return;

        String committed = r.getCommitted();
        String partial = r.getPartial();
        boolean terminal = r.getTerminal();

        String current = this.input.getValue();

        // 1. Remove the previous partial tail — it is tentative and gets replaced.
        if (!partialApplied.isEmpty() && current.endsWith(partialApplied)) {
            current = current.substring(0, current.length() - partialApplied.length());
        }

        // 2. Append any newly-committed text (committed only ever grows).
        if (committed.length() > committedApplied.length()
                && committed.startsWith(committedApplied)) {
            String delta = committed.substring(committedApplied.length());
            // Separate utterances with a comma only when there is preceding text.
            if (needsPunctuation(current)) {
                current = current + "，";
            }
            current = current + delta;
            committedApplied = committed;
        }

        // 3. Append the fresh partial tail.
        current = current + partial;
        partialApplied = partial;

        // 4. On finalization, terminate the sentence with a full stop.
        if (terminal && needsPunctuation(current)) {
            current = current + "。";
        }

        this.input.setValue(current);
    }

    /** True when [s] is non-empty and does not already end with Chinese punctuation. */
    @Unique
    private boolean needsPunctuation(String s) {
        if (s.isEmpty()) return false;
        char c = s.charAt(s.length() - 1);
        return c != '，' && c != '。' && c != '！' && c != '？' && c != '；' && c != '、' && c != '：';
    }
}
