package com.anthony.app.module.wechatlist;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.widget.ImageView;

import com.anthony.app.R;
import com.anthony.app.dagger.DaggerActivity;
import com.anthony.app.dagger.component.ActivityComponent;
import com.anthony.library.base.WebviewDetailsActivity;
import com.anthony.library.data.net.HttpSubscriber;
import com.anthony.pullrefreshview.PullToRefreshView;
import com.anthony.rvhelper.adapter.CommonAdapter;
import com.anthony.rvhelper.base.ViewHolder;
import com.anthony.rvhelper.divider.RecycleViewDivider;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import rx.functions.Action0;

/**
 * Created by Anthony on 2016/11/30.
 * Class Note:
 * 微信文章的界面，支持下拉刷新和上拉加载更多
 * <p>
 * <p>
 * todo 数据库存储
 * todo 封装上啦下拉+recyclerView+adapter的实现
 */

public class WechatListActivity extends DaggerActivity {
    @BindView(R.id.recycleView)
    RecyclerView recycleView;
    @BindView(R.id.ptr)
    PullToRefreshView ptr;
    @BindView(R.id.toolbar)
    Toolbar toolbar;

    private WechatItemAdapter mAdapter;

    private static final int PAGE_NUM = 10;
    private static final int INIT_PAGE_INDEX = 1;
    private int currentPageIndex = INIT_PAGE_INDEX;


    @Override
    protected void initViewsAndEvents(Bundle savedInstanceState) {
        //show loading but not include toolbar
        List<Integer> skipIds = new ArrayList<>();
        skipIds.add(com.anthony.library.R.id.toolbar);
        showLoading(skipIds);

        setToolBar(toolbar, "微信文章列表");

        ptr.setListener(new PullToRefreshView.OnRefreshListener() {
            @Override
            public void onRefresh() {
                getWechatData(PAGE_NUM, INIT_PAGE_INDEX);
            }

            @Override
            public void onLoadMore() {
                getWechatData(PAGE_NUM, currentPageIndex + 1);
            }
        });

        mAdapter = new WechatItemAdapter(mContext);
        recycleView.addItemDecoration(new RecycleViewDivider(mContext, LinearLayoutManager.VERTICAL));
        recycleView.setLayoutManager(new LinearLayoutManager(mContext));
        recycleView.setAdapter(mAdapter);

        getWechatData(PAGE_NUM, INIT_PAGE_INDEX);


    }

    private void getWechatData(int num, int page) {
        getDataRepository()
                .getWechatData(num, page)
                .doOnTerminate(new Action0() {
                    @Override
                    public void call() {
                        ptr.onFinishLoading();
                    }
                })
                .subscribe(new HttpSubscriber<List<WXItemBean>>() {
                    @Override
                    public void onNext(List<WXItemBean> itemList) {
                        onDataReceived(page, itemList);
                    }

                    @Override
                    public void onError(Throwable e) {
                        super.onError(e);
                        showToast("load error");
                    }
                });
    }

    private void onDataReceived(int page, List<WXItemBean> itemList) {
        boolean isRefresh = (page == INIT_PAGE_INDEX);
        if (isRefresh) {
            mAdapter.clearData();
        } else {
            currentPageIndex += 1;
        }

        mAdapter.addDataAll(itemList);
        mAdapter.notifyDataSetChanged();

        showContent();
    }

    @Override
    protected int getContentViewID() {
        return R.layout.prj_pull_list;
    }

    @Override
    protected void injectDagger(ActivityComponent activityComponent) {
        activityComponent.inject(this);
    }


    public class WechatItemAdapter extends CommonAdapter<WXItemBean> {
        public WechatItemAdapter(Context context) {
            super(context, R.layout.prj_item_weichat);
        }


        @Override
        protected void convert(ViewHolder holder, final WXItemBean item, int position) {
            Glide.with(mContext).load(item.getPicUrl()).crossFade().into((ImageView) holder.getView(R.id.iv_wechat_item_image));
            holder.setText(R.id.tv_wechat_item_title, item.getTitle())
                    .setText(R.id.tv_wechat_item_from, item.getDescription())
                    .setText(R.id.tv_wechat_item_time, item.getCtime())
                    .setOnClickListener(R.id.ll_click, v -> {
                        WebviewDetailsActivity.start(mContext, item.getTitle(), item.getUrl());
                    });

        }
    }
}
