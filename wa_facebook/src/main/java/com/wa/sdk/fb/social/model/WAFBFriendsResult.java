package com.wa.sdk.fb.social.model;

import com.wa.sdk.common.model.WAResult;
import com.wa.sdk.user.model.WAUser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Facebook查询好友结果实体类
 * Created by yinglovezhuzhu@gmail.com on 2016/3/23.
 */
public class WAFBFriendsResult extends WAResult {

    private List<WAUser> friends = new ArrayList<>();
    private WAFBPaging paging;
    private int total_count; // 这个是总好友数不一定就是返回的好友列表数，在gifting中，返回的好友列表是使用这个app的好友

    public WAFBFriendsResult() {

    }

    public List<WAUser> getFriends() {
        return friends;
    }

    public void setFriends(List<WAUser> friends) {
        this.friends = friends;
    }

    public WAFBPaging getPaging() {
        return paging;
    }

    public void setPaging(WAFBPaging paging) {
        this.paging = paging;
    }

    public int getTotal_count() {
        return total_count;
    }

    public void setTotal_count(int total_count) {
        this.total_count = total_count;
    }

    public void addFriends(Collection<WAUser> friends) {
        if(null == friends || friends.isEmpty()) {
            return;
        }
        if(null == this.friends) {
            this.friends = new ArrayList<>();
        }
        this.friends.addAll(friends);
    }

    @Override
    public String toString() {
        return "WAFBFriendsResult{" +
                "friends=" + friends +
                ", paging=" + paging +
                ", total_count=" + total_count +
                "} " + super.toString();
    }
}
