package com.wa.sdk.fb;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.login.LoginManager;
import com.wa.sdk.WAConstants;
import com.wa.sdk.common.model.WACallback;
import com.wa.sdk.common.model.WACallbackManager;
import com.wa.sdk.common.model.WACallbackManagerImpl;
import com.wa.sdk.common.model.WAResult;
import com.wa.sdk.common.utils.LogUtil;
import com.wa.sdk.common.utils.StringUtil;
import com.wa.sdk.core.WAComponentFactory;
import com.wa.sdk.core.WASdkProperties;
import com.wa.sdk.user.WAIUser;
import com.wa.sdk.user.model.WAAccount;
import com.wa.sdk.user.model.WAAccountResult;
import com.wa.sdk.user.model.WAUser;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Facebook相关的公共代码
 * Created by yinglovezhuzhu@gmail.com on 2016/3/16.
 */
public class WAFacebook {

    protected static final WACallbackManager mCallbackManager = WACallbackManager.Factory.create();

    protected static final CallbackManager mFBCallbackManager = CallbackManager.Factory.create();

    protected Object mLoadingDialogObject;

    /**
     * 检查账户有效性，查询好友和查询礼物（收到的、发送的、索要礼物），
     * 只有登录操作登录的用户是当前用户绑定的Facebook账户，才能通过校验
     * @param permissions 需要检查的权限列表，如果不需要可以传null
     * @param callback
     */
    protected void checkAccount(Collection<String> permissions,
                              final WACallback<WAResult> callback) {

        if(!WASdkProperties.getInstance().isLogin()) {
            if(null != callback) {
                callback.onError(WACallback.CODE_NOT_LOGIN, "Not login yet", null, null);
            }
            return;
        }

        // 检查登录状态（没有登录、Token失效），这个错误需要重新登录
        if(isTokenExpires()) {
            // Token 失效或者没有登录
            if(null != callback) {
                callback.onError(WACallback.CODE_NOT_LOGIN_WITH_FACEBOOK, "Not login with facebook", null, null);
            }
            return;
        }

        // 检查权限（没有权限）
        if(null != permissions && !permissions.isEmpty()) {
            for (String permission :permissions) {
                if(!hasPermission(permission)) {
                    // 没有相应的权限
                    if(null != callback) {
                        callback.onError(WACallback.CODE_NO_PERMISSION, "No permission : " + permission, null, null);
                    }
                    return;
                }
            }
        }

        queryBoundAccount(true, new WACallback<WAAccountResult>() {
            @Override
            public void onSuccess(int code, String message, WAAccountResult result) {
                if (null == result) {
                    if (null != callback) {
                        callback.onError(WACallback.CODE_ERROR, "Check account failed", null, null);
                    }
                    // 登出刚刚登录的Facebook账户
                    LoginManager.getInstance().logOut();
                    return;
                }
                List<WAAccount> boundAccounts = result.getAccounts();
                Map<String, String> accountMap = new HashMap<String, String>();
                for (WAAccount account : boundAccounts) {
                    accountMap.put(account.getPlatform(), account.getPlatformUserId());
                }
                if (accountMap.containsKey(WAConstants.CHANNEL_FACEBOOK)) {
                    // 当前用户已绑定的Facebook账号id
                    String boundFBAccountId = accountMap.get(WAConstants.CHANNEL_FACEBOOK);

                    AccessToken accessToken = AccessToken.getCurrentAccessToken();
                    // 登录操作获取到的Facebook账号id
                    String loginFBAccountId = null == accessToken ? "" : accessToken.getUserId();

                    if (!StringUtil.isEmpty(boundFBAccountId) && boundFBAccountId.equals(loginFBAccountId)) {
                        // 当前用户绑定的Facebook账户id与 登录的Facebook账户id一致，可以进行后续操作
                        if (null != callback) {
                            callback.onSuccess(WACallback.CODE_SUCCESS, "Check account success", null);
                        }
                    } else {
                        // 当前用户绑定的Facebook账户id与 登录的Facebook账户id不一致，不可以进行后续操作
                        if (null != callback) {
                            callback.onError(WACallback.CODE_FACEBOOK_ACCOUNT_NOT_MATCH,
                                    "Current user not match this facebook account ", null, null);
                        }
                        // 登出刚刚登录的Facebook账户
                        LoginManager.getInstance().logOut();
                    }
                } else {
                    // 当前账户没有绑定此Facebook账户
                    if (null != callback) {
                        callback.onError(WACallback.CODE_FACEBOOK_ACCOUNT_NOT_MATCH,
                                "Current user not match this facebook account ", null, null);
                    }
                    // 登出刚刚登录的Facebook账户
                    LoginManager.getInstance().logOut();
                }
            }

            @Override
            public void onCancel() {
                if (null != callback) {
                    callback.onCancel();
                }
            }

            @Override
            public void onError(int code, String message, WAAccountResult result, Throwable throwable) {
                if (null != callback) {
                    callback.onError(code, message, null, null);
                }
            }
        });
    }



    /**
     * 是否有某个权限
     * @param permission 权限名称
     * @return
     */
    protected boolean hasPermission(String permission) {
        AccessToken accessToken = AccessToken.getCurrentAccessToken();
        return accessToken != null && accessToken.getPermissions().contains(permission);
    }

    /**
     * Token是否过期
     *
     * @return
     */
    protected boolean isTokenExpires() {
        AccessToken accessToken = AccessToken.getCurrentAccessToken();
        return null == accessToken || System.currentTimeMillis() > accessToken.getExpires().getTime();
    }

    protected void registerCallbackImpl(final WACallbackManager callbackManager, final int requestCode,
                                                final WACallbackManagerImpl.Callback callback) {
        if (!(callbackManager instanceof WACallbackManagerImpl)) {
            throw new IllegalStateException("Unexpected CallbackManager, " +
                    "please use the provided Factory.");
        }

        ((WACallbackManagerImpl) callbackManager).registerCallback(requestCode, callback);

    }

    /**
     * 查询已绑定账号列表（通过反射调用WA Sdk功能，需要先集成WA Sdk）
     * @param filterGuest 是否过滤掉WA渠道（WA渠道类型的时设备）
     * @param callback 回调
     */
    protected void queryBoundAccount(boolean filterGuest, WACallback<WAAccountResult> callback) {
        if(WASdkProperties.getInstance().isComponentSupported(WAConstants.CHANNEL_WA, WAConstants.MODULE_USER)) {
            WAIUser waUser = (WAIUser) WAComponentFactory.createComponent(WAConstants.CHANNEL_WA, WAConstants.MODULE_USER);
            if(null == waUser) {
                if(null != callback) {
                    callback.onError(WACallback.CODE_ERROR, "Dependence of WA Sdk, " +
                            "you need integrate WA Sdk first", null, null);
                }
                return;
            }
            waUser.queryBoundAccount(filterGuest, callback);
            return;
        }
        if(null != callback) {
            callback.onError(WACallback.CODE_ERROR, "Dependence of WA Sdk, " +
                    "you need integrate WA Sdk first", null, null);
        }
    }

    /**
     * 查询Facebook open id对应的 wa用户id，有网络请求，请不要在UI线程中调用
     * @param users
     * @return
     */
    protected List<WAUser> queryWAUserIdAndWaite(List<WAUser> users) {
        if(WASdkProperties.getInstance().isComponentSupported(WAConstants.CHANNEL_WA, WAConstants.MODULE_USER)) {
            WAIUser waUser = (WAIUser) WAComponentFactory.createComponent(WAConstants.CHANNEL_WA, WAConstants.MODULE_USER);
            if(null != waUser) {
                return waUser.queryWAUserIdAndWaite(WAConstants.CHANNEL_FACEBOOK, users);
            }
        }
        return null;
    }

    /**
     * 显示LoadingDialog
     * @param context 上下文
     * @param message 消息
     * @param cancelListener 取消监听
     * @return
     */
    protected void showLoadingDialog(Context context, String message,
                                              DialogInterface.OnCancelListener cancelListener) {
        String className = "com.wa.sdk.wa.widget.LoadingDialog";
        try {
            Class<?> cls = Class.forName(className);
            Method showMethod = cls.getDeclaredMethod("showLoadingDialog",
                    Context.class, String.class, boolean.class, boolean.class,
                    DialogInterface.OnCancelListener.class);
            mLoadingDialogObject = showMethod.invoke(null, context, message, true, false, cancelListener);
        } catch (Exception e) {
            LogUtil.w(WAFBConstants.TAG, "Depend on WA Sdk, please integrate WA Sdk first\n"
                    + LogUtil.getStackTrace(e));
        }
    }

    /**
     * 隐藏LoadingDialog
     */
    protected void cancelLoadingDialog() {
        if(isLoadingDialogShowing()) {
            try {
                Method cancelMethod = mLoadingDialogObject.getClass().getDeclaredMethod("cancel");
                cancelMethod.invoke(mLoadingDialogObject);
            } catch (Exception e) {
                LogUtil.w(WAFBConstants.TAG, "Depend on WA Sdk, please integrate WA Sdk first\n"
                        + LogUtil.getStackTrace(e));
            }
        }
        mLoadingDialogObject = null;
    }

    /**
     * 对话框是否在显示
     * @return true显示，false不显示
     */
    private boolean isLoadingDialogShowing() {
        if(null == mLoadingDialogObject) {
            return false;
        }
        try {
            Method isShowingMethod = mLoadingDialogObject.getClass().getDeclaredMethod("isShowing");
            Object result = isShowingMethod.invoke(mLoadingDialogObject);
            return Boolean.valueOf(String.valueOf(result));
        } catch (Exception e) {
            LogUtil.w(WAFBConstants.TAG, "Depend on WA Sdk, please integrate WA Sdk first\n"
                    + LogUtil.getStackTrace(e));
        }
        return false;
    }

    public static boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        return mCallbackManager.onActivityResult(requestCode, resultCode, data);
    }
}
