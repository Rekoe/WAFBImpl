package com.wa.sdk.fb.user;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.wa.sdk.common.model.WACallback;
import com.wa.sdk.common.model.WAResult;
import com.wa.sdk.user.WAIUser;
import com.wa.sdk.user.model.WALoginResult;
import com.wa.sdk.user.model.WAAccountCallback;
import com.wa.sdk.user.model.WAAccountResult;
import com.wa.sdk.user.model.WABindCallback;
import com.wa.sdk.user.model.WAUser;

import java.util.List;

/**
 * Facebook 用户账户实现类
 * Created by yinglovezhuzhu@gmail.com on 2016/3/1.
 */
public class WAFBUser implements WAIUser {

    @Override
    public void initialize(Context context) {
        WAFBLogin.getInstance().initialize(context);
    }

    @Override
    public void loginWA(String userId, String token, String platform, String platformUserId,
                        String platformToken, WACallback<WALoginResult> callback) {

    }

    @Override
    public void login(Activity activity, boolean onlyLoginPlatform,
                      WACallback<WALoginResult> callback, String extInfo) {
        WAFBLogin.getInstance().login(activity, onlyLoginPlatform, callback, extInfo);
    }

    @Override
    public void loginUI(Activity activity, boolean enableCache, WACallback<WALoginResult> callback) {

    }

    @Override
    public void logout() {
        WAFBLogin.getInstance().logout();
    }

    @Override
    public void bindingAccount(Activity activity, String platform, String extInfo, WABindCallback callback) {

    }

    @Override
    public void queryBoundAccount(boolean filterGuest, WACallback<WAAccountResult> callback) {

    }

    @Override
    public void unBindAccount(String platform, String platformUserId, WACallback<WAResult> callback) {

    }

    @Override
    public void createNewAccount(WACallback<WALoginResult> callback) {

    }

    @Override
    public List<WAUser> queryWAUserIdAndWaite(String platform, List<WAUser> users) {
        return null;
    }

    @Override
    public void openAccountManager(Activity activity, WAAccountCallback callback) {

    }


    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        return WAFBLogin.onActivityResult(requestCode, resultCode, data);
    }
}
