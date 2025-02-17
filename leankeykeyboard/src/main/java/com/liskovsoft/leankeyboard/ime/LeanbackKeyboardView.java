package com.liskovsoft.leankeyboard.ime;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.Keyboard.Key;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.core.content.ContextCompat;
import com.liskovsoft.leankeyboard.utils.LeanKeyPreferences;
import com.liskovsoft.leankeykeyboard.R;

import java.util.Iterator;
import java.util.List;

public class LeanbackKeyboardView extends FrameLayout {
    private static final String TAG = "LbKbView";
    /**
     * Space key index (important: wrong value will broke navigation)
     */
    public static final int ASCII_PERIOD = 47;
    /**
     * Keys count among which space key spans (important: wrong value will broke navigation)
     */
    public static final int ASCII_PERIOD_LEN = 5;
    public static final int ASCII_SPACE = 32;
    private static final boolean DEBUG = false;
    public static final int KEYCODE_CAPS_LOCK = -6;
    public static final int KEYCODE_DELETE = -5;
    public static final int KEYCODE_DISMISS_MINI_KEYBOARD = -8;
    public static final int KEYCODE_LEFT = -3;
    public static final int KEYCODE_RIGHT = -4;
    public static final int KEYCODE_SHIFT = -1;
    public static final int KEYCODE_SYM_TOGGLE = -2;
    public static final int KEYCODE_VOICE = -7;
    public static final int KEYCODE_LANG_TOGGLE = -9;
    public static final int KEYCODE_CLIPBOARD = -10;
    public static final int NOT_A_KEY = -1;
    public static final int SHIFT_LOCKED = 2;
    public static final int SHIFT_OFF = 0;
    public static final int SHIFT_ON = 1;
    private final int mNumRowCount;
    private final int mNumColCount;
    private final int mAbcColCount;
    private final int mAbcRowCount;
    private int mBaseMiniKbIndex = -1;
    private final int mClickAnimDur;
    private final float mClickedScale;
    private final float mSquareIconScaleFactor;
    private int mColCount;
    private View mCurrentFocusView;
    private boolean mFocusClicked;
    private int mFocusIndex;
    private final float mFocusedScale;
    private final int mInactiveMiniKbAlpha;
    private ImageView[] mKeyImageViews;
    private int mKeyTextColor;
    private Keyboard mKeyboard;
    private KeyHolder[] mKeys;
    private boolean mMiniKeyboardOnScreen;
    private Rect mPadding;
    private int mRowCount;
    private int mShiftState;
    private final int mUnfocusStartDelay;
    private final KeyConverter mConverter;
    protected Paint mPaint;
    protected int mKeyTextSize;
    protected int mModeChangeTextSize;
    private Drawable mCustomCapsLockDrawable;
    private static final float LOWER_CASE_KEY_TRANSLATION_X_FACTOR = -0.01f;
    private static final float LOWER_CASE_KEY_TRANSLATION_Y_FACTOR = -0.075f;

    private static class KeyConverter {
        private static final int LOWER_CASE = 0;
        private static final int UPPER_CASE = 1;

        private void init(KeyHolder keyHolder) {
            // store original label
            // in case when two characters are stored in one label (e.g. "A|B")
            if (keyHolder.key.text == null) {
                keyHolder.key.text = keyHolder.key.label;
            }
        }

        public void toLowerCase(KeyHolder keyHolder) {
            extractChar(LOWER_CASE, keyHolder);
        }

        public void toUpperCase(KeyHolder keyHolder) {
            extractChar(UPPER_CASE, keyHolder);
        }

        private void extractChar(int charCase, KeyHolder keyHolder) {
            init(keyHolder);

            CharSequence result = null;
            CharSequence label = keyHolder.key.text;

            String[] labels = splitLabels(label);

            switch (charCase) {
                case LOWER_CASE:
                    result = labels != null ? labels[0] : label.toString().toLowerCase();
                    break;
                case UPPER_CASE:
                    result = labels != null ? labels[1] : label.toString().toUpperCase();
                    break;
            }

            keyHolder.key.label = result;
        }

        private String[] splitLabels(CharSequence label) {
            String realLabel = label.toString();

            String[] labels = realLabel.split("\\|");

            return labels.length == 2 ? labels : null; // remember, we encoding two chars
        }
    }

    public LeanbackKeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Resources res = context.getResources();
        TypedArray styledAttrs = context.getTheme().obtainStyledAttributes(attrs, R.styleable.LeanbackKeyboardView, 0, 0);
        mAbcRowCount = styledAttrs.getInteger(R.styleable.LeanbackKeyboardView_abcRowCount, -1);
        mAbcColCount = styledAttrs.getInteger(R.styleable.LeanbackKeyboardView_abcColumnCount, -1);
        mNumRowCount = styledAttrs.getInteger(R.styleable.LeanbackKeyboardView_numRowCount, -1);
        mNumColCount = styledAttrs.getInteger(R.styleable.LeanbackKeyboardView_numColumnCount, -1);
        mRowCount = mAbcRowCount;
        mColCount = mAbcColCount;
        mKeyTextSize = (int) res.getDimension(R.dimen.key_font_size);
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setTextSize(mKeyTextSize);
        mPaint.setTextAlign(Align.CENTER);
        mPaint.setAlpha(255);
        mPadding = new Rect(0, 0, 0, 0);
        mModeChangeTextSize = (int) res.getDimension(R.dimen.function_key_mode_change_font_size);
        mKeyTextColor = ContextCompat.getColor(getContext(), R.color.key_text_default);
        mFocusIndex = -1;
        mShiftState = 0;
        mFocusedScale = res.getFraction(R.fraction.focused_scale, 1, 1);
        mClickedScale = res.getFraction(R.fraction.clicked_scale, 1, 1);
        mSquareIconScaleFactor = res.getFraction(R.fraction.square_icon_scale_factor, 1, 1);
        mClickAnimDur = res.getInteger(R.integer.clicked_anim_duration);
        mUnfocusStartDelay = res.getInteger(R.integer.unfocused_anim_delay);
        mInactiveMiniKbAlpha = res.getInteger(R.integer.inactive_mini_kb_alpha);
        mConverter = new KeyConverter();
    }

    private void adjustCase(KeyHolder keyHolder) {
        boolean flag = keyHolder.isInMiniKb && keyHolder.isInvertible;

        // ^ equals to !=
        if (mKeyboard.isShifted() ^ flag) {
            mConverter.toUpperCase(keyHolder);
        } else {
            mConverter.toLowerCase(keyHolder);
        }
    }

    /**
     * NOTE: Adds key views to root window
     */
    @SuppressLint("NewApi")
    private ImageView createKeyImageView(final int keyIndex) {
        Rect padding = mPadding;
        int kbdPaddingLeft = getPaddingLeft();
        int kbdPaddingTop = getPaddingTop();
        KeyHolder keyHolder = mKeys[keyIndex];
        Key key = keyHolder.key;
        adjustCase(keyHolder);
        String label;
        if (key.label == null) {
            label = null;
        } else {
            label = key.label.toString();
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "LABEL: " + key.label + "->" + label);
        }

        Bitmap bitmap = Bitmap.createBitmap(key.width, key.height, Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = mPaint;
        paint.setColor(mKeyTextColor);
        canvas.drawARGB(0, 0, 0, 0);
        if (key.icon != null) {
            if (key.codes[0] == NOT_A_KEY) {
                switch (mShiftState) {
                    case SHIFT_OFF:
                        key.icon = ContextCompat.getDrawable(getContext(), R.drawable.ic_ime_shift_off);
                        break;
                    case SHIFT_ON:
                        key.icon = ContextCompat.getDrawable(getContext(), R.drawable.ic_ime_shift_on);
                        break;
                    case SHIFT_LOCKED:
                        if (mCustomCapsLockDrawable != null) {
                            key.icon = mCustomCapsLockDrawable;
                        } else {
                            key.icon = ContextCompat.getDrawable(getContext(), R.drawable.ic_ime_shift_lock_on);
                        }
                }
            }

            // NOTE: Fix non proper scale of space key on low dpi

            int iconWidth = key.width; // originally used key.icon.getIntrinsicWidth();
            int iconHeight = key.height; // originally used key.icon.getIntrinsicHeight();

            if (key.width == key.height) { // square key proper fit
                int newSize = Math.round(key.width * mSquareIconScaleFactor);
                iconWidth = newSize;
                iconHeight = newSize;
            }

            if (key.codes[0] == ASCII_SPACE && LeanKeyPreferences.instance(getContext()).getEnlargeKeyboard()) {
                // space fix for large interface
                float gap = getResources().getDimension(R.dimen.keyboard_horizontal_gap);
                float gapDelta = (gap * 1.3f) - gap;
                iconWidth -= gapDelta * (ASCII_PERIOD_LEN - 1);
            }

            int dx = (key.width - padding.left - padding.right - iconWidth) / 2 + padding.left;
            int dy = (key.height - padding.top - padding.bottom - iconHeight) / 2 + padding.top;

            canvas.translate((float) dx, (float) dy);
            key.icon.setBounds(0, 0, iconWidth, iconHeight);
            key.icon.draw(canvas);
            canvas.translate((float) (-dx), (float) (-dy));
        } else if (label != null) {
            if (label.length() > 1) {
                paint.setTextSize((float) mModeChangeTextSize);
                paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            } else {
                paint.setTextSize((float) mKeyTextSize);
                paint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            }

            if (TextUtils.isDigitsOnly(label)) {
                canvas.translate(key.width * LOWER_CASE_KEY_TRANSLATION_X_FACTOR,
                        0);
            } else if (!label.toUpperCase().equals(label)) {
                canvas.translate(key.width * LOWER_CASE_KEY_TRANSLATION_X_FACTOR,
                        key.height * LOWER_CASE_KEY_TRANSLATION_Y_FACTOR);
            }
            canvas.drawText(
                    label,
                    (float) ((key.width - padding.left - padding.right) / 2 + padding.left),
                    (float) ((key.height - padding.top - padding.bottom) / 2) + (paint.getTextSize() - paint.descent()) / 2.0F + (float) padding.top,
                    paint
            );
            paint.setShadowLayer(0.0F, 0.0F, 0.0F, 0);
        }

        ImageView image = new ImageView(getContext());
        image.setImageBitmap(bitmap);
        image.setContentDescription(label);
        // Adds key views to root window
        addView(image, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        // Set position manually for each key
        image.setX((float) (key.x + kbdPaddingLeft));
        image.setY((float) (key.y + kbdPaddingTop));
        int opacity;
        if (mMiniKeyboardOnScreen && !keyHolder.isInMiniKb) {
            opacity = mInactiveMiniKbAlpha;
        } else {
            opacity = 255;
        }

        image.setImageAlpha(opacity);
        image.setVisibility(View.VISIBLE);

        return image;
    }

    private void createKeyImageViews(KeyHolder[] keys) {
        if (mKeyImageViews != null) {
            ImageView[] images = mKeyImageViews;
            int totalImages = images.length;

            for (int i = 0; i < totalImages; ++i) {
                removeView(images[i]);
            }

            mKeyImageViews = null;
        }

        int totalKeys = keys.length;
        for (int i = 0; i < totalKeys; ++i) {
            if (mKeyImageViews == null) {
                mKeyImageViews = new ImageView[totalKeys];
            } else if (mKeyImageViews[i] != null) {
                removeView(mKeyImageViews[i]);
            }

            mKeyImageViews[i] = createKeyImageView(i);
        }

    }

    private void removeMessages() {
        // TODO: not implemented
        Log.w(TAG, "method 'removeMessages()' not implemented");
    }

    /**
     * NOTE: Keys initialization routine.<br/>
     * Any manipulations with keys should be done here.
     */
    private void setKeys(List<Key> keys) {
        mKeys = new KeyHolder[keys.size()];
        Iterator<Key> iterator = keys.iterator();

        for (int i = 0; i < mKeys.length && iterator.hasNext(); ++i) {
            Key key = iterator.next();
            mKeys[i] = new KeyHolder(key);
        }
    }

    public boolean dismissMiniKeyboard() {
        boolean dismiss = false;
        if (mMiniKeyboardOnScreen) {
            mMiniKeyboardOnScreen = false;
            setKeys(mKeyboard.getKeys());
            invalidateAllKeys();
            dismiss = true;
        }

        return dismiss;
    }

    public int getBaseMiniKbIndex() {
        return mBaseMiniKbIndex;
    }

    public int getColCount() {
        return mColCount;
    }

    public Key getFocusedKey() {
        return mFocusIndex == -1 ? null : mKeys[mFocusIndex].key;
    }

    public Key getKey(int index) {
        return mKeys != null && mKeys.length != 0 && index >= 0 && index <= mKeys.length ? mKeys[index].key : null;
    }

    public Keyboard getKeyboard() {
        return mKeyboard;
    }

    /**
     * Get index of the key under cursor
     * <br/>
     * Resulted index depends on the space key position
     * @param x x position
     * @param y y position
     * @return index of the key
     */
    public int getNearestIndex(final float x, final float y) {
        int result;
        if (mKeys != null && mKeys.length != 0) {
            float paddingLeft = (float) getPaddingLeft();
            float paddingTop = (float) getPaddingTop();
            float kbHeight = (float) (getMeasuredHeight() - getPaddingTop() - getPaddingBottom());
            float kbWidth = (float) (getMeasuredWidth() - getPaddingLeft() - getPaddingRight());
            final int rows = getRowCount();
            final int cols = getColCount();
            final int indexVert = (int) ((y - paddingTop) / kbHeight * (float) rows);
            if (indexVert < 0) {
                result = 0;
            } else {
                result = indexVert;
                if (indexVert >= rows) {
                    result = rows - 1;
                }
            }

            final int indexHoriz = (int) ((x - paddingLeft) / kbWidth * (float) cols);
            int indexFull;
            if (indexHoriz < 0) {
                indexFull = 0;
            } else {
                indexFull = indexHoriz;
                if (indexHoriz >= cols) {
                    indexFull = cols - 1;
                }
            }

            indexFull += mColCount * result;
            result = indexFull;
            if (indexFull > ASCII_PERIOD) { // key goes beyond space
                if (indexFull < (ASCII_PERIOD + ASCII_PERIOD_LEN)) {  // key stays within space boundary
                    result = ASCII_PERIOD;
                }
            }

            indexFull = result;
            if (result >= (ASCII_PERIOD + ASCII_PERIOD_LEN)) { // is key position after space?
                indexFull = result - ASCII_PERIOD_LEN + 1;
            }

            if (indexFull < 0) {
                return 0;
            }

            result = indexFull;
            if (indexFull >= mKeys.length) {
                return mKeys.length - 1;
            }
        } else {
            result = 0;
        }

        return result;
    }

    public int getRowCount() {
        return mRowCount;
    }

    public int getShiftState() {
        return mShiftState;
    }

    public void invalidateAllKeys() {
        createKeyImageViews(mKeys);
    }

    public void invalidateKey(int keyIndex) {
        if (mKeys != null && keyIndex >= 0 && keyIndex < mKeys.length) {
            if (mKeyImageViews[keyIndex] != null) {
                removeView(mKeyImageViews[keyIndex]);
            }

            mKeyImageViews[keyIndex] = createKeyImageView(keyIndex);
        }
    }

    public boolean isMiniKeyboardOnScreen() {
        return mMiniKeyboardOnScreen;
    }

    public boolean isShifted() {
        return mShiftState == SHIFT_ON || mShiftState == SHIFT_LOCKED;
    }

    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }

    public void onKeyLongPress() {
        int popupResId = mKeys[mFocusIndex].key.popupResId;

        if (popupResId != 0) {
            dismissMiniKeyboard();
            mMiniKeyboardOnScreen = true;
            List<Key> accentKeys = (new Keyboard(getContext(), popupResId)).getKeys();
            int totalAccentKeys = accentKeys.size();
            int baseIndex = mFocusIndex;
            int currentRow = mFocusIndex / mColCount;
            int nextRow = (mFocusIndex + totalAccentKeys) / mColCount;
            if (currentRow != nextRow) {
                baseIndex = mColCount * nextRow - totalAccentKeys;
            }

            mBaseMiniKbIndex = baseIndex;

            for (int i = 0; i < totalAccentKeys; ++i) {
                Key accentKey = accentKeys.get(i);
                accentKey.x = mKeys[baseIndex + i].key.x;
                accentKey.y = mKeys[baseIndex + i].key.y;
                accentKey.edgeFlags = mKeys[baseIndex + i].key.edgeFlags;
                mKeys[baseIndex + i].key = accentKey;
                mKeys[baseIndex + i].isInMiniKb = true;
                KeyHolder holder = mKeys[baseIndex + i];

                holder.isInvertible = i == 0; // uppercase first char
            }

            invalidateAllKeys();
        } else {
            boolean isSpecialKey = mKeys[mFocusIndex].key.icon != null; // space, paste, voice input etc

            if (!isSpecialKey) { // simply use the same char in uppercase
                dismissMiniKeyboard();
                mMiniKeyboardOnScreen = true;
                mBaseMiniKbIndex = mFocusIndex;

                mKeys[mFocusIndex].isInMiniKb = true;
                mKeys[mFocusIndex].isInvertible = true;

                invalidateAllKeys();
            }
        }
    }

    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mKeyboard == null) {
            setMeasuredDimension(getPaddingLeft() + getPaddingRight(), getPaddingTop() + getPaddingBottom());
        } else {
            int heightFull = mKeyboard.getMinWidth() + getPaddingLeft() + getPaddingRight();
            heightMeasureSpec = heightFull;
            if (MeasureSpec.getSize(widthMeasureSpec) < heightFull + 10) {
                heightMeasureSpec = MeasureSpec.getSize(widthMeasureSpec);
            }

            setMeasuredDimension(heightMeasureSpec, mKeyboard.getHeight() + getPaddingTop() + getPaddingBottom());
        }
    }

    public void setFocus(int row, int col, boolean clicked) {
        setFocus(mColCount * row + col, clicked);
    }

    public void setFocus(int index, boolean clicked) {
        setFocus(index, clicked, true);
    }

    /**
     * NOTE: Increase size of currently focused or clicked key
     * @param index index of the key
     * @param clicked key state
     * @param showFocusScale increase size
     */
    public void setFocus(final int index, final boolean clicked, final boolean showFocusScale) {
        float scale = 1.0F;
        if (mKeyImageViews != null && mKeyImageViews.length != 0) {
            int indexFull;

            if (index >= 0 && index < mKeyImageViews.length) {
                indexFull = index;
            } else {
                indexFull = -1;
            }

            if (indexFull != mFocusIndex || clicked != mFocusClicked) {
                if (indexFull != mFocusIndex) {
                    if (mFocusIndex != -1) {
                        LeanbackUtils.sendAccessibilityEvent(mKeyImageViews[mFocusIndex], false);
                    }

                    if (indexFull != -1) {
                        LeanbackUtils.sendAccessibilityEvent(mKeyImageViews[indexFull], true);
                    }
                }

                if (mCurrentFocusView != null) {
                    mCurrentFocusView.animate()
                                     .scaleX(scale)
                                     .scaleY(scale)
                                     .setInterpolator(LeanbackKeyboardContainer.sMovementInterpolator)
                                     .setStartDelay(mUnfocusStartDelay);

                    mCurrentFocusView.animate()
                                     .setDuration(mClickAnimDur)
                                     .setInterpolator(LeanbackKeyboardContainer.sMovementInterpolator)
                                     .setStartDelay(mUnfocusStartDelay);
                }

                if (indexFull != -1) {
                    if (clicked) {
                        scale = mClickedScale;
                    } else if (showFocusScale) {
                        scale = mFocusedScale;
                    }

                    mCurrentFocusView = mKeyImageViews[indexFull];
                    mCurrentFocusView.animate()
                                     .scaleX(scale)
                                     .scaleY(scale)
                                     .setInterpolator(LeanbackKeyboardContainer.sMovementInterpolator)
                                     .setDuration(mClickAnimDur)
                                     .start();
                }

                mFocusIndex = indexFull;
                mFocusClicked = clicked;
                if (-1 != indexFull && !mKeys[indexFull].isInMiniKb) {
                    dismissMiniKeyboard();
                }
            }
        }

    }

    public void setKeyboard(Keyboard keyboard, boolean isAbc, boolean isNum) {
        if (isNum) {
            mRowCount = mNumRowCount;
            mColCount = mNumColCount;
        } else {
            mRowCount = mAbcRowCount;
            mColCount = mAbcColCount;
        }
        removeMessages();
        mKeyboard = keyboard;
        setKeys(mKeyboard.getKeys());
        int state = mShiftState;
        mShiftState = -1;
        mFocusIndex = -1;
        setShiftState(state);
        requestLayout();
        invalidateAllKeys();
    }

    /**
     * Set keyboard shift sate
     * @param state one of the
     * {@link LeanbackKeyboardView#SHIFT_ON SHIFT_ON},
     * {@link LeanbackKeyboardView#SHIFT_OFF SHIFT_OFF},
     * {@link LeanbackKeyboardView#SHIFT_LOCKED SHIFT_LOCKED}
     * constants
     */
    public void setShiftState(int state) {
        if (mShiftState != state) {
            switch (state) {
                case SHIFT_OFF:
                    mKeyboard.setShifted(false);
                    break;
                case SHIFT_ON:
                case SHIFT_LOCKED:
                    mKeyboard.setShifted(true);
            }

            mShiftState = state;
            invalidateAllKeys();
        }
    }

    private static class KeyHolder {
        public boolean isInMiniKb = false;
        public boolean isInvertible = false;
        public Key key;

        public KeyHolder(Key key) {
            this.key = key;
        }
    }

    public void setCapsLockDrawable(Drawable drawable) {
        mCustomCapsLockDrawable = drawable;
    }

    public void setKeyTextColor(int color) {
        mKeyTextColor = color;
    }
}
