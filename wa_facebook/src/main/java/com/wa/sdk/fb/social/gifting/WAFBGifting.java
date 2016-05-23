package com.wa.sdk.fb.social.gifting;

import android.app.Activity;

import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.share.model.GameRequestContent;
import com.facebook.share.widget.GameRequestDialog;
import com.wa.sdk.common.model.WACallback;
import com.wa.sdk.common.model.WAResult;
import com.wa.sdk.fb.social.WAFBGameService;
import com.wa.sdk.fb.user.WAFBLogin;
import com.wa.sdk.social.model.WAGiftingResult;
import com.wa.sdk.user.model.WALoginResult;

import java.util.List;

/**
 * Facebook礼物
 * Created by yinglovezhuzhu@gmail.com on 2016/3/23.
 */
public class WAFBGifting extends WAFBGameService {

    private WACallback<WAGiftingResult> mGiftingCallback;

    private String mTitle;
    private String mMessage;
    private List<String> mIds;
    private String mObjectType;
    private String mRequestActionType;
    private GameRequestContent.ActionType mGiftActionType;
    private String mObjectId;

    public static WAFBGifting newInstance() {
        return new WAFBGifting();
    }

    private WAFBGifting() {
        super();
    }

    @Override
    protected void onLoginFBSuccess(int code, String message, WALoginResult result) {
        PendingAction previouslyPendingAction = mPendingAction;
        switch (previouslyPendingAction) {
            case GIFTING:
                checkAccount(null, new WACallback<WAResult>() {
                    @Override
                    public void onSuccess(int code, String message, WAResult result) {
                        sendGiftingRequest();
                    }

                    @Override
                    public void onCancel() {
                        if (null != mGiftingCallback) {
                            mGiftingCallback.onCancel();
                        }
                    }

                    @Override
                    public void onError(int code, String message, WAResult result, Throwable throwable) {
                        switch (code) {
                            case CODE_NOT_LOGIN_WITH_FACEBOOK: // 没有登录Facebook
                            case CODE_NO_PERMISSION: // 没有相应权限
                            case CODE_FACEBOOK_ACCOUNT_NOT_MATCH: // 授权的Facebook账户和当前用户关联的Facebook账户不匹配
                            case CODE_ERROR:
                            default:
                                if (null != mGiftingCallback) {
                                    mGiftingCallback.onError(code, message, null, null);
                                }
                                break;
                        }
                    }
                });
                break;
            default:
                break;
        }
    }

    @Override
    protected void onLoginFBCanceled() {
        switch (mPendingAction) {
            case GIFTING:
                if(null != mGiftingCallback) {
                    mGiftingCallback.onCancel();
                }
                break;
            default:
                break;
        }
    }

    @Override
    protected void onLoginFBError(int code, String message, WALoginResult result, Throwable throwable) {
        switch (mPendingAction) {
            case GIFTING:
                if(null != mGiftingCallback) {
                    mGiftingCallback.onError(code, message, null, throwable);
                }
                break;
            default:
                break;
        }
    }


    /**
     * 礼物请求，赠送/索要礼物
     * @param activity 调用Activity
     * @param title 标题
     * @param message 消息
     * @param actionType 类型（赠送/索要）
     * @param objectId 礼物id
     * @param ids 赠送好友的id
     * @param callback 回调方法
     */
    public void giftingRequest(Activity activity, String title, String message,
                            final GameRequestContent.ActionType actionType,
                            final String objectId, final List<String> ids,
                            final WACallback<WAGiftingResult> callback) {
        this.mActivity = activity;
        this.mTitle = title;
        this.mMessage = message;
        this.mGiftActionType = actionType;
        this.mObjectId = objectId;
        this.mIds = ids;
        this.mGiftingCallback = callback;

        mPendingAction = PendingAction.GIFTING;
        checkAccount(null, new WACallback<WAResult>() {
            @Override
            public void onSuccess(int code, String message, WAResult result) {
                sendGiftingRequest();
            }

            @Override
            public void onCancel() {
                if (null != mGiftingCallback) {
                    mGiftingCallback.onCancel();
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
                        if (null != mGiftingCallback) {
                            mGiftingCallback.onError(code, message, null, null);
                        }
                        break;
                }
            }
        });
    }



    /**
     * 发出礼物请求（赠送/索要礼物 请求）
     */
    private void sendGiftingRequest() {
        if(null == mIds || mIds.isEmpty()) {
            // 邀请的好友为空
            if(null != mGiftingCallback) {
                mGiftingCallback.onError(WACallback.CODE_ERROR, "The friends list is null!", null, null);
            }
            return;
        }

        if(mIds.size() > 50) {
            // 一次最多邀请50个好友
            if(null != mGiftingCallback) {
                mGiftingCallback.onError(WACallback.CODE_ERROR,
                        "The number of friends to invite should not be more then 50 at once time!", null, null);
            }
            return;
        }
        mRequestDialog = new GameRequestDialog(mActivity);
        mRequestDialog.registerCallback(mFBCallbackManager, new FacebookCallback<GameRequestDialog.Result>() {
            @Override
            public void onSuccess(GameRequestDialog.Result result) {
                final WAGiftingResult inviteResult = new WAGiftingResult();
                inviteResult.setCode(WACallback.CODE_SUCCESS);
                inviteResult.setMessage("Gifting request send success!");
                inviteResult.setRequestId(result.getRequestId());
                inviteResult.setRecipients(result.getRequestRecipients());
                if (null != mGiftingCallback) {
                    mGiftingCallback.onSuccess(inviteResult.getCode(), inviteResult.getMessage(), inviteResult);
                }
            }

            @Override
            public void onCancel() {
                if (null != mGiftingCallback) {
                    mGiftingCallback.onCancel();
                }
            }

            @Override
            public void onError(FacebookException error) {
                if (null != mGiftingCallback) {
                    mGiftingCallback.onError(WACallback.CODE_ERROR, "Gifting error with exception:"
                            + (null == error ? "" : error.toString()), null, error);
                }
            }
        });
        GameRequestContent requestContent = new GameRequestContent.Builder()
                .setActionType(mGiftActionType)
                .setTitle(mTitle)
                .setMessage(mMessage)
                .setRecipients(mIds)
                .setObjectId(mObjectId)
                .build();
        mRequestDialog.show(requestContent);
    }
}
