package com.wa.sdk.fb.social;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;

import com.facebook.AccessToken;
import com.facebook.FacebookRequestError;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.HttpMethod;
import com.wa.sdk.common.model.WACallback;
import com.wa.sdk.common.model.WAResult;
import com.wa.sdk.fb.social.model.WAFBPaging;
import com.wa.sdk.fb.social.model.WAFBRequestResult;
import com.wa.sdk.fb.user.WAFBLogin;
import com.wa.sdk.social.model.WAFBGameRequestData;
import com.wa.sdk.social.model.WAFBGameRequestResult;
import com.wa.sdk.user.model.WAUser;
import com.wa.sdk.user.model.WALoginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询Facebook请求（礼物、邀请）
 * Created by yinglovezhuzhu@gmail.com on 2016/3/23.
 */
public class WAFBGameRequest extends WAFBGameService {

    private static final String FB_GRAPH_PATH_QUERY_REQUESTS = "/me/apprequests";

    private QueryRequestsTask mQueryRequestsTask;
    private WACallback<WAFBGameRequestResult> mQueryRequestsCallback;

    private DeleteRequestTask mDeleteRequestTask;
    private WACallback<WAResult> mDeleteRequestCallback;

    private String mRequestActionType;

    public static WAFBGameRequest newInstance() {
        return new WAFBGameRequest();
    }

    private WAFBGameRequest() {
        super();
    }

    @Override
    protected void onLoginFBSuccess(int code, String message, WALoginResult result) {
        PendingAction previouslyPendingAction = mPendingAction;
        switch (previouslyPendingAction) {
            case QUERY_REQUEST:
                checkAccount(null, new WACallback<WAResult>() {
                    @Override
                    public void onSuccess(int code, String message, WAResult result) {
                        queryFBGameRequest();
                    }

                    @Override
                    public void onCancel() {
                        if (null != mQueryRequestsCallback) {
                            mQueryRequestsCallback.onCancel();
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
                                if (null != mQueryRequestsCallback) {
                                    mQueryRequestsCallback.onError(code, message, null, null);
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
            case QUERY_REQUEST:
                if(null != mQueryRequestsCallback) {
                    mQueryRequestsCallback.onCancel();
                }
                break;
            case DELETE_REQUEST:
                if(null != mDeleteRequestCallback) {
                    mDeleteRequestCallback.onCancel();
                }
                break;
            default:
                break;
        }
    }

    @Override
    protected void onLoginFBError(int code, String message, WALoginResult result, Throwable throwable) {
        switch (mPendingAction) {
            case QUERY_REQUEST:
                if(null != mQueryRequestsCallback) {
                    mQueryRequestsCallback.onError(WACallback.CODE_LOGIN_FAILURE,
                            message, null, throwable);
                }
                break;
            case DELETE_REQUEST:
                if(null != mDeleteRequestCallback) {
                    mDeleteRequestCallback.onError(code, message, null, throwable);
                }
                break;
            default:
                break;
        }
    }

    /**
     * 查询请求列表（受到的礼物、好友向自己所要礼物）
     * @param activity 调用Activity
     * @param actionType Action类型
     * @param callback 回调
     */
    public void queryRequests(final Activity activity, final String actionType,
                              final WACallback<WAFBGameRequestResult> callback) {
        this.mActivity = activity;
        this.mQueryRequestsCallback = callback;
        this.mRequestActionType = actionType;

        mPendingAction = PendingAction.QUERY_REQUEST;
        checkAccount(null, new WACallback<WAResult>() {
            @Override
            public void onSuccess(int code, String message, WAResult result) {
                queryFBGameRequest();
            }

            @Override
            public void onCancel() {
                if (null != mQueryRequestsCallback) {
                    mQueryRequestsCallback.onCancel();
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
                        if (null != mQueryRequestsCallback) {
                            mQueryRequestsCallback.onError(code, message, null, null);
                        }
                        break;
                }
            }
        });
    }

    /**
     * 取消查询请求
     */
    @SuppressWarnings("unused")
    public void cancelQueryRequests() {
        if(null != mQueryRequestsTask && !mQueryRequestsTask.isCancelled()) {
            mQueryRequestsTask.cancel(true);
        }
    }


    /**
     * 删除请求
     * @param requestId Game request id
     * @param callback 回调
     */
    public void deleteRequest(final String requestId, final WACallback<WAResult> callback) {
        this.mDeleteRequestCallback = callback;
        mPendingAction = PendingAction.DELETE_REQUEST;
        checkAccount(null, new WACallback<WAResult>() {
            @Override
            public void onSuccess(int code, String message, WAResult result) {
                deleteFBGameRequest(requestId);
            }

            @Override
            public void onCancel() {
                if (null != callback) {
                    callback.onCancel();
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
                        if (null != callback) {
                            callback.onError(code, message, null, null);
                        }
                        break;
                }
            }
        });
    }

    /**
     * 取消删除请求
     */
    @SuppressWarnings("unused")
    public void cancelDeleteRequest() {
        if(null != mDeleteRequestTask && !mDeleteRequestTask.isCancelled()) {
            mDeleteRequestTask.cancel(true);
        }
    }

    /**
     * 查询Facebook请求
     */
    private void queryFBGameRequest() {
        mQueryRequestsTask = new QueryRequestsTask();
        if (null == mRequestActionType) {
            mRequestActionType = "";
        }
        mQueryRequestsTask.execute(mRequestActionType);
    }

    /**
     * 删除Facebook请求
     * @param requestId
     */
    private void deleteFBGameRequest(String requestId) {
        mDeleteRequestTask = new DeleteRequestTask();
        mDeleteRequestTask.execute(requestId);
    }

    /**
     * 查询收到的请求的异步线程类，参数是字符串类型，第一个参数有效，如果需要过滤，传入需要过滤的请求类型，不需要过滤直接不传
     */
    private class QueryRequestsTask extends AsyncTask<String, Integer, WAFBGameRequestResult> {

        @Override
        protected WAFBGameRequestResult doInBackground(String... params) {
            WAFBGameRequestResult result = new WAFBGameRequestResult();
            String actionType = null == params || params.length < 1 ? "" : params[0].toLowerCase();
            GraphRequest request = new GraphRequest();
            request.setAccessToken(AccessToken.getCurrentAccessToken());
            request.setGraphPath(FB_GRAPH_PATH_QUERY_REQUESTS);
            request.setHttpMethod(HttpMethod.GET);

            Bundle requestParameters = new Bundle();

            requestParameters.putString(FB_PARAMETER_FIELDS, "id,message,created_time,action_type," +
                    "from{id,name,picture},to{id,name,picture},application{id,name,namespace,category,link}," +
                    "object{id,type,title,created_time,description,image{url},is_scraped,data},data");
            requestParameters.putInt(FB_PARAMETER_LIMIT, 1000);
            request.setParameters(requestParameters);

            GraphResponse response = request.executeAndWait();

            WAFBRequestResult fbRequestResult = parseResponseData(response, actionType);
            result.setCode(fbRequestResult.getCode());
            result.setMessage(fbRequestResult.getMessage());
            result.addRequests(fbRequestResult.getRequests());

            WAFBPaging paging = fbRequestResult.getPaging();
            while(null != paging && paging.hasNext()) { // 防止一页没有全部获取
                requestParameters.putString(FB_PARAMETER_AFTER, paging.getAfter());
                request.setParameters(requestParameters);
                response = request.executeAndWait();
                fbRequestResult = parseResponseData(response, actionType);
                result.addRequests(fbRequestResult.getRequests());
                paging = fbRequestResult.getPaging();
            }

            return result;
        }

        /**
         * 将请求返回的数据解析成实体类数据
         * @param response Facebook数据请求返回实体类
         * @param actionType 请求类型
         * @return Ghw解析数据实体类
         */
        private WAFBRequestResult parseResponseData(GraphResponse response, String actionType) {
            WAFBRequestResult result = new WAFBRequestResult();
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
                        result.setMessage("Query requests failed: return data is null");
                    } else {
                        // 请求失败，error数据不为空，转换为错误信息返回
                        result.setMessage("Query requests failed with exception:" + error.toString());
                    }
                } else {
                    // 请求成功，且数据不为空
                    result.setCode(WACallback.CODE_SUCCESS);
                    result.setMessage("Query requests success!");
                    JSONArray dataJSONArray = jsonObject.optJSONArray("data");
                    List<WAFBGameRequestData> requests = parseRequestsData(dataJSONArray, actionType);
                    result.setRequests(requests);

                    JSONObject pagingObject = jsonObject.optJSONObject("paging");
                    WAFBPaging paging = parsePagingData(pagingObject);
                    result.setPaging(paging);
                }
            } else {
                result.setCode(WACallback.CODE_ERROR);
                FacebookRequestError error = response.getError();
                if(null == error) {
                    // 请求失败，error数据为空，自定义错误提示
                    result.setMessage("Query invitable friends failed: return data is null");
                } else {
                    // 请求失败，error数据不为空，转换为错误信息返回
                    result.setMessage("Query invitable friends failed with exception:" + error.toString());
                }
            }
            return result;
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            if(null != mQueryRequestsCallback) {
                mQueryRequestsCallback.onCancel();
            }
        }

        @Override
        protected void onPostExecute(WAFBGameRequestResult result) {
            super.onPostExecute(result);
            if(isCancelled()) {
                return;
            }
            switch (result.getCode()) {
                case WACallback.CODE_SUCCESS:
                    if(null != mQueryRequestsCallback) {
                        mQueryRequestsCallback.onSuccess(WACallback.CODE_SUCCESS, result.getMessage(), result);
                    }
                    break;
                case WACallback.CODE_ERROR:
                default:
                    if(null != mQueryRequestsCallback) {
                        mQueryRequestsCallback.onError(WACallback.CODE_ERROR, result.getMessage(), null, null);
                    }
                    break;
            }
        }

        /**
         * 解析好友列表数据
         * @param jsonArray 请求列表数据JSONArray
         * @param actionType 请求类型
         * @return 请求列表数据
         */
        private List<WAFBGameRequestData> parseRequestsData(JSONArray jsonArray, String actionType) {
            List<WAFBGameRequestData> requests = new ArrayList<>();
            if(null == jsonArray || jsonArray.length() < 1) {
                return requests;
            }
            JSONObject dataObject;
            WAFBGameRequestData request;
            String type;
            for(int i = 0; i < jsonArray.length(); i++) {
                dataObject = jsonArray.optJSONObject(i);
                if(null != dataObject) {
                    type = dataObject.optString("action_type");
                    if(!actionType.equals(type)) {
                        // 需要过滤，且类型不符合，跳过
                        continue;
                    }
                    request = new WAFBGameRequestData();
                    request.setId(dataObject.optString("id"));
                    request.setMessage(dataObject.optString("msg"));
                    request.setCreatedTime(dataObject.optString("created_time"));
                    request.setActionType(type);
                    request.setFrom(parseUserData(dataObject.optJSONObject("from")));
                    request.setTo(parseUserData(dataObject.optJSONObject("to")));
                    request.setObject(parseObjectData(dataObject.optJSONObject("object")));
                    request.setApplication(parseApplicationData(dataObject.optJSONObject("application")));
                    request.setData(dataObject.optString("data"));
                    requests.add(request);
                }
            }
            return requests;
        }

        /**
         * 解析用户数据（发送/接收者）
         * @param jsonObject 用户JSON数据
         * @return 用户数据信息
         */
        private WAUser parseUserData(JSONObject jsonObject) {
            if(null == jsonObject) {
                return null;
            }
            WAUser user = new WAUser();
            user.setId(jsonObject.optString("id"));
            user.setName(jsonObject.optString("name"));
            JSONObject pictureObject = jsonObject.optJSONObject("picture");
            if(null != pictureObject) {
                JSONObject pictureDataObject = pictureObject.optJSONObject("data");
                if(null != pictureDataObject) {
                    user.setPicture(pictureDataObject.optString("url"));
                }
            }
            return user;
        }

    }


    /**
     * 删除请求的异步线程类
     */
    private class DeleteRequestTask extends AsyncTask<String, Integer, WAResult> {

        @Override
        protected WAResult doInBackground(String... params) {
            WAResult result = new WAResult();
            if(null == params || params.length < 1) {
                result.setCode(WACallback.CODE_ERROR);
                result.setMessage("Parameter error: request_id should not be null");
                return result;
            }

            String requestId = params[0];
            if(null == requestId || "".equals(requestId)) {
                result.setCode(WACallback.CODE_ERROR);
                result.setMessage("Parameter error: request_id should not be null");
                return result;
            }

            GraphRequest request = new GraphRequest();
            request.setAccessToken(AccessToken.getCurrentAccessToken());
            request.setGraphPath(requestId);
            request.setHttpMethod(HttpMethod.DELETE);

            GraphResponse response = request.executeAndWait();

            result = parseResponseData(response);

            return result;
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
        }

        @Override
        protected void onPostExecute(WAResult result) {
            super.onPostExecute(result);
            if(isCancelled()) {
                return;
            }
            switch (result.getCode()) {
                case WACallback.CODE_SUCCESS:
                    if(null != mDeleteRequestCallback) {
                        mDeleteRequestCallback.onSuccess(WACallback.CODE_SUCCESS, result.getMessage(), result);
                    }
                    break;
                case WACallback.CODE_ERROR:
                default:
                    if(null != mDeleteRequestCallback) {
                        mDeleteRequestCallback.onError(WACallback.CODE_ERROR, result.getMessage(), null, null);
                    }
                    break;
            }
        }

        /**
         * 将请求返回的数据解析成实体类数据
         * @param response Facebook请求返回数据实体类
         * @return Ghw数据实体类
         */
        private WAResult parseResponseData(GraphResponse response) {
            WAResult result = new WAResult();
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
                        result.setMessage("Delete requests failed: return data is null");
                    } else {
                        // 请求失败，error数据不为空，转换为错误信息返回
                        result.setMessage("Delete requests failed with exception:" + error.toString());
                    }
                } else {
                    // 请求成功，且数据不为空
                    result.setCode(WACallback.CODE_SUCCESS);
                    result.setMessage("Delete requests success!");
                }
            } else {
                result.setCode(WACallback.CODE_ERROR);
                FacebookRequestError error = response.getError();
                if(null == error) {
                    // 请求失败，error数据为空，自定义错误提示
                    result.setMessage("Delete request failed:");
                } else {
                    // 请求失败，error数据不为空，转换为错误信息返回
                    result.setMessage("Delete request failed: with exception:" + error.toString());
                }
            }
            return result;
        }
    }

}
