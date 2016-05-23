package com.wa.sdk.fb.social.share.internal;

import com.wa.sdk.common.model.WACallback;

/**
 * 分享内部处理工具
 * Created by yinglovezhuzhu@gmail.com on 2016/3/21.
 */
public class WAFBShareInternalUtility {

    public static void invokeCallbackWithError(WACallback callback, int code, String message) {
        invokeOnErrorCallback(callback, code, message);
    }

    static void invokeOnErrorCallback(WACallback callback, int code, String message) {
        if (callback != null) {
            callback.onError(code, message, null, null);
        }
    }
}
