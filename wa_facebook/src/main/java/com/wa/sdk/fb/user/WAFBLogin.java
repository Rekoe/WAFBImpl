package com.wa.sdk.fb.user;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.facebook.AccessToken;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.internal.CallbackManagerImpl;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.wa.sdk.WAConstants;
import com.wa.sdk.common.WAConfig;
import com.wa.sdk.common.WASharedPrefHelper;
import com.wa.sdk.common.model.WACallback;
import com.wa.sdk.common.model.WACallbackManagerImpl;
import com.wa.sdk.common.utils.LogUtil;
import com.wa.sdk.common.utils.StringUtil;
import com.wa.sdk.core.WAComponentFactory;
import com.wa.sdk.fb.WAFBConstants;
import com.wa.sdk.fb.WAFacebook;
import com.wa.sdk.user.WAIUser;
import com.wa.sdk.user.model.WALoginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Facebook 登录实现
 * Created by yinglovezhuzhu@gmail.com on 2016/3/3.
 */
public class WAFBLogin extends WAFacebook {

    private static final String PERMISSION_READ = "read";
    private static final String PERMISSION_PUBLISH = "publish";

    private WACallback<WALoginResult> mWACallback = null;
    private static WAFBLogin mInstance = null;

    private WASharedPrefHelper mSharedPrefHelper;
    private boolean mInitialized = false;
    private boolean mOnlyLoginPlatform = false;

//    private Object mWALoginObject = null;
private WAIUser mWAUser;

    private WAFBLogin() {
        registerCallbackImpl(mCallbackManager,
                CallbackManagerImpl.RequestCodeOffset.Login.toRequestCode(),
                new WACallbackManagerImpl.Callback() {
                    @Override
                    public boolean onActivityResult(int resultCode, Intent data) {
                        return mFBCallbackManager.onActivityResult(
                                CallbackManagerImpl.RequestCodeOffset.Login.toRequestCode(), resultCode, data);
                    }
                });
        LoginManager.getInstance().registerCallback(mFBCallbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                if(null != loginResult && null != loginResult.getAccessToken()) {
                    WALoginResult result = new WALoginResult();
                    final AccessToken accessToken = loginResult.getAccessToken();
                    if(mOnlyLoginPlatform) {
                        if(null != mWACallback) {
                            result.setCode(WACallback.CODE_SUCCESS);
                            result.setMessage("Login Facebook success");
                            result.setPlatform(WAConstants.CHANNEL_FACEBOOK);
                            result.setPlatformUserId(accessToken.getUserId());
                            result.setPlatformToken(accessToken.getToken());
                            mWACallback.onSuccess(WACallback.CODE_SUCCESS, "Login Facebook success\"", result);
                        }
                        return;
                    }

                    String cachedLoginType = mSharedPrefHelper.getString(WAConfig.SP_KEY_LOGIN_PLATFORM, "");
                    String cachedPlatformUserId = mSharedPrefHelper.getString(WAConfig.SP_KEY_LOGIN_PLATFORM_USER_ID, "");
                    String userId = "";
                    String token = "";
                    if(WAConstants.CHANNEL_FACEBOOK.equals(cachedLoginType)
                            && cachedPlatformUserId.equals(accessToken.getUserId())) {
                        userId = mSharedPrefHelper.getString(WAConfig.SP_KEY_LOGIN_WA_USER_ID, "");
                        token = mSharedPrefHelper.getString(WAConfig.SP_KEY_LOGIN_WA_TOKEN, "");
                    } else {
                        // 登录类型改变了，将前一次的登录缓存清掉
                        mSharedPrefHelper.remove(WAConfig.SP_KEY_LOGIN_WA_USER_ID);
                        mSharedPrefHelper.remove(WAConfig.SP_KEY_LOGIN_WA_TOKEN);
                    }
                    loginWA(userId, token, WAConstants.CHANNEL_FACEBOOK,
                            accessToken.getUserId(), accessToken.getToken(),
                            new WACallback<WALoginResult>() {
                                @Override
                                public void onSuccess(int code, String message, WALoginResult result) {
                                    if(null != mWACallback) {
                                        if(null == result) {
                                            mWACallback.onError(WACallback.CODE_ERROR, "Unknown error", null, null);
                                            return;
                                        }
                                        mWACallback.onSuccess(code, message, result);
                                    }
                                }

                                @Override
                                public void onCancel() {
                                    if(null != mWACallback) {
                                        mWACallback.onCancel();
                                    }
                                }

                                @Override
                                public void onError(int code, String message, WALoginResult result, Throwable throwable) {
                                    if(null != mWACallback) {
                                        if(null != result) {
                                            result.setPlatform(WAConstants.CHANNEL_FACEBOOK);
                                            result.setPlatformUserId(accessToken.getUserId());
                                            result.setPlatformToken(accessToken.getToken());
                                        }
                                        mWACallback.onError(code, message, result, throwable);
                                    }
                                }
                            });
                } else {
                    if(null != mWACallback) {
                        mWACallback.onError(WACallback.CODE_ERROR,
                                "Login with facebook failed: return data is null", null, null);
                    }
                }
            }

            @Override
            public void onCancel() {
                if(null != mWACallback) {
                    mWACallback.onCancel();
                }
            }

            @Override
            public void onError(FacebookException error) {
                if(null != mWACallback) {
                    mWACallback.onError(WACallback.CODE_ERROR,
                            "Login with facebook failed: with exception error", null, error);
                }
            }
        });
    }

    public static WAFBLogin getInstance() {
        synchronized (WAFBLogin.class) {
            if(null == mInstance) {
                mInstance = new WAFBLogin();
            }
            return mInstance;
        }
    }

    public void initialize(Context context) {
        if(mInitialized) {
            return;
        }
        Context appContext = context.getApplicationContext();
        mSharedPrefHelper = WASharedPrefHelper.newInstance(appContext, WAConfig.SHARE_PRE_LOGIN_CONFIG);

        mWAUser = (WAIUser) WAComponentFactory.createComponent(WAConstants.CHANNEL_WA, WAConstants.MODULE_USER);
        if(null != mWAUser) {
            mWAUser.initialize(context);
        }
        mInitialized = true;
    }

    public void login(Activity activity, boolean onlyLoginPlatform,
                      WACallback<WALoginResult> callback, String extInfo) {
        if(!mInitialized) {
            LogUtil.e(WAFBConstants.TAG, "WAFBUser--Sdk uninitialized!");
            if(null != callback) {
                callback.onError(WACallback.CODE_ERROR, "Sdk uninitialized", null, null);
            }
            return;
        }
        if(StringUtil.isEmpty(extInfo)) {
            login(activity, true, onlyLoginPlatform, callback);
        } else {
            try {
                FBLoginExtInfo info = parseExtInfo(extInfo);
                if(info.permissions.isEmpty()) {
                    if(null != callback) {
                        callback.onError(WACallback.CODE_ERROR,
                                "Facebook login permissions is empty", null, null);
                    }
                    return;
                }
                if(PERMISSION_READ.equals(info.permissionType)) {
                    loginWithReadPermissions(activity, info.permissions, true, onlyLoginPlatform, callback);
                } else if(PERMISSION_PUBLISH.equals(info.permissionType)) {
                    loginWithPublishPermissions(activity, info.permissions, true, onlyLoginPlatform, callback);
                } else {
                    if(null != callback) {
                        callback.onError(WACallback.CODE_ERROR,
                                "Unsupported permission type: " + info.permissionType, null, null);
                    }
                }
            } catch (JSONException e) {
                if(null != callback) {
                    String message = "Parse extInfo error:" + e.getMessage() + "\n"
                            + "extInfo in Facebook login should be a json string like this:\n" +
                            "{\n" +
                            "  \"permissionType\": \"read\",\n" +
                            "  \"permissions\": [\n" +
                            "    \"public_profile\",\n" +
                            "    \"user_friends\"\n" +
                            "  ]\n" +
                            "}";
                    callback.onError(WACallback.CODE_ERROR, message, null, null);
                }
            }
        }
    }

    /**
     * 登出
     */
    public void logout() {
        if(null == mSharedPrefHelper) {
            return;
        }
        LoginManager.getInstance().logOut();
        mWACallback = null;
        mOnlyLoginPlatform = false;

        String loginPlatform = mSharedPrefHelper.getString(WAConfig.SP_KEY_LOGIN_PLATFORM, "");
        if(!WAConstants.CHANNEL_FACEBOOK.equals(loginPlatform)) { // 只能当前登录类型是Facebook的时候才会做登出操作
            return;
        }
        logoutWA();
    }

    /**
     * 登陆，默认的读取用户信息的权限（public_profile）
     * @param activity Activity对象
     * @param onlyLoginPlatform 只登陆平台账户，如果为true，只返回Facebook登录信息，不会返回PCL后台登录信息
     * @param needLogout 是否需要做登出操作
     * @param callback 回调
     */
    private void login(Activity activity, boolean needLogout, boolean onlyLoginPlatform,
                       WACallback<WALoginResult> callback) {
        loginWithReadPermissions(activity, Arrays.asList(WAConstants.FB_PERMISSION_PUBLIC_PROFILE,
                        WAConstants.FB_PERMISSION_USER_FRIENDS), needLogout,
                onlyLoginPlatform, callback);
    }

    /**
     * 以读取的权限登陆，权限通过权限参数传入
     * @param activity Activity
     * @param permissions 传入的读取权限
     * @param needLogout 登录前是否执行logout操作
     * @param onlyLoginPlatform 只登陆平台账户，如果为true，只返回Facebook登录信息，不会返回PCL后台登录信息
     * @param callback 回调
     */
    private void loginWithReadPermissions(Activity activity, Collection<String> permissions, boolean needLogout,
                                          boolean onlyLoginPlatform, WACallback<WALoginResult> callback) {
        if(needLogout) {
            // 清除上一次的登录信息，防止没有Logout的情况下切换账号登录出现无法登录的问题
            LoginManager.getInstance().logOut();
        }
        this.mOnlyLoginPlatform = onlyLoginPlatform;
        this.mWACallback = callback;
        LogUtil.d(WAFBConstants.TAG, "WAFBLogin--loginWithReadPermissions " + permissions.toString());
        LoginManager.getInstance().logInWithReadPermissions(activity, permissions);
    }

    /**
     * 以发布的权限登陆，权限通过权限参数传入
     * @param activity Activity
     * @param permissions 传入的发布权限
     * @param needLogout 登录前是否执行logout操作
     * @param onlyLoginPlatform 只登陆平台账户，如果为true，只返回Facebook登录信息，不会返回PCL后台登录信息
     * @param callback 回调
     */
    private void loginWithPublishPermissions(Activity activity, Collection<String> permissions, boolean needLogout,
                                             boolean onlyLoginPlatform, WACallback<WALoginResult> callback) {
        // 清除上一次的登录信息，防止没有Logout的情况下切换账号登录出现无法登录的问题
        if(needLogout) {
            // 清除上一次的登录信息，防止没有Logout的情况下切换账号登录出现无法登录的问题
            LoginManager.getInstance().logOut();
        }
        this.mOnlyLoginPlatform = onlyLoginPlatform;
        this.mWACallback = callback;
        LogUtil.d(WAFBConstants.TAG, "WAFBLogin--loginWithPublishPermissions " + permissions.toString());
        LoginManager.getInstance().logInWithPublishPermissions(activity, permissions);
    }

    /**
     * 解析Facebook登录中传入的extInfo数据，数据是json格式字符串，格式如下：<br/>
     * <code>
     * {
     *  "permissionType": "read",
     *  "permissions": [
     *      "public_profile",
     *      "user_friends"
     *   ]
     * }
     * </code>
     * @param extInfo 数据字符串
     * @return 解析结果
     * @throws JSONException 数据格式不合法时抛出
     */
    private FBLoginExtInfo parseExtInfo(String extInfo) throws JSONException {
        FBLoginExtInfo info = new FBLoginExtInfo();
        JSONObject jsonObject = new JSONObject(extInfo);
        info.permissionType = jsonObject.optString("permissionType");
        JSONArray jsonArray = jsonObject.optJSONArray("permissions");
        if(null != jsonArray) {
            String permission;
            for (int i = 0; i < jsonArray.length(); i++) {
                permission = jsonArray.optString(i);
                if(!StringUtil.isEmpty(permission)) {
                   info.permissions.add(permission);
                }
            }
        }
        return info;
    }


    /**
     * 登陆WA后台<br/>
     * 注意：通过反射调用WA Sdk，需要先集成WA Sdk
     * @param userId userId
     * @param token token
     * @param platform 平台
     * @param platformUserId 平台userId
     * @param platformToken 平台Token
     * @param callback 回调
     */
    private void loginWA(String userId, String token, String platform,
                        String platformUserId, String platformToken,
                        WACallback<WALoginResult> callback) {
        if(null == mWAUser) {
            if(null != callback) {
                callback.onError(WACallback.CODE_ERROR, "Dependence of WA Sdk, " +
                        "you need integrate WA Sdk first", null, null);
            }
            return;
        }
        mWAUser.loginWA(userId, token, platform, platformUserId, platformToken, callback);
    }

    /**
     * 登出<br/>
     * 注意：通过反射调用WA Sdk，需要先集成WA Sdk
     */
    private void logoutWA() {
        if(null != mWAUser) {
            mWAUser.logout();
        }
    }

    private static class FBLoginExtInfo {
        String permissionType;
        List<String> permissions = new ArrayList<>();
    }
}
