package com.wa.sdk.fb.core;

import android.content.Context;

import com.facebook.applinks.AppLinkData;
import com.wa.sdk.common.model.WACallback;

/**
 * Facebook渠道工具类，这个工具类直供内部渠道间的一个反射调用，不提供外部调用，请不要随意移动
 * Created by yinglovezhuzhu@gmail.com on 16-3-14.
 */
public class WAFBTool {

    /**
     * Fetch facebook deferred app deep link
     * @param context 上下文
     * @param callback 回调
     */
    public static void fetchDeferredAppLinkData(Context context, final WACallback<String> callback) {
        AppLinkData.fetchDeferredAppLinkData(context, new AppLinkData.CompletionHandler() {
            @Override
            public void onDeferredAppLinkDataFetched(AppLinkData appLinkData) {
                if(null == appLinkData) {
                    if(null != callback) {
                        callback.onError(WACallback.CODE_ERROR, "No app link data", null, null);
                    }
                    return;
                }
                final String appLineUrl = appLinkData.getTargetUri().toString();
                if(null != callback) {
                    callback.onSuccess(WACallback.CODE_SUCCESS, "", appLineUrl);
                }
            }
        });
    }

}
