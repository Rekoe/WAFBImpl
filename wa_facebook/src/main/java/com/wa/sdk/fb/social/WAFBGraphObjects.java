package com.wa.sdk.fb.social;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;

import com.facebook.AccessToken;
import com.facebook.FacebookRequestError;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.HttpMethod;
import com.facebook.internal.ServerProtocol;
import com.wa.sdk.common.WAConfig;
import com.wa.sdk.common.WASharedPrefHelper;
import com.wa.sdk.common.http.HttpRequest;
import com.wa.sdk.common.http.HttpResult;
import com.wa.sdk.common.http.HttpStatus;
import com.wa.sdk.common.model.WACallback;
import com.wa.sdk.common.utils.LogUtil;
import com.wa.sdk.common.utils.StringUtil;
import com.wa.sdk.fb.WAFBConstants;
import com.wa.sdk.fb.social.model.WAFBObjectResult;
import com.wa.sdk.fb.social.model.WAFBPaging;
import com.wa.sdk.social.model.WAFBGraphObject;
import com.wa.sdk.social.model.WAFBGraphObjectResult;
import com.wa.sdk.user.model.WALoginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Facebook Graph Object
 * Created by yinglovezhuzhu@gmail.com on 2016/3/23.
 */
public class WAFBGraphObjects extends WAFBGameService {

    private static final String FB_GRAPH_PATH_ACCESS_TOKEN = "/oauth/access_token";

    private static final String FB_GRAPH_PATH_OBJECTS = "app/objects/";

    private QueryObjectTask mQueryObjectTask;
    private WACallback<WAFBGraphObjectResult> mQueryObjectsCallback;

    private String mObjectType;

    public static WAFBGraphObjects newInstance() {
        return new WAFBGraphObjects();
    }

    private WAFBGraphObjects() {
        super();
    }

    @Override
    protected void onLoginFBSuccess(int code, String message, WALoginResult result) {

    }

    @Override
    protected void onLoginFBCanceled() {

    }

    @Override
    protected void onLoginFBError(int code, String message, WALoginResult result, Throwable throwable) {

    }

    /**
     * 查询Facebook Objects列表（礼物列表）
     * @param objectType Objects 类型
     * @param callback 回调
     */
    public void queryFBObjects(Activity activity, String objectType, WACallback<WAFBGraphObjectResult> callback) {
        mObjectType = objectType;
        mQueryObjectsCallback = callback;
        mActivity = activity;
        mQueryObjectTask = new QueryObjectTask(activity);
        mQueryObjectTask.execute(objectType);
    }

    /**
     * 取消查询Object
     */
    @SuppressWarnings("unused")
    public void cancelQueryFBObjects() {
        if(null != mQueryObjectTask && !mQueryObjectTask.isCancelled()) {
            mQueryObjectTask.cancel(true);
        }
    }

    /**
     * 查询礼物列表（Objects）的异步线程类
     */
    private class QueryObjectTask extends AsyncTask<String, Integer, WAFBGraphObjectResult> {

        private Context mmContext;

        public QueryObjectTask(Context context) {
            this.mmContext = context;
        }

        @Override
        protected WAFBGraphObjectResult doInBackground(String... params) {
            WAFBGraphObjectResult result = new WAFBGraphObjectResult();
            if(null == params || params.length < 1 || StringUtil.isEmpty(params[0])) {
                // 参数错误
                result.setCode(WACallback.CODE_ERROR);
                result.setMessage("Parameter error：object type should not be empty");
                return result;
            }
            String objectType = params[0];
            WASharedPrefHelper sharedPrefHelper = WASharedPrefHelper.newInstance(mmContext, WAConfig.SHARE_PRE_CONFIG);

            String facebookAppToken = sharedPrefHelper.getString(WAConfig.SP_KEY_FB_APP_TOKEN, "");

            Resources resources = mmContext.getResources();
            String packageName = mmContext.getPackageName();
            int resId = resources.getIdentifier("facebook_app_id", "string", packageName);
            if(0 == resId) {
                result.setCode(WACallback.CODE_ERROR);
                result.setMessage("No facebook app id found in string.xml");
                return result;
            }

            String facebookAppId = resources.getString(resId);

            if(StringUtil.isEmpty(facebookAppToken)) {
                // 直接引用WA Sdk的加载Facebook secret key，这个方法需要在编译时以来WA Sdk
//                WAParameterResult parameterResult = WASdkOnlineParameter.getInstance().loadParameterAndWait();
//                String facebookSecret = parameterResult.getFbSecretKey();
//                if(WACallback.CODE_ERROR == parameterResult.getCode() || StringUtil.isEmpty(facebookSecret)) {
//                    // 获取facebook secret失败了
//                    result.setCode(WACallback.CODE_ERROR);
//                    result.setMessage("Load facebook secret key failed");
//                    return result;
//                }

                // 这里用反射的方法调用WA SDK的加载Facebook secret key
                String facebookSecret = null;
                try {
                    facebookSecret = loadFBSecretKeyAndWait();
                } catch (Exception e) {
                    LogUtil.e(WAFBConstants.TAG, "WAFBGraphObjects--Load facebook secret key " +
                            "function dependence of WA Sdk， you need integrate WA Sdk first\n"
                            + LogUtil.getStackTrace(e));
                    result.setCode(WACallback.CODE_ERROR);
                    result.setMessage("Load facebook secret key function dependence of WA Sdk, " +
                            "you need integrate WA Sdk first");
                    return result;
                }

                if(StringUtil.isEmpty(facebookSecret)) {
                    // 获取facebook secret失败了
                    result.setCode(WACallback.CODE_ERROR);
                    result.setMessage("Load facebook secret key failed");
                    return result;
                }

                try {
                    HttpResult<String> httpResult = HttpRequest.httpPostRequest(WAFBConstants.TAG,
                            ServerProtocol.getGraphUrlBase()
                            + "/" + ServerProtocol.GRAPH_API_VERSION + FB_GRAPH_PATH_ACCESS_TOKEN
                            + "?client_id=" + facebookAppId
                            + "&client_secret=" + facebookSecret
                            + "&grant_type=client_credentials", null);
                    if(HttpStatus.SC_OK == httpResult.getResponseCode()) {
                        try {
                            JSONObject jsonObject = new JSONObject(httpResult.getResponseData());
                            facebookAppToken = jsonObject.optString("access_token");
                            if(null == facebookAppToken || facebookAppToken.isEmpty()) {
                                // 获取appToken失败
                                result.setCode(WACallback.CODE_ERROR);
                                result.setMessage("Load facebook app token failed");
                                return result;
                            }
                            sharedPrefHelper.saveString(WAConfig.SP_KEY_FB_APP_TOKEN, facebookAppToken);
                        } catch (JSONException e) {
                            // 获取appToken失败
                            result.setCode(WACallback.CODE_ERROR);
                            result.setMessage("Load facebook app token failed: " + e.getMessage());
                            return result;
                        }
                    }
                } catch (IOException e) {
                    // 获取appToken失败
                    result.setCode(WACallback.CODE_ERROR);
                    result.setMessage("Load facebook app token failed: " + e.getMessage());
                    return result;
                }
            }

            GraphRequest request = new GraphRequest();
            request.setAccessToken(AccessToken.getCurrentAccessToken());
            request.setGraphPath(FB_GRAPH_PATH_OBJECTS + objectType);
            request.setHttpMethod(HttpMethod.GET);

            Bundle requestParameters = new Bundle();

            requestParameters.putString(FB_PARAMETER_FIELDS, "id,title,type,is_scraped,created_time,description,image{url},data");
            requestParameters.putInt(FB_PARAMETER_LIMIT, 1000);
            requestParameters.putString("access_token", facebookAppToken);
            request.setParameters(requestParameters);

            GraphResponse response = request.executeAndWait();

            WAFBObjectResult pageResult = parseResponseData(response);
            result.setCode(pageResult.getCode());
            result.setMessage(pageResult.getMessage());
            result.addObjects(pageResult.getObjects());

            WAFBPaging paging = pageResult.getPaging();
            while(null != paging && paging.hasNext()) {
                request = new GraphRequest();
                request.setAccessToken(AccessToken.getCurrentAccessToken());
                request.setGraphPath(FB_GRAPH_PATH_OBJECTS + objectType);
                request.setHttpMethod(HttpMethod.GET);
                requestParameters.putString(FB_PARAMETER_AFTER, paging.getAfter());
                request.setParameters(requestParameters);
                response = request.executeAndWait();
                pageResult = parseResponseData(response);
                result.addObjects(pageResult.getObjects());
                paging = pageResult.getPaging();
            }

            return result;
        }

        @Override
        protected void onPostExecute(WAFBGraphObjectResult result) {
            super.onPostExecute(result);
            if(isCancelled()) {
                return;
            }
            switch (result.getCode()) {
                case WACallback.CODE_SUCCESS:
                    if(null != mQueryObjectsCallback) {
                        mQueryObjectsCallback.onSuccess(WACallback.CODE_SUCCESS, result.getMessage(), result);
                    }
                    break;
                case WACallback.CODE_ERROR:
                default:
                    if(null != mQueryObjectsCallback) {
                        mQueryObjectsCallback.onError(WACallback.CODE_ERROR, result.getMessage(), null, null);
                    }
                    break;
            }

        }

        /**
         * 将请求返回的数据解析成实体类数据
         * @param response Facebook请求返回数据实体类
         * @return Ghw数据实体类，查询Object请求
         */
        private WAFBObjectResult parseResponseData(GraphResponse response) {
            WAFBObjectResult result = new WAFBObjectResult();
            int responseCode = getResponseCode(response);
            if(responseCode == 200) {
                // 获取JSON数据，这里为了防止GraphResponse中的getJSONObject出现错误拿到的对象为空，所以加了一个步骤就是
                // 对getRawResponse进行判断，如果不为空，将内容转换为JSON数据
                JSONObject jsonObject = response.getJSONObject();
                if(null == jsonObject && null != response.getRawResponse()) {
                    try {
                        jsonObject = new JSONObject(response.getRawResponse());
                    } catch (JSONException e) {
                        // do nothing
                    }
                }

                if(null == jsonObject) { // 请求成功了但是数据为空（数据解析失败）
                    result.setCode(WACallback.CODE_ERROR);
                    FacebookRequestError error = response.getError();
                    if(null == error) {
                        // 请求失败，error数据为空，自定义错误提示
                        result.setMessage("Query objects failed: return data is null");
                    } else {
                        // 请求失败，error数据不为空，转换为错误信息返回
                        result.setMessage("Query objects failed with exception:" + error.toString());
                    }
                } else {
                    // 请求成功，切数据不为空
                    result.setCode(WACallback.CODE_SUCCESS);
                    result.setMessage("Query objects success!");
                    JSONArray dataJSONArray = jsonObject.optJSONArray("data");
                    if(null != dataJSONArray) {
                        List<WAFBGraphObject> objects = new ArrayList<>();
                        WAFBGraphObject object;
                        for(int i = 0; i < dataJSONArray.length(); i++) {
                            object = parseObjectData(dataJSONArray.optJSONObject(i));
                            if(null == object || object.isScraped()) {
                                continue;
                            }
                            objects.add(object);
                        }
                        result.setObjects(objects);
                    }

                    JSONObject pagingObject = jsonObject.optJSONObject("paging");
                    WAFBPaging paging = parsePagingData(pagingObject);
                    result.setPaging(paging);
                }
            } else {
                result.setCode(WACallback.CODE_ERROR);
                FacebookRequestError error = response.getError();
                if(null == error) {
                    // 请求失败，error数据为空，自定义错误提示
                    result.setMessage("Query objects failed: return data is null");
                } else {
                    // 请求失败，error数据不为空，转换为错误信息返回
                    result.setMessage("Query objects failed with exception:" + error.toString());
                }
            }
            return result;
        }

        /**
         * 从WA后台查询Facebook的Secret key
         */
        private String loadFBSecretKeyAndWait() throws Exception {
            String className = "com.wa.sdk.wa.core.WASdkOnlineParameter";
            Class<?> cls = Class.forName(className);
            Method getInstanceMethod = cls.getDeclaredMethod("getInstance");
            Object instanceObject = getInstanceMethod.invoke(null);
            Method loadParameterAndWaitMethod = cls.getDeclaredMethod("loadParameterAndWait");
            Object resultObject = loadParameterAndWaitMethod.invoke(instanceObject);

            Class<?> resultClass = resultObject.getClass();
            Field fbSecretField = resultClass.getDeclaredField("fbSecretKey");
            fbSecretField.setAccessible(true);
            Object fbSecretObject = fbSecretField.get(resultObject);
            if(fbSecretObject instanceof  String) {
                return String.valueOf(fbSecretObject);
            }
            return null;
        }
    }
}
