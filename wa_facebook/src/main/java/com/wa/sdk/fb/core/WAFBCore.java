package com.wa.sdk.fb.core;

import android.app.Activity;
import android.content.Context;

import com.facebook.FacebookSdk;
import com.wa.sdk.WASdkInterfaceVersion;
import com.wa.sdk.common.utils.LogUtil;
import com.wa.sdk.core.WAICore;
import com.wa.sdk.fb.WAFBConstants;
import com.wa.sdk.fb.WAFBVersion;
import com.wa.sdk.fb.track.WAFBTrackApi;
import com.wa.sdk.fb.user.WAFBLogin;

/**
 * WA FB Sdk核心类
 * Created by yinglovezhuzhu@gmail.com on 2016/3/2.
 */
public class WAFBCore implements WAICore {

    @Override
    public void initialize(Activity activity) {
        WAFBVersion.SDK_VER = WASdkInterfaceVersion.BUILD + "_" + WAFBVersion.BUILD;
        WAFBConstants.TAG = LogUtil.TAG + "_" + WAFBVersion.SDK_VER;

        FacebookSdk.sdkInitialize(activity);

        WAFBTrackApi.getInstance().initialize(activity);

        WAFBLogin.getInstance().initialize(activity);
    }

    @Override
    public void setDebugMode(boolean debugMode) {
        FacebookSdk.setIsDebugEnabled(debugMode);
    }

    @Override
    public String getClientId(Context context) {
        return null;
    }

    @Override
    public boolean loadOnlineParameterAndWaite() {
        return false;
    }

    @Override
    public void reportInstallCampaign(Context context) {

    }
}
