package com.anthony.inputlayout.layout;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.IdRes;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;

import com.anthony.inputlayout.utils.Utils;
import com.anthony.inputlayout.utils.EmotionUtil;
import com.trs.inputlayout.R;



public class CustomKeyboardLayout extends BaseCustomCompositeView {
    private static final int WHAT_SCROLL_CONTENT_TO_BOTTOM = 1;
    private static final int WHAT_CHANGE_TO_EMOTION_KEYBOARD = 2;
    private static final int WHAT_CHANGE_TO_VOICE_KEYBOARD = 3;

    private EmotionKeyboardLayout mEmotionKeyboardLayout;

    private RecorderKeyboardLayout mRecorderKeyboardLayout;

    private Activity mActivity;
    private EditText mContentEt;
    private Callback mCallback;

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case WHAT_CHANGE_TO_EMOTION_KEYBOARD:
                    showEmotionKeyboard();
                    break;
                case WHAT_CHANGE_TO_VOICE_KEYBOARD:
                    showVoiceKeyboard();
                    break;
                case WHAT_SCROLL_CONTENT_TO_BOTTOM:
                    mCallback.scrollContentToBottom();
                    break;
            }
        }
    };

    public CustomKeyboardLayout(Context context) {
        super(context);
    }

    public CustomKeyboardLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomKeyboardLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.layout_custom_keyboard;
    }

    @Override
    protected void initView() {
        mEmotionKeyboardLayout = getViewById(R.id.emotionKeyboardLayout);
        mRecorderKeyboardLayout = getViewById(R.id.recorderKeyboardLayout);
    }

    @Override
    protected void setListener() {
        mEmotionKeyboardLayout.setCallback(new EmotionKeyboardLayout.Callback() {
            @Override
            public void onDelete() {
                mContentEt.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
            }

            @Override
            public void onInsert(String text) {
                // ?????????????????????????????????
                int cursorPosition = mContentEt.getSelectionStart();
                StringBuilder sb = new StringBuilder(mContentEt.getText());
                sb.insert(cursorPosition, text);
                mContentEt.setText(EmotionUtil.getEmotionText(getContext(), sb.toString(), 20));
                mContentEt.setSelection(cursorPosition + text.length());
            }
        });

        mRecorderKeyboardLayout.setCallback(new RecorderKeyboardLayout.Callback() {
            @Override
            public void onAudioRecorderFinish(int time, String filePath) {
                if (mCallback != null) {
                    mCallback.onAudioRecorderFinish(time, filePath);
                }
            }

            @Override
            public void onAudioRecorderTooShort() {
                if (mCallback != null) {
                    mCallback.onAudioRecorderTooShort();
                }
            }

            @Override
            public void onAudioRecorderNoPermission() {
                if (mCallback != null) {
                    mCallback.onAudioRecorderNoPermission();
                }
            }
        });
    }

    @Override
    protected int[] getAttrs() {
        return new int[0];
    }

    @Override
    protected void initAttr(int attr, TypedArray typedArray) {
    }

    @Override
    protected void processLogic() {
    }

    /**
     * ??????????????????????????????
     */
    public void toggleEmotionOriginKeyboard() {
        if (isEmotionKeyboardVisible()) {
            changeToOriginalKeyboard();
        } else {
            changeToEmotionKeyboard();
        }
    }

    /**
     * ??????????????????????????????
     */
    public void toggleVoiceOriginKeyboard() {
        if (isVoiceKeyboardVisible()) {
            changeToOriginalKeyboard();
        } else {
            changeToVoiceKeyboard();
        }
    }

    /**
     * ?????????????????????
     */
    public void changeToVoiceKeyboard() {
        Utils.closeKeyboard(mActivity);

        if (isCustomKeyboardVisible()) {
            showVoiceKeyboard();
        } else {
            mHandler.sendEmptyMessageDelayed(WHAT_CHANGE_TO_VOICE_KEYBOARD, Utils.KEYBOARD_CHANGE_DELAY);
        }
    }

    /**
     * ?????????????????????
     */
    public void changeToEmotionKeyboard() {
        if (!mContentEt.isFocused()) {
            mContentEt.requestFocus();
            mContentEt.setSelection(mContentEt.getText().toString().length());
        }

        Utils.closeKeyboard(mActivity);

        if (isCustomKeyboardVisible()) {
            showEmotionKeyboard();
        } else {
            mHandler.sendEmptyMessageDelayed(WHAT_CHANGE_TO_EMOTION_KEYBOARD, Utils.KEYBOARD_CHANGE_DELAY);
        }
    }

    /**
     * ???????????????????????????
     */
    public void changeToOriginalKeyboard() {
        closeCustomKeyboard();
        Utils.openKeyboard(mContentEt);
        // ????????????????????????????????????????????????2?????????????????????
        mHandler.sendEmptyMessageDelayed(WHAT_SCROLL_CONTENT_TO_BOTTOM, Utils.KEYBOARD_CHANGE_DELAY * 2);
    }

    /**
     * ??????????????????
     */
    private void showEmotionKeyboard() {
        mEmotionKeyboardLayout.setVisibility(VISIBLE);
        sendScrollContentToBottomMsg();

        closeVoiceKeyboard();
    }

    /**
     * ??????????????????
     */
    private void showVoiceKeyboard() {
        mRecorderKeyboardLayout.setVisibility(VISIBLE);
        sendScrollContentToBottomMsg();

        closeEmotionKeyboard();
    }

    /**
     * ?????????????????????????????????????????????Handler
     */
    private void sendScrollContentToBottomMsg() {
        mHandler.sendEmptyMessageDelayed(WHAT_SCROLL_CONTENT_TO_BOTTOM, Utils.KEYBOARD_CHANGE_DELAY);
    }

    /**
     * ??????????????????
     */
    public void closeEmotionKeyboard() {
        mEmotionKeyboardLayout.setVisibility(GONE);
    }

    /**
     * ??????????????????
     */
    public void closeVoiceKeyboard() {
        mRecorderKeyboardLayout.setVisibility(GONE);
    }

    /**
     * ?????????????????????
     */
    public void closeCustomKeyboard() {
        closeEmotionKeyboard();
        closeVoiceKeyboard();
    }

    /**
     * ??????????????????
     */
    public void closeAllKeyboard() {
        closeCustomKeyboard();
        Utils.closeKeyboard(mActivity);
    }

    /**
     * ????????????????????????
     *
     * @return
     */
    public boolean isEmotionKeyboardVisible() {
        return mEmotionKeyboardLayout.getVisibility() == View.VISIBLE;
    }

    /**
     * ????????????????????????
     *
     * @return
     */
    public boolean isVoiceKeyboardVisible() {
        return mRecorderKeyboardLayout.getVisibility() == View.VISIBLE;
    }

    /**
     * ?????????????????????????????????Activity???onBackPressed?????????????????????
     *
     * @return
     */
    public boolean isCustomKeyboardVisible() {
        return isEmotionKeyboardVisible() || isVoiceKeyboardVisible();
    }

    /**
     * ?????????????????????????????????
     *
     * @param activity
     * @param contentEt
     */
    public void init(Activity activity, EditText contentEt, Callback callback) {
        if (activity == null || contentEt == null || callback == null) {
            throw new RuntimeException(CustomKeyboardLayout.class.getSimpleName() + "???init???????????????????????????null");
        }

        mActivity = activity;
        mContentEt = contentEt;
        mCallback = callback;


        mContentEt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isCustomKeyboardVisible()) {
                    closeCustomKeyboard();
                }
                sendScrollContentToBottomMsg();
            }
        });

        mContentEt.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    closeAllKeyboard();
                } else {
                    sendScrollContentToBottomMsg();
                }
            }
        });
    }

    /**
     * ??????????????????
     *
     * @return
     */
    public boolean isRecording() {
        return mRecorderKeyboardLayout.isRecording();
    }

    public interface Callback {
        /**
         * ????????????
         *
         * @param time     ????????????
         * @param filePath ??????????????????
         */
        void onAudioRecorderFinish(int time, String filePath);

        /**
         * ??????????????????
         */
        void onAudioRecorderTooShort();

        /**
         * ????????????????????????
         */
        void scrollContentToBottom();

        /**
         * ??????????????????
         */
        void onAudioRecorderNoPermission();
    }

    /**
     * ??????View
     *
     * @param id   ?????????id
     * @param <VT> View??????
     * @return
     */
    protected <VT extends View> VT getViewById(@IdRes int id) {
        return (VT) findViewById(id);
    }
}