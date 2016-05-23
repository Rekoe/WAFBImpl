package com.wa.sdk.fb.track;

import android.app.Activity;
import android.content.Context;

import com.wa.sdk.track.WAITrack;
import com.wa.sdk.track.model.WAEvent;

/**
 * Facebook 数据收集实现
 * Created by yinglovezhuzhu@gmail.com on 2016/3/10.
 */
public class WAFBTrack implements WAITrack {
    @Override
    public void startHeartBeat(Activity activity) {
        WAFBTrackApi.getInstance().startHeartBeat(activity);
    }

    @Override
    public void stopHeartBeat(Activity activity) {
        WAFBTrackApi.getInstance().stopHeartBeat(activity);
    }

    @Override
    public void trackEvent(Context context, WAEvent event) {
        WAFBTrackApi.getInstance().trackEvent(context, event);
    }

    @Override
    public void setUserId(String userId) {

    }
}
