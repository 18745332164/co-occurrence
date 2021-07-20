package com.anthony.library.data.dao;


import com.anthony.library.data.bean.Channel;
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.stmt.QueryBuilder;

import java.sql.SQLException;
import java.util.List;


public class ChannelDao extends BaseDao<Channel> {


    public ChannelDao(OrmLiteSqliteOpenHelper sqliteOpenHelper) {
        super(sqliteOpenHelper);
    }

    public List<Channel> getChannelByTitle(String title) {
        try {
            QueryBuilder builder = daoOpe.queryBuilder();
            builder.where().eq("title", title);
            return builder.query();
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<Channel> getChannelByParentId(int id){
        try {
            QueryBuilder builder = daoOpe.queryBuilder();
            builder.where().eq("parentChannelId", id);
            return builder.query();
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Channel getChannelById(int id) {
        try {
            return daoOpe.queryForId(id);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<Channel> queryVisibleChannel() {
        try {
            QueryBuilder builder = daoOpe.queryBuilder();
            builder.distinct().where().eq("isSubscribe", 1).or().eq("isFix", 1);
            return builder.query();
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }


}
