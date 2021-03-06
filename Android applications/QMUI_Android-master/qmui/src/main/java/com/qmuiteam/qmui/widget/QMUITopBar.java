/*
 * Tencent is pleased to support the open source community by making QMUI_Android available.
 *
 * Copyright (C) 2017-2018 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the MIT License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/MIT
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.qmuiteam.qmui.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.SimpleArrayMap;

import com.qmuiteam.qmui.R;
import com.qmuiteam.qmui.alpha.QMUIAlphaImageButton;
import com.qmuiteam.qmui.layout.QMUIRelativeLayout;
import com.qmuiteam.qmui.qqface.QMUIQQFaceView;
import com.qmuiteam.qmui.skin.IQMUISkinHandlerView;
import com.qmuiteam.qmui.skin.QMUISkinManager;
import com.qmuiteam.qmui.skin.QMUISkinValueBuilder;
import com.qmuiteam.qmui.skin.defaultAttr.IQMUISkinDefaultAttrProvider;
import com.qmuiteam.qmui.skin.defaultAttr.QMUISkinSimpleDefaultAttrProvider;
import com.qmuiteam.qmui.util.QMUIDisplayHelper;
import com.qmuiteam.qmui.util.QMUILangHelper;
import com.qmuiteam.qmui.util.QMUIResHelper;
import com.qmuiteam.qmui.util.QMUIViewHelper;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A standard toolbar for use within application content.
 * <p>
 * <ul>
 * <li>add icon/text/custom-view in left or right.</li>
 * <li>set title and subtitle with gravity support.</li>
 * </ul>
 */
public class QMUITopBar extends QMUIRelativeLayout implements IQMUISkinHandlerView, IQMUISkinDefaultAttrProvider {

    private static final int DEFAULT_VIEW_ID = -1;
    private int mLeftLastViewId; // ???????????? view ??? id
    private int mRightLastViewId; // ???????????? view ??? id

    private View mCenterView; // ????????? View
    private LinearLayout mTitleContainerView; // ?????? title ??? subTitle ?????????
    private QMUIQQFaceView mTitleView; // ?????? title ????????? TextView
    private QMUIQQFaceView mSubTitleView; // ?????? subTitle ????????? TextView

    private List<View> mLeftViewList;
    private List<View> mRightViewList;
    private int mTitleGravity;
    private int mLeftBackDrawableRes;
    private int mLeftBackViewWidth;
    private boolean mClearLeftPaddingWhenAddLeftBackView;
    private int mTitleTextSize;
    private Typeface mTitleTypeface;
    private Typeface mSubTitleTypeface;
    private int mTitleTextSizeWithSubTitle;
    private int mSubTitleTextSize;
    private int mTitleTextColor;
    private int mSubTitleTextColor;
    private int mTitleMarginHorWhenNoBtnAside;
    private int mTitleContainerPaddingHor;
    private int mTopBarImageBtnWidth;
    private int mTopBarImageBtnHeight;
    private int mTopBarTextBtnPaddingHor;
    private ColorStateList mTopBarTextBtnTextColor;
    private int mTopBarTextBtnTextSize;
    private Typeface mTopBarTextBtnTypeface;
    private int mTopBarHeight = -1;
    private Rect mTitleContainerRect;
    private boolean mIsBackgroundSetterDisabled = false;
    private TruncateAt mEllipsize;

    private static SimpleArrayMap<String, Integer> sDefaultSkinAttrs;

    static {
        sDefaultSkinAttrs = new SimpleArrayMap<>(4);
        sDefaultSkinAttrs.put(QMUISkinValueBuilder.BOTTOM_SEPARATOR, R.attr.qmui_skin_support_topbar_separator_color);
        sDefaultSkinAttrs.put(QMUISkinValueBuilder.BACKGROUND, R.attr.qmui_skin_support_topbar_bg);
    }

    public QMUITopBar(Context context) {
        this(context, null);
    }

    public QMUITopBar(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.QMUITopBarStyle);
    }

    public QMUITopBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initVar();
        init(context, attrs, defStyleAttr);
    }

    private void initVar() {
        mLeftLastViewId = DEFAULT_VIEW_ID;
        mRightLastViewId = DEFAULT_VIEW_ID;
        mLeftViewList = new ArrayList<>();
        mRightViewList = new ArrayList<>();
    }

    void init(Context context, AttributeSet attrs) {
        init(context, attrs, R.attr.QMUITopBarStyle);
    }

    void init(Context context, AttributeSet attrs, int defStyleAttr) {
        TypedArray array = getContext().obtainStyledAttributes(attrs, R.styleable.QMUITopBar, defStyleAttr, 0);
        mLeftBackDrawableRes = array.getResourceId(R.styleable.QMUITopBar_qmui_topbar_left_back_drawable_id, R.drawable.qmui_icon_topbar_back);
        mLeftBackViewWidth = array.getDimensionPixelSize(R.styleable.QMUITopBar_qmui_topbar_left_back_width, -1);
        mClearLeftPaddingWhenAddLeftBackView = array.getBoolean(R.styleable.QMUITopBar_qmui_topbar_clear_left_padding_when_add_left_back_view, false);
        mTitleGravity = array.getInt(R.styleable.QMUITopBar_qmui_topbar_title_gravity, Gravity.CENTER);
        mTitleTextSize = array.getDimensionPixelSize(R.styleable.QMUITopBar_qmui_topbar_title_text_size, QMUIDisplayHelper.sp2px(context, 17));
        mTitleTextSizeWithSubTitle = array.getDimensionPixelSize(R.styleable.QMUITopBar_qmui_topbar_title_text_size_with_subtitle, QMUIDisplayHelper.sp2px(context, 16));
        mSubTitleTextSize = array.getDimensionPixelSize(R.styleable.QMUITopBar_qmui_topbar_subtitle_text_size, QMUIDisplayHelper.sp2px(context, 11));
        mTitleTextColor = array.getColor(R.styleable.QMUITopBar_qmui_topbar_title_color, QMUIResHelper.getAttrColor(context, R.attr.qmui_config_color_gray_1));
        mSubTitleTextColor = array.getColor(R.styleable.QMUITopBar_qmui_topbar_subtitle_color, QMUIResHelper.getAttrColor(context, R.attr.qmui_config_color_gray_4));
        mTitleMarginHorWhenNoBtnAside = array.getDimensionPixelSize(R.styleable.QMUITopBar_qmui_topbar_title_margin_horizontal_when_no_btn_aside, 0);
        mTitleContainerPaddingHor = array.getDimensionPixelSize(R.styleable.QMUITopBar_qmui_topbar_title_container_padding_horizontal, 0);
        mTopBarImageBtnWidth = array.getDimensionPixelSize(R.styleable.QMUITopBar_qmui_topbar_image_btn_width, QMUIDisplayHelper.dp2px(context, 48));
        mTopBarImageBtnHeight = array.getDimensionPixelSize(R.styleable.QMUITopBar_qmui_topbar_image_btn_height, QMUIDisplayHelper.dp2px(context, 48));
        mTopBarTextBtnPaddingHor = array.getDimensionPixelSize(R.styleable.QMUITopBar_qmui_topbar_text_btn_padding_horizontal, QMUIDisplayHelper.dp2px(context, 12));
        mTopBarTextBtnTextColor = array.getColorStateList(R.styleable.QMUITopBar_qmui_topbar_text_btn_color_state_list);
        mTopBarTextBtnTextSize = array.getDimensionPixelSize(R.styleable.QMUITopBar_qmui_topbar_text_btn_text_size, QMUIDisplayHelper.sp2px(context, 16));

        mTitleTypeface = array.getBoolean(R.styleable.QMUITopBar_qmui_topbar_title_bold, false) ? Typeface.DEFAULT_BOLD : null;
        mSubTitleTypeface = array.getBoolean(R.styleable.QMUITopBar_qmui_topbar_subtitle_bold, false) ? Typeface.DEFAULT_BOLD : null;
        mTopBarTextBtnTypeface = array.getBoolean(R.styleable.QMUITopBar_qmui_topbar_text_btn_bold, false) ? Typeface.DEFAULT_BOLD : null;
        int ellipsize = array.getInt(R.styleable.QMUITopBar_android_ellipsize, -1) ;
        switch (ellipsize) {
            case 1:
                mEllipsize = TextUtils.TruncateAt.START;
                break;
            case 2:
                mEllipsize = TextUtils.TruncateAt.MIDDLE;
                break;
            case 3:
                mEllipsize = TextUtils.TruncateAt.END;
                break;
            default:
                mEllipsize = null;
                break;
        }
        array.recycle();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ViewParent parent = getParent();
        while (parent instanceof View) {
            if (parent instanceof QMUICollapsingTopBarLayout) {
                makeSureTitleContainerView();
                return;
            }
            parent = parent.getParent();
        }
    }

    /**
     * ??? TopBar ??????????????? View???????????????????????? View ???????????????????????? TopBar????????????View?????? remove
     *
     * @param view ????????????TopBar?????????View
     */
    public void setCenterView(View view) {
        if (mCenterView == view) {
            return;
        }
        if (mCenterView != null) {
            removeView(mCenterView);
        }
        mCenterView = view;
        LayoutParams params = (LayoutParams) mCenterView.getLayoutParams();
        if (params == null) {
            params = new LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        params.addRule(RelativeLayout.CENTER_IN_PARENT);
        addView(view, params);
    }

    /**
     * ?????? TopBar ?????????
     *
     * @param resId TopBar ????????? resId
     */
    public QMUIQQFaceView setTitle(int resId) {
        return setTitle(getContext().getString(resId));
    }

    /**
     * ?????? TopBar ?????????
     *
     * @param title TopBar ?????????
     */
    public QMUIQQFaceView setTitle(String title) {
        QMUIQQFaceView titleView = ensureTitleView();
        titleView.setText(title);
        if (QMUILangHelper.isNullOrEmpty(title)) {
            titleView.setVisibility(GONE);
        } else {
            titleView.setVisibility(VISIBLE);
        }
        return titleView;
    }

    public CharSequence getTitle() {
        if (mTitleView == null) {
            return null;
        }
        return mTitleView.getText();
    }

    @Nullable
    public QMUIQQFaceView getTitleView(){
        return mTitleView;
    }

    public void showTitleView(boolean toShow) {
        if (mTitleView != null) {
            mTitleView.setVisibility(toShow ? VISIBLE : GONE);
        }
    }

    private QMUIQQFaceView ensureTitleView() {
        if (mTitleView == null) {
            mTitleView = new QMUIQQFaceView(getContext());
            mTitleView.setGravity(Gravity.CENTER);
            mTitleView.setSingleLine(true);
            mTitleView.setEllipsize(mEllipsize);
            mTitleView.setTypeface(mTitleTypeface);
            mTitleView.setTextColor(mTitleTextColor);
            QMUISkinSimpleDefaultAttrProvider provider = new QMUISkinSimpleDefaultAttrProvider();
            provider.setDefaultSkinAttr(QMUISkinValueBuilder.TEXT_COLOR, R.attr.qmui_skin_support_topbar_title_color);
            mTitleView.setTag(R.id.qmui_skin_default_attr_provider, provider);
            updateTitleViewStyle();
            LinearLayout.LayoutParams titleLp = generateTitleViewAndSubTitleViewLp();
            makeSureTitleContainerView().addView(mTitleView, titleLp);
        }

        return mTitleView;
    }

    /**
     * ?????? titleView ??????????????????????????? subTitle ????????? titleView ????????????
     */
    private void updateTitleViewStyle() {
        if (mTitleView != null) {
            if (mSubTitleView == null || QMUILangHelper.isNullOrEmpty(mSubTitleView.getText())) {
                mTitleView.setTextSize(mTitleTextSize);
            } else {
                mTitleView.setTextSize(mTitleTextSizeWithSubTitle);
            }
        }
    }

    /**
     * ?????? TopBar ????????????
     *
     * @param subTitle TopBar ????????????
     */
    public QMUIQQFaceView setSubTitle(CharSequence subTitle) {
        QMUIQQFaceView subTitleView = ensureSubTitleView();
        subTitleView.setText(subTitle);
        if (QMUILangHelper.isNullOrEmpty(subTitle)) {
            subTitleView.setVisibility(GONE);
        } else {
            subTitleView.setVisibility(VISIBLE);
        }
        // ?????? titleView ??????????????????????????? subTitle ????????? titleView ????????????
        updateTitleViewStyle();
        return subTitleView;
    }

    /**
     * ?????? TopBar ????????????
     *
     * @param resId TopBar ???????????? resId
     */
    public QMUIQQFaceView setSubTitle(int resId) {
        return setSubTitle(getResources().getString(resId));
    }

    private QMUIQQFaceView ensureSubTitleView() {
        if (mSubTitleView == null) {
            mSubTitleView = new QMUIQQFaceView(getContext());
            mSubTitleView.setGravity(Gravity.CENTER);
            mSubTitleView.setSingleLine(true);
            mSubTitleView.setTypeface(mSubTitleTypeface);
            mSubTitleView.setEllipsize(mEllipsize);
            mSubTitleView.setTextSize(mSubTitleTextSize);
            mSubTitleView.setTextColor(mSubTitleTextColor);
            QMUISkinSimpleDefaultAttrProvider provider = new QMUISkinSimpleDefaultAttrProvider();
            provider.setDefaultSkinAttr(QMUISkinValueBuilder.TEXT_COLOR, R.attr.qmui_skin_support_topbar_subtitle_color);
            mSubTitleView.setTag(R.id.qmui_skin_default_attr_provider, provider);
            LinearLayout.LayoutParams titleLp = generateTitleViewAndSubTitleViewLp();
            titleLp.topMargin = QMUIDisplayHelper.dp2px(getContext(), 1);
            makeSureTitleContainerView().addView(mSubTitleView, titleLp);
        }

        return mSubTitleView;
    }

    @Nullable
    public QMUIQQFaceView getSubTitleView(){
        return mSubTitleView;
    }

    /**
     * ?????? TopBar ??? gravity??????????????? title ??? subtitle ???????????????
     *
     * @param gravity ?????? {@link android.view.Gravity}
     */
    public void setTitleGravity(int gravity) {
        mTitleGravity = gravity;
        if (mTitleView != null) {
            ((LinearLayout.LayoutParams) mTitleView.getLayoutParams()).gravity = gravity;
            if (gravity == Gravity.CENTER || gravity == Gravity.CENTER_HORIZONTAL) {
                mTitleView.setPadding(getPaddingLeft(), getPaddingTop(), getPaddingLeft(), getPaddingBottom());
            }
        }
        if (mSubTitleView != null) {
            ((LinearLayout.LayoutParams) mSubTitleView.getLayoutParams()).gravity = gravity;
        }
        requestLayout();
    }

    public Rect getTitleContainerRect() {
        if (mTitleContainerRect == null) {
            mTitleContainerRect = new Rect();
        }
        if (mTitleContainerView == null) {
            mTitleContainerRect.set(0, 0, 0, 0);
        } else {
            QMUIViewHelper.getDescendantRect(this, mTitleContainerView, mTitleContainerRect);
        }
        return mTitleContainerRect;
    }

    public LinearLayout getTitleContainerView() {
        return mTitleContainerView;
    }

    void disableBackgroundSetter(){
        mIsBackgroundSetterDisabled = true;
        super.setBackgroundDrawable(null);
    }

    @Override
    public void setBackgroundDrawable(Drawable background) {
        if(!mIsBackgroundSetterDisabled){
            super.setBackgroundDrawable(background);
        }
    }

    // ========================= leftView???rightView ???????????????

    private LinearLayout makeSureTitleContainerView() {
        if (mTitleContainerView == null) {
            mTitleContainerView = new LinearLayout(getContext());
            // ???????????????????????????????????????????????????????????????
            mTitleContainerView.setOrientation(LinearLayout.VERTICAL);
            mTitleContainerView.setGravity(Gravity.CENTER);
            mTitleContainerView.setPadding(mTitleContainerPaddingHor, 0, mTitleContainerPaddingHor, 0);
            addView(mTitleContainerView, generateTitleContainerViewLp());
        }
        return mTitleContainerView;
    }

    /**
     * ?????? TitleContainerView ??? LayoutParams???
     * ???????????????????????? View ????????????????????????
     * ??????????????????????????? View ?????? TopBar ??????????????????????????????
     */
    private LayoutParams generateTitleContainerViewLp() {
        return new LayoutParams(LayoutParams.MATCH_PARENT,
                QMUIResHelper.getAttrDimen(getContext(), R.attr.qmui_topbar_height));
    }

    /**
     * ?????? titleView ??? subTitleView ??? LayoutParams
     */
    private LinearLayout.LayoutParams generateTitleViewAndSubTitleViewLp() {
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        // ????????????
        titleLp.gravity = mTitleGravity;
        return titleLp;
    }

    /**
     * ???TopBar???????????????View????????????????????????View????????????????????????TopBar????????????????????????View??????????????????View?????????
     *
     * @param view   ???????????? TopBar ????????? View
     * @param viewId ????????????id?????????ids.xml??????????????????????????????????????????viewId?????????????????????????????????
     */
    public void addLeftView(View view, int viewId) {
        ViewGroup.LayoutParams viewLayoutParams = view.getLayoutParams();
        LayoutParams layoutParams;
        if (viewLayoutParams != null && viewLayoutParams instanceof LayoutParams) {
            layoutParams = (LayoutParams) viewLayoutParams;
        } else {
            layoutParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        }
        this.addLeftView(view, viewId, layoutParams);
    }

    /**
     * ???TopBar???????????????View????????????????????????View????????????????????????TopBar????????????????????????View??????????????????View????????????
     *
     * @param view         ???????????? TopBar ????????? View???
     * @param viewId       ???????????? id????????? ids.xml ?????????????????????????????????????????? viewId ?????????????????????????????????
     * @param layoutParams ???????????? LayoutParams????????? Button addView ??? TopBar ?????????????????? LayouyParams???
     */
    public void addLeftView(View view, int viewId, LayoutParams layoutParams) {
        if (mLeftLastViewId == DEFAULT_VIEW_ID) {
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        } else {
            layoutParams.addRule(RelativeLayout.RIGHT_OF, mLeftLastViewId);
        }
        layoutParams.alignWithParent = true; // alignParentIfMissing
        mLeftLastViewId = viewId;
        view.setId(viewId);
        mLeftViewList.add(view);
        addView(view, layoutParams);
    }

    /**
     * ??? TopBar ??????????????? View???????????????????????? iew ???????????????????????? TopBar????????????????????????View??????????????????View?????????
     *
     * @param view   ???????????? TopBar ?????????View
     * @param viewId ????????????id????????? ids.xml ?????????????????????????????????????????? viewId ?????????????????????????????????
     */
    public void addRightView(View view, int viewId) {
        ViewGroup.LayoutParams viewLayoutParams = view.getLayoutParams();
        LayoutParams layoutParams;
        if (viewLayoutParams != null && viewLayoutParams instanceof LayoutParams) {
            layoutParams = (LayoutParams) viewLayoutParams;
        } else {
            layoutParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        }
        this.addRightView(view, viewId, layoutParams);
    }

    /**
     * ??? TopBar ??????????????? View???????????????????????? View ???????????????????????? TopBar???????????????????????? View ??????????????????View????????????
     *
     * @param view         ???????????? TopBar ????????? View???
     * @param viewId       ???????????? id????????? ids.xml ?????????????????????????????????????????? viewId ?????????????????????????????????
     * @param layoutParams ???????????? LayoutParams????????? Button addView ??? TopBar ?????????????????? LayouyParams???
     */
    public void addRightView(View view, int viewId, LayoutParams layoutParams) {
        if (mRightLastViewId == DEFAULT_VIEW_ID) {
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        } else {
            layoutParams.addRule(RelativeLayout.LEFT_OF, mRightLastViewId);
        }
        layoutParams.alignWithParent = true; // alignParentIfMissing
        mRightLastViewId = viewId;
        view.setId(viewId);
        mRightViewList.add(view);
        addView(view, layoutParams);
    }

    public LayoutParams generateTopBarImageButtonLayoutParams(){
        return generateTopBarImageButtonLayoutParams(-1, -1);
    }

    /**
     * ???????????? LayoutParams????????? Button addView ??? TopBar ?????????????????? LayouyParams
     */
    public LayoutParams generateTopBarImageButtonLayoutParams(int iconWidth, int iconHeight) {
        iconHeight = iconHeight > 0 ? iconHeight : mTopBarImageBtnHeight;
        LayoutParams lp = new LayoutParams(iconWidth > 0 ? iconWidth : mTopBarImageBtnWidth, iconHeight);
        lp.topMargin = Math.max(0, (getTopBarHeight() - iconHeight) / 2);
        return lp;
    }


    public QMUIAlphaImageButton addRightImageButton(int drawableResId, int viewId) {
        return addRightImageButton(drawableResId, true, viewId);
    }

    public QMUIAlphaImageButton addRightImageButton(int drawableResId, boolean followTintColor, int viewId) {
        return addRightImageButton(drawableResId, followTintColor, viewId, -1, -1);
    }

    /**
     * ?????? resourceId ???????????? TopBar ??????????????? add ??? TopBar ?????????
     *
     * @param drawableResId   ??????????????? resourceId
     * @param viewId          ???????????? id????????? ids.xml ?????????????????????????????????????????? viewId ?????????????????????????????????
     * @param followTintColor ??????????????? tintColor ??????????????????
     * @return ?????????????????????
     */
    public QMUIAlphaImageButton addRightImageButton(int drawableResId, boolean followTintColor, int viewId, int iconWidth, int iconHeight) {
        QMUIAlphaImageButton rightButton = generateTopBarImageButton(drawableResId, followTintColor);
        this.addRightView(rightButton, viewId, generateTopBarImageButtonLayoutParams(iconWidth, iconHeight));
        return rightButton;
    }

    public QMUIAlphaImageButton addLeftImageButton(int drawableResId, int viewId) {
        return addLeftImageButton(drawableResId, true, viewId);
    }

    public QMUIAlphaImageButton addLeftImageButton(int drawableResId, boolean followTintColor, int viewId) {
        return addLeftImageButton(drawableResId, followTintColor, viewId, -1, -1);
    }

    /**
     * ?????? resourceId ???????????? TopBar ??????????????? add ??? TopBar ?????????
     *
     * @param drawableResId   ??????????????? resourceId
     * @param viewId          ???????????? id?????????ids.xml?????????????????????????????????????????? viewId ?????????????????????????????????
     * @param followTintColor ??????????????? tintColor ??????????????????
     * @return ?????????????????????
     */
    public QMUIAlphaImageButton addLeftImageButton(int drawableResId, boolean followTintColor, int viewId, int iconWidth, int iconHeight) {
        QMUIAlphaImageButton leftButton = generateTopBarImageButton(drawableResId, followTintColor);
        this.addLeftView(leftButton, viewId, generateTopBarImageButtonLayoutParams(iconWidth, iconHeight));
        return leftButton;
    }

    /**
     * ????????????LayoutParams????????? Button addView ??? TopBar ?????????????????? LayouyParams
     */
    public LayoutParams generateTopBarTextButtonLayoutParams() {
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, mTopBarImageBtnHeight);
        lp.topMargin = Math.max(0, (getTopBarHeight() - mTopBarImageBtnHeight) / 2);
        return lp;
    }

    /**
     * ??? TopBar ?????????????????? Button??????????????????
     *
     * @param stringResId ?????????????????? resourceId
     * @param viewId      ????????????id????????? ids.xml ?????????????????????????????????????????? viewId ?????????????????????????????????
     * @return ?????????????????????
     */
    public Button addLeftTextButton(int stringResId, int viewId) {
        return addLeftTextButton(getResources().getString(stringResId), viewId);
    }

    /**
     * ??? TopBar ?????????????????? Button??????????????????
     *
     * @param buttonText ???????????????
     * @param viewId     ???????????? id????????? ids.xml ?????????????????????????????????????????? viewId ?????????????????????????????????
     * @return ?????????????????????
     */
    public Button addLeftTextButton(String buttonText, int viewId) {
        Button button = generateTopBarTextButton(buttonText);
        this.addLeftView(button, viewId, generateTopBarTextButtonLayoutParams());
        return button;
    }

    /**
     * ??? TopBar ?????????????????? Button??????????????????
     *
     * @param stringResId ?????????????????? resourceId
     * @param viewId      ????????????id????????? ids.xml ?????????????????????????????????????????? viewId ?????????????????????????????????
     * @return ?????????????????????
     */
    public Button addRightTextButton(int stringResId, int viewId) {
        return addRightTextButton(getResources().getString(stringResId), viewId);
    }

    /**
     * ??? TopBar ?????????????????? Button??????????????????
     *
     * @param buttonText ???????????????
     * @param viewId     ???????????? id????????? ids.xml ?????????????????????????????????????????? viewId ?????????????????????????????????
     * @return ?????????????????????
     */
    public Button addRightTextButton(String buttonText, int viewId) {
        Button button = generateTopBarTextButton(buttonText);
        this.addRightView(button, viewId, generateTopBarTextButtonLayoutParams());
        return button;
    }


    private IQMUISkinDefaultAttrProvider mTopBarTextDefaultAttrProvider;

    /**
     * ??????????????????????????????????????????
     *
     * @param text ???????????????
     * @return ?????????????????????
     */
    private Button generateTopBarTextButton(String text) {
        Button button = new Button(getContext());
        if (mTopBarTextDefaultAttrProvider == null) {
            QMUISkinSimpleDefaultAttrProvider provider = new QMUISkinSimpleDefaultAttrProvider();
            provider.setDefaultSkinAttr(
                    QMUISkinValueBuilder.TEXT_COLOR, R.attr.qmui_skin_support_topbar_text_btn_color_state_list);
            mTopBarTextDefaultAttrProvider = provider;

        }
        button.setTag(R.id.qmui_skin_default_attr_provider, mTopBarTextDefaultAttrProvider);
        button.setBackgroundResource(0);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setTypeface(mTopBarTextBtnTypeface);
        button.setPadding(mTopBarTextBtnPaddingHor, 0, mTopBarTextBtnPaddingHor, 0);
        button.setTextColor(mTopBarTextBtnTextColor);
        button.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTopBarTextBtnTextSize);
        button.setGravity(Gravity.CENTER);
        button.setText(text);
        return button;
    }


    private IQMUISkinDefaultAttrProvider mTopBarImageColorTintColorProvider;

    /**
     * ????????????????????????????????? {{@link #generateTopBarImageButtonLayoutParams()} ??????
     *
     * @param imageResourceId ????????? resId
     */
    private QMUIAlphaImageButton generateTopBarImageButton(int imageResourceId, boolean followTintColor) {
        QMUIAlphaImageButton imageButton = new QMUIAlphaImageButton(getContext());
        if (followTintColor) {
            if (mTopBarImageColorTintColorProvider == null) {
                QMUISkinSimpleDefaultAttrProvider provider = new QMUISkinSimpleDefaultAttrProvider();
                provider.setDefaultSkinAttr(
                        QMUISkinValueBuilder.TINT_COLOR, R.attr.qmui_skin_support_topbar_image_tint_color);
                mTopBarImageColorTintColorProvider = provider;
            }
            imageButton.setTag(R.id.qmui_skin_default_attr_provider, mTopBarImageColorTintColorProvider);
        }
        imageButton.setBackgroundColor(Color.TRANSPARENT);
        imageButton.setImageResource(imageResourceId);
        return imageButton;
    }

    /**
     * ?????????????????? TopBar ????????????????????????????????????
     *
     * @return ????????????
     */
    public QMUIAlphaImageButton addLeftBackImageButton() {
        if(mClearLeftPaddingWhenAddLeftBackView){
            QMUIViewHelper.setPaddingLeft(this, 0);
        }
        if(mLeftBackViewWidth > 0){
            return addLeftImageButton(mLeftBackDrawableRes, true, R.id.qmui_topbar_item_left_back, mLeftBackViewWidth, -1);
        }
        return addLeftImageButton(mLeftBackDrawableRes, R.id.qmui_topbar_item_left_back);
    }

    /**
     * ?????? TopBar ??????????????? View
     */
    public void removeAllLeftViews() {
        for (View leftView : mLeftViewList) {
            removeView(leftView);
        }
        mLeftLastViewId = DEFAULT_VIEW_ID;
        mLeftViewList.clear();
    }

    /**
     * ?????? TopBar ??????????????? View
     */
    public void removeAllRightViews() {
        for (View rightView : mRightViewList) {
            removeView(rightView);
        }
        mRightLastViewId = DEFAULT_VIEW_ID;
        mRightViewList.clear();
    }

    /**
     * ?????? TopBar ??? centerView ??? titleView
     */
    public void removeCenterViewAndTitleView() {
        if (mCenterView != null) {
            if (mCenterView.getParent() == this) {
                removeView(mCenterView);
            }
            mCenterView = null;
        }

        if (mTitleView != null) {
            if (mTitleView.getParent() == this) {
                removeView(mTitleView);
            }
            mTitleView = null;
        }
    }

    int getTopBarHeight() {
        if (mTopBarHeight == -1) {
            mTopBarHeight = QMUIResHelper.getAttrDimen(getContext(), R.attr.qmui_topbar_height);
        }
        return mTopBarHeight;
    }

    /**
     * ?????? TopBar ??????????????????
     *
     * @param alpha ???????????????[0, 255]???255???????????????
     */
    public void setBackgroundAlpha(int alpha) {
        this.getBackground().mutate().setAlpha(alpha);
    }

    /**
     * ???????????? offset??????????????????????????? offset ????????? offset?????????????????? Topbar ????????????
     *
     * @param currentOffset     ?????? offset
     * @param alphaBeginOffset  ????????????????????????offset????????? currentOffset == alphaBeginOffset ??????????????????0
     * @param alphaTargetOffset ????????????????????????offset????????? currentOffset == alphaTargetOffset ??????????????????1
     */
    public int computeAndSetBackgroundAlpha(int currentOffset, int alphaBeginOffset, int alphaTargetOffset) {
        double alpha = (float) (currentOffset - alphaBeginOffset) / (alphaTargetOffset - alphaBeginOffset);
        alpha = Math.max(0, Math.min(alpha, 1)); // from 0 to 1
        int alphaInt = (int) (alpha * 255);
        setBackgroundAlpha(alphaInt);
        return alphaInt;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mTitleContainerView != null) {
            // ???????????? View ????????????
            int leftViewWidth = getPaddingLeft();
            for (int leftViewIndex = 0; leftViewIndex < mLeftViewList.size(); leftViewIndex++) {
                View view = mLeftViewList.get(leftViewIndex);
                if (view.getVisibility() != GONE) {
                    leftViewWidth += view.getMeasuredWidth();
                }
            }
            // ???????????? View ????????????
            int rightViewWidth = getPaddingRight();
            for (int rightViewIndex = 0; rightViewIndex < mRightViewList.size(); rightViewIndex++) {
                View view = mRightViewList.get(rightViewIndex);
                if (view.getVisibility() != GONE) {
                    rightViewWidth += view.getMeasuredWidth();
                }
            }

            leftViewWidth = Math.max(mTitleMarginHorWhenNoBtnAside, leftViewWidth);
            rightViewWidth = Math.max(mTitleMarginHorWhenNoBtnAside, rightViewWidth);

            // ?????? titleContainer ???????????????
            int titleContainerWidth;
            if ((mTitleGravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.CENTER_HORIZONTAL) {


                // ?????????????????????????????????????????????????????????
                titleContainerWidth = MeasureSpec.getSize(widthMeasureSpec) -
                        Math.max(leftViewWidth, rightViewWidth) * 2;
            } else {
                // ??????????????????????????????????????????????????????????????????
                titleContainerWidth = MeasureSpec.getSize(widthMeasureSpec) - leftViewWidth - rightViewWidth;
            }
            int titleContainerWidthMeasureSpec = MeasureSpec.makeMeasureSpec(titleContainerWidth, MeasureSpec.EXACTLY);
            mTitleContainerView.measure(titleContainerWidthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (mTitleContainerView != null) {
            int titleContainerViewWidth = mTitleContainerView.getMeasuredWidth();
            int titleContainerViewHeight = mTitleContainerView.getMeasuredHeight();
            int titleContainerViewTop = (b - t - mTitleContainerView.getMeasuredHeight()) / 2;
            int titleContainerViewLeft = getPaddingLeft();
            if ((mTitleGravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.CENTER_HORIZONTAL) {
                // ??????????????????
                titleContainerViewLeft = (r - l - mTitleContainerView.getMeasuredWidth()) / 2;
            } else {
                // ?????????????????????
                // ???????????? View ????????????
                for (int leftViewIndex = 0; leftViewIndex < mLeftViewList.size(); leftViewIndex++) {
                    View view = mLeftViewList.get(leftViewIndex);
                    if (view.getVisibility() != GONE) {
                        titleContainerViewLeft += view.getMeasuredWidth();
                    }
                }

                titleContainerViewLeft = Math.max(titleContainerViewLeft, mTitleMarginHorWhenNoBtnAside);
            }
            mTitleContainerView.layout(titleContainerViewLeft, titleContainerViewTop,
                    titleContainerViewLeft + titleContainerViewWidth,
                    titleContainerViewTop + titleContainerViewHeight);
        }
    }

    @Override
    public void handle(@NotNull QMUISkinManager manager, int skinIndex, @NotNull Resources.Theme theme, @Nullable SimpleArrayMap<String, Integer> attrs) {
        if (attrs != null) {
            for (int i = 0; i < attrs.size(); i++) {
                String key = attrs.keyAt(i);
                Integer attr = attrs.valueAt(i);
                if (attr == null) {
                    continue;
                }
                if (getParent() instanceof QMUITopBarLayout &&
                        (QMUISkinValueBuilder.BACKGROUND.equals(key) ||
                                QMUISkinValueBuilder.BOTTOM_SEPARATOR.equals(key))) {
                    // handled by parent
                    continue;
                }
                manager.defaultHandleSkinAttr(this, theme, key, attr);
            }
        }
    }


    @Override
    public SimpleArrayMap<String, Integer> getDefaultSkinAttrs() {
        return sDefaultSkinAttrs;
    }

    public void eachLeftRightView(@NonNull Action action){
        for(int i = 0; i < mLeftViewList.size(); i++){
            action.call(mLeftViewList.get(i), i, true);
        }
        for(int i = 0; i < mRightViewList.size(); i++){
            action.call(mRightViewList.get(i), i, false);
        }
    }

    public interface Action {
        void call(View view, int index, boolean isLeftView);
    }
}
