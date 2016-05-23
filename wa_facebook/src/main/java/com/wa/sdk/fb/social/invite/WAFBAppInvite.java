package com.wa.sdk.fb.social.invite;

import android.app.Activity;
import android.content.Intent;

import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.internal.CallbackManagerImpl;
import com.facebook.share.model.AppInviteContent;
import com.facebook.share.widget.AppInviteDialog;
import com.wa.sdk.common.model.WACallback;
import com.wa.sdk.common.model.WACallbackManagerImpl;
import com.wa.sdk.common.model.WAResult;
import com.wa.sdk.fb.WAFacebook;
import com.wa.sdk.fb.user.WAFBLogin;
import com.wa.sdk.user.model.WALoginResult;

/**
 * Facebook邀请
 * Created by yinglovezhuzhu@gmail.com on 2016/3/22.
 */
public class WAFBAppInvite extends WAFacebook {

    private Activity mActivity;

    private String mAppLinkUrl;
    private String mPreviewImageUrl;
    private WACallback<WAResult> mInviteCallback;

    private WACallback<WALoginResult> mLoginCallback = new WACallback<WALoginResult>() {
        @Override
        public void onSuccess(int code, String message, WALoginResult result) {
//            showLoadingDialog(null, null);
            checkAccount(null, new WACallback<WAResult>() {

                @Override
                public void onSuccess(int code, String message, WAResult result) {
                    cancelLoadingDialog();
                    sendAppInvite();
                }

                @Override
                public void onCancel() {
                    cancelLoadingDialog();
                    if (null != mInviteCallback) {
                        mInviteCallback.onCancel();
                    }
                }

                @Override
                public void onError(int code, String message, WAResult result, Throwable throwable) {
                    cancelLoadingDialog();
                    switch (code) {
                        case CODE_NOT_LOGIN_WITH_FACEBOOK: // 没有登录Facebook
                        case CODE_NO_PERMISSION: // 没有相应权限
                        case CODE_FACEBOOK_ACCOUNT_NOT_MATCH: // 授权的Facebook账户和当前用户关联的Facebook账户不匹配
                        case CODE_ERROR:
                        default:
                            if (null != mInviteCallback) {
                                mInviteCallback.onError(code, message, null, null);
                            }
                            break;
                    }
                }
            });
        }

        @Override
        public void onCancel() {
            cancelLoadingDialog();
            if(null != mInviteCallback) {
                mInviteCallback.onCancel();
            }
        }

        @Override
        public void onError(int code, String message, WALoginResult result, Throwable throwable) {
            cancelLoadingDialog();
            if(null != mInviteCallback) {
                mInviteCallback.onError(WACallback.CODE_LOGIN_FAILURE,
                        "Login with facebook failed:" + (null == throwable ?
                                "" : throwable.getMessage()), null, throwable);
            }
        }
    };

    public static WAFBAppInvite newInstance() {
        return new WAFBAppInvite();
    }

    private WAFBAppInvite() {
        super();
        registerCallbackImpl(mCallbackManager,
                CallbackManagerImpl.RequestCodeOffset.AppInvite.toRequestCode(),
                new WACallbackManagerImpl.Callback() {
                    @Override
                    public boolean onActivityResult(int resultCode, Intent data) {
                        return mFBCallbackManager.onActivityResult(CallbackManagerImpl.RequestCodeOffset.AppInvite.toRequestCode(),
                                resultCode, data);
                    }
                });
    }

    public void appInvite(final Activity activity, String appLinkUrl, String previewImageUrl,
                                 final WACallback<WAResult> callback) {
        this.mActivity = activity;
        this.mAppLinkUrl = appLinkUrl;
        this.mPreviewImageUrl = previewImageUrl;
        this.mInviteCallback = callback;

        showLoadingDialog(activity, null, null);
        checkAccount(null, new WACallback<WAResult>() {
            @Override
            public void onSuccess(int code, String message, WAResult result) {
                cancelLoadingDialog();
                sendAppInvite();
            }

            @Override
            public void onCancel() {
                cancelLoadingDialog();
                if (null != mInviteCallback) {
                    mInviteCallback.onCancel();
                }
            }

            @Override
            public void onError(int code, String message, WAResult result, Throwable throwable) {
                switch (code) {
                    case CODE_NOT_LOGIN_WITH_FACEBOOK: // 没有登录Facebook
                    case CODE_NO_PERMISSION: // 没有相应权限
                    case CODE_FACEBOOK_ACCOUNT_NOT_MATCH: // 授权的Facebook账户和当前用户关联的Facebook账户不匹配
                        WAFBLogin.getInstance().login(mActivity, true, mLoginCallback, null);
                        break;
                    case CODE_ERROR:
                    default:
                        cancelLoadingDialog();
                        if (null != mInviteCallback) {
                            mInviteCallback.onError(code, message, null, null);
                        }
                        break;
                }
            }
        });
    }


    /**
     * 发送邀请
     */
    private void sendAppInvite() {
        final AppInviteDialog appInviteDialog = new AppInviteDialog(mActivity);
        appInviteDialog.registerCallback(mFBCallbackManager, new FacebookCallback<AppInviteDialog.Result>() {
            @Override
            public void onSuccess(AppInviteDialog.Result result) {
                if (null != mInviteCallback) {
                    mInviteCallback.onSuccess(WACallback.CODE_SUCCESS, "AppInvite success", null);
                }
            }

            @Override
            public void onCancel() {
                if (null != mInviteCallback) {
                    mInviteCallback.onCancel();
                }
            }

            @Override
            public void onError(FacebookException error) {
                if (null != mInviteCallback) {
                    mInviteCallback.onError(WACallback.CODE_ERROR, "AppInvite error", null, error);
                }
            }
        });
        if (AppInviteDialog.canShow()) {
            AppInviteContent content = new AppInviteContent.Builder()
                    .setApplinkUrl(mAppLinkUrl)
                    .setPreviewImageUrl(mPreviewImageUrl)
                    .build();
            appInviteDialog.show(content);
        } else {
            if(null != mInviteCallback) {
                mInviteCallback.onError(WACallback.CODE_ERROR, "App invite unusable!", null, null);
            }
        }
    }

}
