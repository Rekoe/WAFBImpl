package com.wa.sdk.fb.social;

import android.app.Activity;
import android.content.Intent;

import com.facebook.GraphResponse;
import com.facebook.internal.CallbackManagerImpl;
import com.facebook.share.widget.GameRequestDialog;
import com.wa.sdk.common.WASharedPrefHelper;
import com.wa.sdk.common.model.WACallback;
import com.wa.sdk.common.model.WACallbackManagerImpl;
import com.wa.sdk.fb.WAFacebook;
import com.wa.sdk.fb.social.model.WAFBPaging;
import com.wa.sdk.social.model.WAFBApplication;
import com.wa.sdk.social.model.WAFBGraphObject;
import com.wa.sdk.user.model.WALoginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Facebook Game Service Request
 * Created by yinglovezhuzhu@gmail.com on 2016/3/23.
 */
public abstract class WAFBGameService extends WAFacebook {

    public static final String FB_ACTION_TYPE_INVITE = "invite";

    protected static final String FB_RECENT_INVITE_FRIENDS = "fb_recent_invite_friends";

    protected static final String FB_PARAMETER_FIELDS = "fields";

    protected static final String FB_PARAMETER_EXCLUDED_IDS = "excluded_ids";

    protected static final String FB_PARAMETER_LIMIT = "limit";

    protected static final String FB_PARAMETER_BEFORE = "before";

    protected static final String FB_PARAMETER_AFTER = "after";

    protected GameRequestDialog mRequestDialog;

    protected static WASharedPrefHelper mSharedPrefHelper;

    protected Activity mActivity;

    protected PendingAction mPendingAction = PendingAction.NONE;

    /**
     * 登陆回调
     */
    protected final WACallback<WALoginResult> mLoginCallback = new WACallback<WALoginResult>() {
        @Override
        public void onSuccess(int code, String message, WALoginResult result) {
            onLoginFBSuccess(code, message, result);
        }

        @Override
        public void onCancel() {
            onLoginFBCanceled();
        }

        @Override
        public void onError(int code, String message, WALoginResult result, Throwable throwable) {
            onLoginFBError(code, message, result, throwable);
        }
    };

    public WAFBGameService() {
        registerCallbackImpl(mCallbackManager,
                CallbackManagerImpl.RequestCodeOffset.GameRequest.toRequestCode(),
                new WACallbackManagerImpl.Callback() {
                    @Override
                    public boolean onActivityResult(int resultCode, Intent data) {
                        mFBCallbackManager.onActivityResult(CallbackManagerImpl.RequestCodeOffset.GameRequest.toRequestCode(),
                                resultCode, data);
                        return false;
                    }
                });
    }


    /**
     * 登录Facebook成功
     * @param code
     * @param message
     * @param result
     */
    protected abstract void onLoginFBSuccess(int code, String message, WALoginResult result);

    /**
     * 登录Facebook取消
     */
    protected abstract void onLoginFBCanceled();

    /**
     * 登录Facebook错误
     * @param code
     * @param message
     * @param result
     * @param throwable
     */
    protected abstract void onLoginFBError(int code, String message, WALoginResult result, Throwable throwable);

    /**
     * 获取请求响应码
     *
     * @param response
     * @return
     */
    protected int getResponseCode(GraphResponse response) {
        if (null == response) {
            return -1;
        }
        final HttpURLConnection connection = response.getConnection();
        int responseCode = -1;
        try {
            responseCode = (connection != null) ? connection.getResponseCode() : 200;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return responseCode;
    }

    /**
     * 解析分页数据
     * @param jsonObject 分页JSON数据
     * @return 分页数据信息
     */
    protected WAFBPaging parsePagingData(JSONObject jsonObject) {
        WAFBPaging paging = new WAFBPaging();
        if(null != jsonObject) {
            JSONObject cursorsObject = jsonObject.optJSONObject("cursors");
            if(null != cursorsObject) {
                paging.setBefore(cursorsObject.optString("before"));
                paging.setAfter(cursorsObject.optString("after"));
            }
            paging.setPrevious(jsonObject.optString("previous"));
            paging.setNext(jsonObject.optString("next"));
        }
        return paging;
    }

    /**
     * 解析OpenGraphObject数据
     * @param jsonObject Facebook Object JSON数据
     * @return Facebook Object数据信息
     */
    protected WAFBGraphObject parseObjectData(JSONObject jsonObject) {
        if(null == jsonObject) {
            return null;
        }
        WAFBGraphObject object = new WAFBGraphObject();
        object.setId(jsonObject.optString("id"));
        object.setTitle(jsonObject.optString("title"));
        object.setIsScraped(jsonObject.optBoolean("is_scraped"));
        object.setCreatedTime(jsonObject.optString("created_time"));

        JSONArray imageArray = jsonObject.optJSONArray("image");
        if(null != imageArray && imageArray.length() > 0) {
            try {
                JSONObject imageObject = imageArray.getJSONObject(0);
                if(null != imageObject) {
                    object.setImageUrl(imageObject.optString("url"));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        object.setDescription(jsonObject.optString("description"));

        JSONObject dataObject = jsonObject.optJSONObject("data");
        if(null != dataObject) {
            Iterator<String> dataKeys = dataObject.keys();
            Map<String, Object> dataMap = new HashMap<>();
            String key;
            while(dataKeys.hasNext()) {
                key = dataKeys.next();
                dataMap.put(key, dataObject.opt(key));
            }
            object.setData(dataMap);
        }
        return object;
    }


    /**
     * 解析应用数据
     * @param jsonObject Facebook Application JSON数据
     * @return Facebook Application 数据信息
     */
    protected WAFBApplication parseApplicationData(JSONObject jsonObject) {
        if(null == jsonObject) {
            return null;
        }
        WAFBApplication application = new WAFBApplication();
        application.setId(jsonObject.optString("id"));
        application.setName(jsonObject.optString("category"));
        application.setCategory(jsonObject.optString("link"));
        application.setNamespace(jsonObject.optString("namespace"));
        application.setLink(jsonObject.optString("name"));
        return application;
    }


//    public static boolean onActivityResult(int requestCode, int resultCode, Intent data) {
//        return mCallbackManager.onActivityResult(requestCode, resultCode, data);
//    }


    /**
     * 挂起操作类型
     */
    protected enum PendingAction {
        NONE,
        QUERY_INVITABLE_FRIENDS,
        QUERY_FRIENDS,
        QUERY_REQUEST,
        DELETE_REQUEST,
        //        QUERY_OBJECTS, // 查询Object不需要任何的授权，使用Facebook 的APP Token即可
        INVITE,
        GIFTING,
    }
}
