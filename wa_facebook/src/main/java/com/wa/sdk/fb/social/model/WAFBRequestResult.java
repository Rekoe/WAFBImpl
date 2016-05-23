package com.wa.sdk.fb.social.model;

import com.wa.sdk.common.model.WAResult;
import com.wa.sdk.social.model.WAFBGameRequestData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 应用邀请查询返回数据实体类
 * Created by yinglovezhuzhu@gmail.com on 2016/3/23.
 */
public class WAFBRequestResult extends WAResult {

    private List<WAFBGameRequestData> requests = new ArrayList<>();
    private WAFBPaging paging;

    public List<WAFBGameRequestData> getRequests() {
        return requests;
    }

    public WAFBRequestResult() {

    }

    public void setRequests(List<WAFBGameRequestData> requests) {
        this.requests = requests;
    }

    public void addRequests(Collection<WAFBGameRequestData> requests) {
        if(null == requests || requests.isEmpty()) {
            return;
        }
        if(null == this.requests) {
            this.requests = new ArrayList<>();
        }
        this.requests.addAll(requests);
    }

    public WAFBPaging getPaging() {
        return paging;
    }

    public void setPaging(WAFBPaging paging) {
        this.paging = paging;
    }

    @Override
    public String toString() {
        return "WAFBRequestResult{" +
                "requests=" + requests +
                ", paging=" + paging +
                "} " + super.toString();
    }
}
