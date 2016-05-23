package com.wa.sdk.fb.social.invite;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;

import com.facebook.AccessToken;
import com.wa.sdk.common.WAConfig;
import com.wa.sdk.common.WASharedPrefHelper;
import com.wa.sdk.common.http.HttpRequest;
import com.wa.sdk.common.http.HttpResult;
import com.wa.sdk.common.http.HttpStatus;
import com.wa.sdk.common.model.WACallback;
import com.wa.sdk.common.model.WAResult;
import com.wa.sdk.common.utils.LogUtil;
import com.wa.sdk.common.utils.StringUtil;
import com.wa.sdk.common.utils.WAUtil;
import com.wa.sdk.core.WASdkProperties;
import com.wa.sdk.fb.WAFBConstants;
import com.wa.sdk.fb.WAFBVersion;
import com.wa.sdk.fb.WAFacebook;
import com.wa.sdk.fb.core.WAFBCore;
import com.wa.sdk.fb.social.WAFBGameRequest;
import com.wa.sdk.fb.social.WAFBGameService;
import com.wa.sdk.social.model.WAFBGameRequestData;
import com.wa.sdk.social.model.WAFBGameRequestResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Facebook 邀请奖励机制
 * Created by yinglovezhuzhu@gmail.com on 2016/3/23.
 */
public class WAFBInviteReward extends WAFacebook {


    private static final String SP_FB_REWARD_CACHE = "wa_sdk_fb_reward_cache";

    private static final String SP_KEY_REWARD_STATUS = "wa_invite_reward_already";

    private Context mContext;

    private WASharedPrefHelper mSharedPrefHelper;

    private RewardHandler mHandler = new RewardHandler();

    private boolean mInitialized = false;

    private static WAFBInviteReward mInstance = null;

    public static WAFBInviteReward getInstance() {
        synchronized (WAFBInviteReward.class) {
            if(null == mInstance) {
                mInstance = new WAFBInviteReward();
            }
            return mInstance;
        }
    }

    private WAFBInviteReward() {

    }

    public void initialize(Context context) {
        if(mInitialized) {
            return;
        }
        mContext = context.getApplicationContext();
        mSharedPrefHelper = WASharedPrefHelper.newInstance(mContext, SP_FB_REWARD_CACHE);
        mInitialized = true;

        syncInitRetryAll();
    }

    /**
     * 向SDK服务器创建一条邀请记录，当邀请成功的时候调用这个接口，通知SDK服务器(异步执行)
     * @param context 上下文
     * @param requestId 邀请请求id
     * @param recipients 接收者id列表
     * @param callback 回调
     */
    public void createInviteRecord(final Context context, final String requestId,
                                   final Collection<String> recipients,
                                   final WACallback<WAResult> callback) {
        checkAccount(null, new WACallback<WAResult>() {
            @Override
            public void onSuccess(int code, String message, WAResult result) {
                if (!mInitialized) {
                    initialize(context);
                }
                FBCreateInvitedTask task = new FBCreateInvitedTask(requestId,
                        list2String(recipients), 0L, callback);
                task.execute();
            }

            @Override
            public void onCancel() {
                if (null != callback) {
                    callback.onCancel();
                }
            }

            @Override
            public void onError(int code, String message, WAResult result, Throwable throwable) {
                if (null != callback) {
                    callback.onError(code, message, result, throwable);
                }
            }
        });
    }

    /**
     * 邀请奖励上报，当用户第一次安装使用的时候(异步执行)
     * @param activity Activity对象
     * @param callback 回调
     */
    public void inviteInstallReward(final Activity activity, final WACallback<WAResult> callback) {
        checkAccount(null, new WACallback<WAResult>() {
            @Override
            public void onSuccess(int code, String message, WAResult result) {
                if (WASdkProperties.getInstance().isOnServer()) {
                    handleInviteInstall(activity, callback);
                } else {
                    if (null != callback) {
                        callback.onError(WACallback.CODE_SERVER_ID_NOT_FOUND,
                                "Not enter a server yet", null, null);
                    }
                }
            }

            @Override
            public void onCancel() {
                if (null != callback) {
                    callback.onCancel();
                }
            }

            @Override
            public void onError(int code, String message, WAResult result, Throwable throwable) {
                if (null != callback) {
                    callback.onError(code, message, result, throwable);
                }
            }
        });
    }

    /**
     * 被邀请者达到某个特定的事件后，通知服务器奖励邀请者（比如达到某个等级后）(异步执行)
     * @param activity
     * @param eventName
     * @param callback
     */
    public void inviteEventReward(Activity activity, String eventName,
                                  WACallback<WAResult> callback) {

        if(!WASdkProperties.getInstance().isLogin()) {
            if(null != callback) {
                callback.onError(WACallback.CODE_NOT_LOGIN, "Not login yet!", null, null);
            }
            return;
        }

        if(!WASdkProperties.getInstance().isOnServer()) {
            if(null != callback) {
                callback.onError(WACallback.CODE_SERVER_ID_NOT_FOUND,
                        "Not enter a server yet", null, null);
            }
            return;
        }

        initialize(activity);
        FBInviteEventRewardTask task = new FBInviteEventRewardTask(callback);
        task.execute(eventName);
    }

    /**
     * 重试所有缓存中的失败记录，这个方法只在初始化的时候调用
     */
    private void syncInitRetryAll() {
        mHandler.post(new Runnable() {
            @SuppressWarnings("unchecked")
            @Override
            public void run() {
                Map<String, String> cacheData = (Map<String, String>) mSharedPrefHelper.getAll();
                if (cacheData.isEmpty()) {
                    return;
                }
                Set<String> requestIds = cacheData.keySet();
                for (String requestId : requestIds) {
                    if (!mHandler.hasMessages(RewardHandler.MSG_RETRY_REPORT, requestId)) {
                        retry(requestId, cacheData.get(requestId));
                    }
                }
            }
        });
    }

    /**
     * 生成提交Facebook邀请信息接口的数据缓存JSON
     * @param userId 用户id
     * @param serverId 服务器id
     * @param requestId 请求id
     * @param inviteeIds 被邀请者id
     * @param duration 失败循环延时
     * @return 包含入参在内的一个JSON字符串
     */
    private String createRewardCacheJsonStr(String userId, String serverId, String requestId,
                                            String inviteeIds, long duration) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.putOpt("userId", userId);
            jsonObject.putOpt("serverId", serverId);
            jsonObject.putOpt("requestId", requestId);
            jsonObject.putOpt("inviteeIds", inviteeIds);
            jsonObject.putOpt("duration", duration);
        } catch (JSONException e) {
            LogUtil.e(WAFBConstants.TAG, "WAFBInviteReward--" + LogUtil.getStackTrace(e));
            return null;
        }
        return jsonObject.toString();
    }


    /**
     * 获取循环延时
     * @param lastDuration 上一次循环的延时
     * @return 根据上一次循环延时生成的计算循环时间
     */
    private long getDuration(long lastDuration) {
        if(lastDuration < 60000L) {
            return 60000L; // 小于1分钟的时候，返回1分钟
        } else if(lastDuration < 300000L) {
            return 300000L; // 小于5分钟，返回5分钟
        } else if(lastDuration < 1800000L) {
            return lastDuration + 300000L; // 小于30分钟，每次在之前时长加5分钟
        }
        return 1800000L; // 其他，返回30分钟
    }

    /**
     * 失败重试
     * @param data 缓存数据
     */
    private void retry(String requestId, String data) {
        if(StringUtil.isEmpty(data)) {
            return;
        }
        try {
            JSONObject jsonObject = new JSONObject(data);
            String inviteeIds = jsonObject.optString("inviteeIds");
            long duration = jsonObject.optLong("duration");
            FBCreateInvitedTask task = new FBCreateInvitedTask(requestId, inviteeIds, duration, null);
            task.execute();
        } catch (JSONException e) {
            LogUtil.e(WAFBConstants.TAG, "WAFBInviteReward--" + LogUtil.getStackTrace(e));
            mSharedPrefHelper.remove(requestId);
        }
    }

    /**
     * 将String的列表转换为字符窜，多个项之间用半角逗号隔开
     * @param list
     * @return
     */
    private String list2String(Collection<String> list) {
        if(null == list || list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String string : list) {
            sb.append(string).append(",");
        }
        if(sb.length() > 0) {
            sb.deleteCharAt(sb.lastIndexOf(","));
        }
        return sb.toString();
    }

    /**
     * 处理（被）邀请安装事件
     * @param activity
     * @param callback
     */
    private void handleInviteInstall(Activity activity, final WACallback<WAResult> callback) {
        final WASharedPrefHelper loginSharedPref = WASharedPrefHelper.newInstance(activity,
                WAConfig.SHARE_PRE_LOGIN_CONFIG);
        final WASharedPrefHelper sdkConfigSharedPref = WASharedPrefHelper.newInstance(activity,
                WAConfig.SHARE_PRE_CONFIG);
        final AccessToken accessToken = AccessToken.getCurrentAccessToken();
        final String statusSpKey = SP_KEY_REWARD_STATUS + "_" + accessToken.getUserId();
        if(sdkConfigSharedPref.getBoolean(statusSpKey, false)) {
            LogUtil.i(WAFBConstants.TAG, "WAFBInviteReward--Game install invite reward has been sent to server.");
            if(null != callback) {
                callback.onSuccess(WACallback.CODE_SUCCESS,
                        "Game install invite reward has been sent to server.", null);
            }
            return;
        }

        initialize(activity);

        WAFBGameRequest.newInstance().queryRequests(activity, "",
                new WACallback<WAFBGameRequestResult>() {
            @Override
            public void onSuccess(int code, String message, WAFBGameRequestResult result) {
                List<WAFBGameRequestData> requests = result.getRequests();
                if(null == requests || requests.isEmpty()) {
                    if(null != callback) {
                        WAResult WAResult = new WAResult(WACallback.CODE_SUCCESS,
                                "Nobody invite you!");
                        callback.onSuccess(WACallback.CODE_SUCCESS,
                                "Nobody invite you!", WAResult);
                    }
                    return;
                }

                List<String> requestIds = new ArrayList<String>();
                JSONObject jsonObject;
                for (WAFBGameRequestData requestData : requests) {
                    if(StringUtil.isEmpty(requestData.getData())) {
                        continue;
                    }
                    try {
                        jsonObject = new JSONObject(requestData.getData());
                        if(WAFBGameService.FB_ACTION_TYPE_INVITE.equals(jsonObject.optString("actionType"))) {
                            requestIds.add(requestData.getId());
                        }
                    } catch (JSONException e) {
                        // do nothing
                    }
                }

                if(requestIds.size() < 1) {
                    // 如果是没有人邀请，那么将配置缓存中的奖励标识设置为true，以后调用这个方法不会再进行安装奖励通知
                    sdkConfigSharedPref.saveBoolean(statusSpKey, true);
                    if(null != callback) {
                        WAResult WAResult = new WAResult(WACallback.CODE_SUCCESS, "Nobody invite you!");
                        callback.onSuccess(WACallback.CODE_SUCCESS, "Nobody invite you!", WAResult);
                    }
                    return;
                }

                FBInviteRewardTask task = new FBInviteRewardTask(requestIds, callback);
                task.execute(loginSharedPref.getString(WAConfig.SP_KEY_LOGIN_PLATFORM_TOKEN, ""));
            }

            @Override
            public void onCancel() {
                if(null != callback) {
                    callback.onCancel();
                }
            }

            @Override
            public void onError(int code, String message, WAFBGameRequestResult result, Throwable throwable) {
                if(null != callback) {
                    callback.onError(code, message, null, throwable);
                }
            }
        });
    }


    @SuppressLint("HandlerLeak")
    private class RewardHandler extends Handler {

        public static final int MSG_RETRY_REPORT = 0x01;

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MSG_RETRY_REPORT:
                    String requestId = (String)msg.obj;
                    String data = mSharedPrefHelper.getString(requestId, "");
                    retry(requestId, data);
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Facebook邀请好友之后通知服务器的请求<br/>
     *
     */
    private class FBCreateInvitedTask extends AsyncTask<String, Integer, WAResult> {

        private String mmUserId;
        private String mmGameUserId;
        private String mmServerId;
        private String mmRequestId;
        private String mmInviteeIds;
        private long mmDuration;
        private WACallback<WAResult> mmCallback;

        public FBCreateInvitedTask(String requestId, String inviteeIds,
                                   long duration, WACallback<WAResult> callback) {
            this.mmUserId = WASdkProperties.getInstance().getUserId();
            this.mmGameUserId = WASdkProperties.getInstance().getGameUserId();
            this.mmServerId = WASdkProperties.getInstance().getServerId();
            this.mmRequestId = requestId;
            this.mmInviteeIds = inviteeIds;
            this.mmDuration = duration;
            this.mmCallback = callback;
        }

        @Override
        protected WAResult doInBackground(String... params) {
            WAResult result = new WAResult();
            StringBuilder signStringBuilder = new StringBuilder();
            signStringBuilder.append(WASdkProperties.getInstance().getSdkAppId())
                    .append(WASdkProperties.getInstance().getSdkAppKey())
                    .append(WAFBVersion.SDK_VER)
                    .append(WASdkProperties.getInstance().getClientId())
                    .append(mmUserId)
                    .append(mmServerId)
                    .append(mmGameUserId)
                    .append(mmRequestId)
                    .append(mmInviteeIds);

//            Md5(
//                    appId+
//                    appKey+
//                    sdkVer+
//                    clientId +
//                    userId +
//                    serverId +
//                    requestId+
//                    inviteeIds)

            LogUtil.d(WAFBConstants.TAG, "WAFBInviteReward----sign string:"
                    + signStringBuilder.toString());
            String osign = null;
            try {
                osign = WAUtil.getMD5Hex(signStringBuilder.toString());
            } catch (NoSuchAlgorithmException e) {
                LogUtil.e(WAFBConstants.TAG, "WAFBInviteReward----Get sign string failed: "
                        + LogUtil.getStackTrace(e));
            }
            LogUtil.d(WAFBConstants.TAG, "WAFBInviteReward----sign:" + osign);

            final SortedMap<String, Object> requestParams = new TreeMap<>();
            requestParams.put("appId", WASdkProperties.getInstance().getSdkAppId());
            requestParams.put("clientId", WASdkProperties.getInstance().getClientId());
            requestParams.put("os", WASdkProperties.getInstance().getOS());
            requestParams.put("sdkVer", WAFBVersion.SDK_VER);
            requestParams.put("userId", mmUserId);
            requestParams.put("serverId", mmServerId);
            requestParams.put("gameUserId", mmGameUserId);
            requestParams.put("requestId", mmRequestId);
            requestParams.put("inviteeIds", mmInviteeIds);
            requestParams.put("osign", osign);

//            sdkVer	String(10)	Y	Sdk版本号
//            os	String(10)	Y	手机系统：androidios
//            clientId	String	Y	手机特征号，唯一
//            appId	String	Y	Sdk平台分配的应用id
//            userId	Long	N	邀请人用户GHW 用户Id
//            serverId	String	N	邀请人服务区Id
//            requestId	String	Y	Facebook请求Id
//            inviteeIds	String	Y	被邀请人用户Facebook帐号Id,多个用逗号分割
//            osign	String	Y	验证用的加密串
            try {
                HttpResult<String> httpResult = HttpRequest.httpPostRequest(WAFBConstants.TAG,
                        WASdkProperties.getInstance().getSdkRequestBaseUrl()
                                + WAFBConstants.URL_CREATE_INVITE, requestParams);
                String responseData = httpResult.getResponseData();
                LogUtil.d(WAFBConstants.TAG, "WAFBInviteReward--Facebook invite reward response:" + responseData);
                if (HttpStatus.SC_OK == httpResult.getResponseCode()) {
                    JSONObject jsonObject = new JSONObject(responseData);
                    int code = jsonObject.optInt("code");
                    String message = jsonObject.optString("msg");
                    if (200 == code) {
                        result.setCode(WACallback.CODE_SUCCESS);
                        result.setMessage(null == message ? "Facebook invite reward http request success" : message);
                    } else {
                        result.setCode(WACallback.CODE_ERROR);
                        result.setMessage(null == message ? "Facebook invite reward http request error" : message);
                    }
                } else {
                    JSONObject jsonObject = new JSONObject(responseData);
                    int code = jsonObject.optInt("code");
                    String message = jsonObject.optString("msg");
                    result.setCode(WACallback.CODE_ERROR);
                    result.setMessage(null == message ? ("Facebook invite reward http request error: " + code) : message);
                }
            } catch (IOException|JSONException e) {
                result.setCode(WACallback.CODE_ERROR);
                result.setMessage(e.toString());
                LogUtil.e(WAFBConstants.TAG, "WAFBInviteReward--Facebook invite reward error: " + LogUtil.getStackTrace(e));
            }
            return result;
        }

        @Override
        protected void onPostExecute(WAResult result) {
            super.onPostExecute(result);
            if(isCancelled()) {
                return;
            }
            switch (result.getCode()) {
                case WACallback.CODE_SUCCESS:
                   if(mSharedPrefHelper.contains(mmRequestId)) {
                        mSharedPrefHelper.remove(mmRequestId);
                    }
                    LogUtil.i(WAFBConstants.TAG, "WAFBInviteReward--Create invite request success("
                            + WAFBConstants.URL_CREATE_INVITE + "): " + result.getCode());
                    if(null != mmCallback) {
                        mmCallback.onSuccess(WACallback.CODE_SUCCESS, result.getMessage(), result);
                    }
                    break;
                case WACallback.CODE_ERROR:
                    saveCacheAndRetry(mmUserId, mmServerId, mmRequestId, mmInviteeIds, mmDuration);
                    LogUtil.i(WAFBConstants.TAG, "WAFBInviteReward--Create invite request error("
                            + WAFBConstants.URL_CREATE_INVITE + "): " + result.getCode());
                    if(null != mmCallback) {
                        mmCallback.onError(WACallback.CODE_ERROR, result.getMessage(), result, null);
                    }
                    break;
                default:
                    saveCacheAndRetry(mmUserId, mmServerId, mmRequestId, mmInviteeIds, mmDuration);
                    LogUtil.i(WAFBConstants.TAG, "WAFBInviteReward--Create invite request error("
                            + WAFBConstants.URL_CREATE_INVITE + "): " + result.getCode());
                    if(null != mmCallback) {
                        mmCallback.onError(WACallback.CODE_ERROR, result.getMessage(), result, null);
                    }
                    break;
            }
        }

        public boolean cancel() {
            saveCacheAndRetry(mmUserId, mmServerId, mmRequestId, mmInviteeIds, mmDuration);
            if(null != mmCallback) {
                mmCallback.onCancel();
            }
            return this.cancel(true);
        }

        // 保存失败的缓存，并且重试
        private void saveCacheAndRetry(String userId, String serverId, String requestId,
                                     String inviteeIds, long lastDuration) {
            long duration = getDuration(lastDuration);
            String data = createRewardCacheJsonStr(userId, serverId, requestId, inviteeIds, duration);
            if(!StringUtil.isEmpty(data)) {
                mSharedPrefHelper.saveString(requestId, data);
            }
            mHandler.sendMessageDelayed(mHandler.obtainMessage(RewardHandler.MSG_RETRY_REPORT, requestId), duration);
        }
    }

    /**
     * Facebook邀请奖励机制的第一次奖励通知异步线程类<br/>
     *
     * 登陆网络请求异步线程类,登录传入的参数顺序为accessToken, fbInviteIds(list,用半角逗号隔开)
     */
    private class FBInviteRewardTask extends AsyncTask<String, Integer, WAResult> {

        private WACallback<WAResult> mmCallback;
        private List<String> mmRequestIds = new ArrayList<>();

        public FBInviteRewardTask(Collection<String> requestIds, WACallback<WAResult> callback) {
            this.mmCallback = callback;
            mmRequestIds.addAll(requestIds);
        }

        @Override
        protected WAResult doInBackground(String... params) {
            WAResult result = new WAResult();
            if(null == params || params.length < 1) {
                result.setCode(WACallback.CODE_ERROR);
                result.setMessage("Parameters error");
                LogUtil.e(WAFBConstants.TAG, "WAFBInviteReward--Parameters error");
                return result;
            }
            String userId = WASdkProperties.getInstance().getUserId();
            String serverId = WASdkProperties.getInstance().getServerId();
            String inviteeGameUserId = WASdkProperties.getInstance().getGameUserId();
            if(StringUtil.isEmpty(serverId) || WASdkProperties.VALUE_UNKNOWN.equals(serverId)) {
                serverId = "0";
            }
            String accessToken = params[0];
            String fbInviteIds = list2String(mmRequestIds);
            StringBuilder signStringBuilder = new StringBuilder();
            signStringBuilder.append(WASdkProperties.getInstance().getSdkAppId())
                    .append(WASdkProperties.getInstance().getSdkAppKey())
                    .append(WAFBVersion.SDK_VER)
                    .append(WASdkProperties.getInstance().getClientId())
                    .append(userId)
                    .append(serverId)
                    .append(inviteeGameUserId)
                    .append(accessToken)
                    .append(fbInviteIds);

//            Md5(
//                    appId+
//                    appKey+
//                    sdkVer+
//                    clientId +
//                    userId+
//                    serverId+
//                    accessToken+
//                    fbIinviteIds)

            LogUtil.d(WAFBConstants.TAG, "WAFBInviteReward--sign string:" + signStringBuilder.toString());
            String osign = null;
            try {
                osign = WAUtil.getMD5Hex(signStringBuilder.toString());
            } catch (NoSuchAlgorithmException e) {
                LogUtil.e(WAFBConstants.TAG, "WAFBInviteReward--Get sign string failed: "
                        + LogUtil.getStackTrace(e));
            }
            LogUtil.d(WAFBConstants.TAG, "WAFBInviteReward--sign:" + osign);

            final SortedMap<String, Object> requestParams = new TreeMap<>();
            requestParams.put("appId", WASdkProperties.getInstance().getSdkAppId());
            requestParams.put("clientId", WASdkProperties.getInstance().getClientId());
            requestParams.put("os", WASdkProperties.getInstance().getOS());
            requestParams.put("sdkVer", WAFBVersion.SDK_VER);
            requestParams.put("inviteeUserId", userId);
            requestParams.put("inviteeGameUserId", inviteeGameUserId);
            requestParams.put("inviteeServerId", serverId);
            requestParams.put("accessToken", accessToken);
            requestParams.put("fbInviteIds", fbInviteIds);
            requestParams.put("osign", osign);


//            sdkVer	String(10)	Y	Sdk版本号
//            os	String(10)	Y	手机系统：androidios
//            clientId	String	Y	手机特征号，唯一
//            appId	String	Y	Sdk平台分配的应用id
//            userId	Long	Y	被邀请人用户GHW用户Id
//            serverId	String	Y	被邀请人服务区Id
//            accessToken	String	Y	被邀请人FB accessToken
//            fbInviteIds	String	Y	Fb邀请Id,多个用逗号“,”分割
//            osign	String	Y	验证用的加密串

            try {
                HttpResult<String> httpResult = HttpRequest.httpPostRequest(WAFBConstants.TAG,
                        WASdkProperties.getInstance().getSdkRequestBaseUrl()
                                + WAFBConstants.URL_INVITE_REWARD, requestParams);
                String responseData = httpResult.getResponseData();
                LogUtil.d(WAFBConstants.TAG, "WAFBInviteReward--Facebook invite reward response:" + responseData);
                if (HttpStatus.SC_OK == httpResult.getResponseCode()) {
                    JSONObject jsonObject = new JSONObject(responseData);
                    int code = jsonObject.optInt("code");
                    String message = jsonObject.optString("msg");
                    if (200 == code) {
                        result.setCode(WACallback.CODE_SUCCESS);
                        result.setMessage(null == message ? "Facebook invite reward http request success" : message);
                    } else {
                        result.setCode(WACallback.CODE_ERROR);
                        result.setMessage(null == message ? "Facebook invite reward http request error" : message);
                    }
                } else {
                    JSONObject jsonObject = new JSONObject(responseData);
                    int code = jsonObject.optInt("code");
                    String message = jsonObject.optString("msg");
                    result.setCode(WACallback.CODE_ERROR);
                    result.setMessage(null == message ? ("Facebook invite reward http request error: "
                            + code) : message);
                }
            } catch (IOException|JSONException e) {
                result.setCode(WACallback.CODE_ERROR);
                result.setMessage(e.toString());
                LogUtil.e(WAFBConstants.TAG, "WAFBInviteReward--Facebook invite reward error: "
                        + LogUtil.getStackTrace(e));
            }
            return result;
        }

        @Override
        protected void onPostExecute(WAResult result) {
            super.onPostExecute(result);
            if(isCancelled()) {
                return;
            }
            switch (result.getCode()) {
                case WACallback.CODE_SUCCESS:
                    if(null != mmCallback) {
                        mmCallback.onSuccess(WACallback.CODE_SUCCESS, result.getMessage(), result);
                    }
                    WASharedPrefHelper sharedPrefHelper = WASharedPrefHelper.newInstance(mContext, WAConfig.SHARE_PRE_CONFIG);
                    AccessToken accessToken = AccessToken.getCurrentAccessToken();
                    String statusSpKey = SP_KEY_REWARD_STATUS + (null == accessToken ? "" : ("_" + accessToken.getUserId()));
                    sharedPrefHelper.saveBoolean(statusSpKey, true);
                    break;
                case WACallback.CODE_ERROR:
                    if(null != mmCallback) {
                        mmCallback.onError(WACallback.CODE_ERROR, result.getMessage(), result, null);
                    }
                    break;
                default:
                    if(null != mmCallback) {
                        mmCallback.onError(WACallback.CODE_ERROR, result.getMessage(), result, null);
                    }
                    break;
            }
        }

        public boolean cancel() {
            if(null != mmCallback) {
                mmCallback.onCancel();
            }
            return this.cancel(true);
        }
    }

    /**
     * Facebook邀请奖励机制异步线程类，这个异步线程是根据某个事件进行通知的接口<br/>
     *
     * 登陆网络请求异步线程类,登录传入的参数顺序为 userId，serverId，fbInviteId
     */
    private class FBInviteEventRewardTask extends AsyncTask<String, Integer, WAResult> {

        private WACallback<WAResult> mmCallback;

        public FBInviteEventRewardTask(WACallback<WAResult> callback) {
            this.mmCallback = callback;
        }

        @Override
        protected WAResult doInBackground(String... params) {
            WAResult result = new WAResult();
            if(null == params || params.length < 1) {
                result.setCode(WACallback.CODE_ERROR);
                result.setMessage("Parameters error");
                LogUtil.e(WAFBConstants.TAG, "WAFBInviteReward--Parameters error");
                return result;
            }
            String userId = WASdkProperties.getInstance().getUserId();
            String inviteeGameUserId = WASdkProperties.getInstance().getGameUserId();//Zping on 20151106
            String serverId = WASdkProperties.getInstance().getServerId();
            String eventName = params[0];
            StringBuilder signStringBuilder = new StringBuilder();
            signStringBuilder.append(WASdkProperties.getInstance().getSdkAppId())
                    .append(WASdkProperties.getInstance().getSdkAppKey())
                    .append(WAFBVersion.SDK_VER)
                    .append(WASdkProperties.getInstance().getClientId())
                    .append(userId)
                    .append(serverId)
                    .append(inviteeGameUserId)
                    .append(eventName);

//            Md5(
//                appId+
//                appKey+
//                sdkVer+
//                clientId +
//                userId+
//                serverId+
//                enventName
//            )


            LogUtil.d(WAFBConstants.TAG, "WAFBInviteReward--sign string:" + signStringBuilder.toString());
            String osign = null;
            try {
                osign = WAUtil.getMD5Hex(signStringBuilder.toString());
            } catch (NoSuchAlgorithmException e) {
                LogUtil.e(WAFBConstants.TAG, "WAFBInviteReward--Get sign string failed: " + LogUtil.getStackTrace(e));
            }
            LogUtil.d(WAFBConstants.TAG, "WAFBInviteReward--sign:" + osign);

            final SortedMap<String, Object> requestParams = new TreeMap<>();
            requestParams.put("appId", WASdkProperties.getInstance().getSdkAppId());
            requestParams.put("clientId", WASdkProperties.getInstance().getClientId());
            requestParams.put("os", WASdkProperties.getInstance().getOS());
            requestParams.put("sdkVer", WAFBVersion.SDK_VER);
            requestParams.put("inviteeUserId", userId);
            requestParams.put("inviteeServerId", serverId);
            requestParams.put("inviteeGameUserId", inviteeGameUserId);
            requestParams.put("eventName", eventName);
            requestParams.put("osign", osign);

//            sdkVer	String	Y	Sdk版本号
//            os	String	Y	手机系统：androidios
//            clientId	String	Y	手机特征号，唯一
//            appId	String	Y	Sdk平台分配的应用id
//            userId	Long	Y	用户Id
//            serverId	String	Y	服务区Id
//            enventName	String	Y	奖励事件名
//            osign	String	Y	验证用的加密串

            try {
                HttpResult<String> httpResult = HttpRequest.httpPostRequest(WAFBConstants.TAG,
                        WASdkProperties.getInstance().getSdkRequestBaseUrl()
                                + WAFBConstants.URL_INVITE_EVENT_REWARD, requestParams);
                String responseData = httpResult.getResponseData();
                LogUtil.d(WAFBConstants.TAG, "WAFBInviteReward--Facebook invite event reward response:" + responseData);
                if (HttpStatus.SC_OK == httpResult.getResponseCode()) {
                    JSONObject jsonObject = new JSONObject(responseData);
                    int code = jsonObject.optInt("code");
                    String message = jsonObject.optString("msg");
                    if (200 == code) {
                        result.setCode(WACallback.CODE_SUCCESS);
                        result.setMessage(null == message ? "Facebook invite event reward http request success" : message);
                    } else {
                        result.setCode(WACallback.CODE_ERROR);
                        result.setMessage(null == message ? "Facebook invite event reward http request error" : message);
                    }
                } else {
                    JSONObject jsonObject = new JSONObject(responseData);
                    int code = jsonObject.optInt("code");
                    String message = jsonObject.optString("msg");
                    result.setCode(WACallback.CODE_ERROR);
                    result.setMessage(null == message ? ("Facebook invite event reward http request error: "
                            + code) : message);
                }
            } catch (IOException|JSONException e) {
                result.setCode(WACallback.CODE_ERROR);
                result.setMessage(e.toString());
                LogUtil.e(WAFBConstants.TAG, "WAFBInviteReward--Facebook invite event reward error: "
                        + LogUtil.getStackTrace(e));
            }
            return result;
        }

        @Override
        protected void onPostExecute(WAResult result) {
            super.onPostExecute(result);
            if(isCancelled()) {
                return;
            }
            switch (result.getCode()) {
                case WACallback.CODE_SUCCESS:
                    if(null != mmCallback) {
                        mmCallback.onSuccess(WACallback.CODE_SUCCESS, result.getMessage(), result);
                    }
                    break;
                case WACallback.CODE_ERROR:
                    if(null != mmCallback) {
                        mmCallback.onError(WACallback.CODE_ERROR, result.getMessage(), result, null);
                    }
                    break;
                default:
                    if(null != mmCallback) {
                        mmCallback.onError(WACallback.CODE_ERROR, result.getMessage(), result, null);
                    }
                    break;
            }
        }

        public boolean cancel() {
            if(null != mmCallback) {
                mmCallback.onCancel();
            }
            return this.cancel(true);
        }
    }

}
