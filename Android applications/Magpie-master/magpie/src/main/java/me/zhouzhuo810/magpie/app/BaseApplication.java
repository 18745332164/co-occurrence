package me.zhouzhuo810.magpie.app;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.view.Gravity;

import com.hjq.toast.ToastUtils;

import me.zhouzhuo810.magpie.cons.Cons;
import me.zhouzhuo810.magpie.utils.BaseUtil;
import me.zhouzhuo810.magpie.utils.LanguageUtil;
import me.zhouzhuo810.magpie.utils.SimpleUtil;
import me.zhouzhuo810.magpie.utils.SpUtil;

public abstract class BaseApplication extends Application {
    
    @Override
    public void onCreate() {
        super.onCreate();
    
        BaseUtil.init(this);
    
        ToastUtils.init(this);
        ToastUtils.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.BOTTOM,
            0, SimpleUtil.getScaledValue(200));
    }
    
    @Override
    protected void attachBaseContext(Context base) {
        BaseUtil.updateContext(base);
        if (shouldSupportMultiLanguage()) {
            int language = SpUtil.getInt(base, Cons.SP_KEY_OF_CHOOSED_LANGUAGE);
            switch (language) {
                case LanguageUtil.SIMPLE_CHINESE:
                    super.attachBaseContext(LanguageUtil.attachBaseContext(base, Cons.SIMPLIFIED_CHINESE));
                    break;
                case LanguageUtil.TRADITIONAL_CHINESE:
                    super.attachBaseContext(LanguageUtil.attachBaseContext(base, Cons.TRADITIONAL_CHINESE));
                    break;
                case LanguageUtil.ENGLISH:
                    super.attachBaseContext(LanguageUtil.attachBaseContext(base, Cons.ENGLISH));
                    break;
                case LanguageUtil.VI:
                    super.attachBaseContext(LanguageUtil.attachBaseContext(base, Cons.VI));
                    break;
                case LanguageUtil.PT:
                    super.attachBaseContext(LanguageUtil.attachBaseContext(base, Cons.PT));
                    break;
                case LanguageUtil.RU:
                    super.attachBaseContext(LanguageUtil.attachBaseContext(base, Cons.RU));
                    break;
            }
        } else {
            super.attachBaseContext(base);
        }
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (shouldSupportMultiLanguage()) {
            LanguageUtil.updateApplicationLanguage();
        }
        SimpleUtil.resetScale(this);
    }
    
    /**
     * ?????????????????????
     *
     * @return true/false, ????????????false
     * <p>
     * ????????????false???????????????app???????????????????????????
     * <p>
     * ????????????true???????????????app?????????????????????
     * <p>
     * ????????????{@link Application#onCreate()}?????????
     * {@link LanguageUtil#setGlobalLanguage(int)}
     * ????????????????????????
     * <p>
     * ?????????????????????
     * <ul>
     * <li>{@link LanguageUtil#SIMPLE_CHINESE}</li>
     * <li>{@link LanguageUtil#TRADITIONAL_CHINESE }</li>
     * <li>{@link LanguageUtil#ENGLISH }</li>
     * </ul>
     */
    public abstract boolean shouldSupportMultiLanguage();
}
