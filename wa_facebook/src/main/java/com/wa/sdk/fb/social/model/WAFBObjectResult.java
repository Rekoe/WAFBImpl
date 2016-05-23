package com.wa.sdk.fb.social.model;

import com.wa.sdk.common.model.WAResult;
import com.wa.sdk.social.model.WAFBGraphObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 查询Object的返回数据实体类
 * Created by yinglovezhuzhu@gmail.com on 2016/3/23.
 */
public class WAFBObjectResult extends WAResult {
    private List<WAFBGraphObject> objects = new ArrayList<>();
    private WAFBPaging paging;

    public WAFBObjectResult() {

    }

    public List<WAFBGraphObject> getObjects() {
        return objects;
    }

    public void setObjects(List<WAFBGraphObject> objects) {
        this.objects = objects;
    }

    public void addObjects(Collection<WAFBGraphObject> objects) {
        if(null == objects || objects.isEmpty()) {
            return;
        }
        this.objects.addAll(objects);
    }

    public WAFBPaging getPaging() {
        return paging;
    }

    public void setPaging(WAFBPaging paging) {
        this.paging = paging;
    }

    @Override
    public String toString() {
        return "WAFBGraphObjectResult{" +
                "objects=" + objects +
                ", paging=" + paging +
                "} " + super.toString();
    }
}
