package com.anthony.app.module.tab;

import android.os.Bundle;
import android.view.View;

import com.anthony.app.R;
import com.anthony.app.dagger.component.ActivityComponent;
import com.anthony.app.dagger.DaggerFragment;

/**
 * Created by Anthony on 2016/9/12.
 * Class Note:
 * 发现 fragment
 */
public class FindFragment extends DaggerFragment {

    @Override
    protected int getLayoutId() {
        return R.layout.prj_fragment_find;
    }

    @Override
    protected void initDagger2(ActivityComponent activityComponent) {

    }

    @Override
    protected void initViews(View rootView, Bundle savedInstanceState) {

    }

    @Override
    protected void loadData() {

    }
}
