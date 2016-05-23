package com.wa.sdk.fb.social.invite;

import android.app.Activity;

import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.share.model.GameRequestContent;
import com.facebook.share.widget.GameRequestDialog;
import com.wa.sdk.common.WASharedPrefHelper;
import com.wa.sdk.common.model.WACallback;
import com.wa.sdk.common.model.WAResult;
import com.wa.sdk.common.utils.WAUtil;
import com.wa.sdk.core.WASdkProperties;
import com.wa.sdk.fb.social.WAFBGameService;
import com.wa.sdk.fb.user.WAFBLogin;
import com.wa.sdk.social.model.WAInviteResult;
import com.wa.sdk.user.model.WALoginResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Facebook Game Service 邀请
 * Created by yinglovezhuzhu@gmail.com on 16-3-23.
 */
public class WAFBGameInvite extends WAFBGameService {

    private WACallback<WAInviteResult> mInviteCallback;

    private String mTitle;
    private String mMessage;
    private List<String> mIds;

    public static WAFBGameInvite newInstance() {
        return new WAFBGameInvite();
    }

    private WAFBGameInvite() {
        super();
    }

    @Override
    protected void onLoginFBSuccess(int code, String message, WALoginResult result) {
        checkAccount(null, new WACallback<WAResult>() {
            @Override
            public void onSuccess(int code, String message, WAResult result) {
                cancelLoadingDialog();
                sendInviteRequest();
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

    @Override
    protected void onLoginFBCanceled() {
        if(null != mInviteCallback) {
            mInviteCallback.onCancel();
        }
    }

    @Override
    protected void onLoginFBError(int code, String message, WALoginResult result, Throwable throwable) {
        if(null != mInviteCallback) {
            mInviteCallback.onError(code, message, null, throwable);
        }
    }

    /**
     * 发起邀请，一次最多可以邀请50个好友
     * @param activity 调用activity
     * @param title 标题
     * @param message 描述
     * @param ids 邀请的好友id列表
     * @param callback 回调
     */
    public void gameInvite(final Activity activity, final String title,
                           final String message, final List<String> ids,
                           final WACallback<WAInviteResult> callback) {
        this.mActivity = activity;
        this.mInviteCallback = callback;
        this.mTitle = title;
        this.mMessage = message;
        this.mIds = ids;
        showLoadingDialog(activity, null, null);
        checkAccount(null, new WACallback<WAResult>() {
            @Override
            public void onSuccess(int code, String message, WAResult result) {
                cancelLoadingDialog();
                sendInviteRequest();
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
     * 发送邀请请求
     */
    private void sendInviteRequest() {
        if(null == mIds || mIds.isEmpty()) {
            // 邀请的好友为空
            if(null != mInviteCallback) {
                mInviteCallback.onError(WACallback.CODE_ERROR, "The friends to invite is null!", null, null);
            }
            return;
        }
        if(mIds.size() > 50) {
            // 一次最多邀请50个好友
            if(null != mInviteCallback) {
                mInviteCallback.onError(WACallback.CODE_ERROR,
                        "The number of friends to invite should not be more then 50 at once time!", null, null);
            }
            return;
        }
        if(null == mSharedPrefHelper) {
            mSharedPrefHelper = WASharedPrefHelper.newInstance(mActivity.getApplicationContext(),
                    FB_RECENT_INVITE_FRIENDS);
        }
        mRequestDialog = new GameRequestDialog(mActivity);
        mRequestDialog.registerCallback(mFBCallbackManager, new FacebookCallback<GameRequestDialog.Result>() {
            @Override
            public void onSuccess(GameRequestDialog.Result result) {
                final WAInviteResult inviteResult = new WAInviteResult();
                inviteResult.setCode(WACallback.CODE_SUCCESS);
                inviteResult.setMessage("Invite request send success!");
                inviteResult.setRequestId(result.getRequestId());
                inviteResult.setRecipients(result.getRequestRecipients());
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        saveInvitedFriendIds(mIds);
                    }
                }).start();
                if (null != mInviteCallback) {
                    mInviteCallback.onSuccess(inviteResult.getCode(), inviteResult.getMessage(), inviteResult);
                }

                // 通知服务器，创建邀请记录
//                List<String> recipients = result.getRequestRecipients();
//                WAFBInviteReward.getInstance().createInviteRecord(mActivity.getApplicationContext(),
//                        result.getRequestId(), recipients, null);
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
                    mInviteCallback.onError(WACallback.CODE_ERROR, "Invite error with exception:"
                            + (null == error ? "" : error.toString()), null, error);
                }
            }
        });

        String appId = WAUtil.getMataDataFromManifest(mActivity, WASdkProperties.META_KEY_SDK_APP_ID);
        String userId = WASdkProperties.getInstance().getUserId();
        String serverId =  WASdkProperties.getInstance().getServerId();
        GameRequestContent requestContent = new GameRequestContent.Builder()
                .setTitle(mTitle)
                .setMessage(mMessage)
                .setRecipients(mIds)
                .setData(createInviteDataJson(userId, serverId, appId))
                .build();
        mRequestDialog.show(requestContent);
    }

    /**
     * 生成邀请时传入data字段的字符串数据
     * @param userId 用户id
     * @param serverId 用户服务器id
     * @param appId 应用id
     * @return json格式的字符串
     */
    private String createInviteDataJson(String userId, String serverId, String appId) {
        JSONObject jsonObject = new JSONObject();
//        {"appId":"appId01","fromUserId":"123","fromServerId":"server01","actionType":"invite"}
        try {
            jsonObject.putOpt("actionType", FB_ACTION_TYPE_INVITE);
            jsonObject.putOpt("appId", appId);
            jsonObject.putOpt("fromUserId", userId);
            jsonObject.putOpt("fromServerId", serverId);
        } catch (JSONException e) {
//            Log.e(TAG)
            return "";
        }
        return jsonObject.toString();
    }

    /**
     * 保存已发送的好友id
     * @param ids 好友id列表
     */
    private void saveInvitedFriendIds(List<String> ids) {
        if(null != mSharedPrefHelper) {
            if(null == ids || ids.isEmpty()) {
                return;
            }
            long currentTimeMillis = System.currentTimeMillis();
            Map<String, Long> datas = new HashMap<>();
            for (String id :ids) {
                datas.put(id, currentTimeMillis);
            }
            mSharedPrefHelper.saveLongMap(datas);
        }
    }
}
