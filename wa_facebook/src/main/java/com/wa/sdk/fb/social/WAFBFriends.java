package com.wa.sdk.fb.social;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;

import com.facebook.AccessToken;
import com.facebook.FacebookRequestError;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.HttpMethod;
import com.wa.sdk.WAConstants;
import com.wa.sdk.common.WASharedPrefHelper;
import com.wa.sdk.common.model.WACallback;
import com.wa.sdk.common.model.WAResult;
import com.wa.sdk.fb.social.model.WAFBFriendsResult;
import com.wa.sdk.fb.social.model.WAFBPaging;
import com.wa.sdk.fb.user.WAFBLogin;
import com.wa.sdk.social.model.WAFriendsResult;
import com.wa.sdk.user.model.WALoginResult;
import com.wa.sdk.user.model.WAUser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 查询Facebook好友（可邀请好友、正在玩该游戏的好友）列表
 * Created by yinglovezhuzhu@gmail.com on 2016/2/17.
 */
class WAFBFriends extends WAFBGameService {

    private static final String FB_GRAPH_PATH_QUERY_INVITABLE_FRIENDS = "/me/invitable_friends";

    private static final String FB_GRAPH_PATH_QUERY_FRIENDS = "/me/friends";

    private static final String SDK_PARAMETER_DURATION = "fb_invite_duration";

    private static long mInviteDuration = 1000 * 60 * 30L;

    private WACallback<WAFriendsResult> mQueryFriendsCallback;
    private QueryFriendsTask mQueryFriendsTask;
    private QueryInvitableFriendsTask mQueryInvitableFriendsTask;

    public static WAFBFriends newInstance() {
        return new WAFBFriends();
    }

    private WAFBFriends() {
        super();
    }

    @Override
    protected void onLoginFBSuccess(int code, String message, WALoginResult result) {
        final PendingAction previouslyPendingAction = mPendingAction;
        checkAccount(null, new WACallback<WAResult>() {

            @Override
            public void onSuccess(int code, String message, WAResult result) {
                switch (previouslyPendingAction) {
                    case QUERY_INVITABLE_FRIENDS:
                        // 搁置的动作是查询可邀请的好友
                        queryInvitableFriends();
                        break;
                    case QUERY_FRIENDS:
                        // 挂起的动作是查询使用app的好友
                        queryFriends();
                        break;
                    default:
                        if (null != mQueryFriendsCallback) {
                            mQueryFriendsCallback.onCancel();
                        }
                        break;
                }
            }

            @Override
            public void onCancel() {
                if (null != mQueryFriendsCallback) {
                    mQueryFriendsCallback.onCancel();
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
                        if (null != mQueryFriendsCallback) {
                            mQueryFriendsCallback.onError(code, message, null, null);
                        }
                        break;
                }
            }
        });
    }

    @Override
    protected void onLoginFBCanceled() {
        switch (mPendingAction) {
            case QUERY_FRIENDS:
                if(null != mQueryFriendsCallback) {
                    mQueryFriendsCallback.onCancel();
                }
                break;
            default:
                break;
        }
    }

    @Override
    protected void onLoginFBError(int code, String message, WALoginResult result, Throwable throwable) {
        switch (mPendingAction) {
            case QUERY_FRIENDS:
                if(null != mQueryFriendsCallback) {
                    mQueryFriendsCallback.onError(code, message, null, throwable);
                }
                break;
            default:
                break;
        }
    }

    /**
     * 查询可邀请好友列表
     * @param activity 调用activity
     * @param duration 排除邀请的时间间隔，在这个时间内邀请过的好友将不会再次邀请
     * @param callback 回调
     */
    public void queryInvitableFriends(final Activity activity, final long duration,
                                      final WACallback<WAFriendsResult> callback) {
        if(null == mSharedPrefHelper) {
            mSharedPrefHelper = WASharedPrefHelper.newInstance(activity.getApplicationContext(),
                    FB_RECENT_INVITE_FRIENDS);
        }
        mActivity = activity;
        mInviteDuration = duration;
        mQueryFriendsCallback = callback;

        mPendingAction = PendingAction.QUERY_INVITABLE_FRIENDS;
        handleQueryFriends();

    }


    /**
     * 取消正在查询的好友列表，取消后不返回结果
     */
    @SuppressWarnings("unused")
    public void cancelQueryInvitableFriends() {
        if(null != mQueryInvitableFriendsTask && !mQueryInvitableFriendsTask.isCancelled()) {
            mQueryInvitableFriendsTask.cancel(true);
        }
    }

    /**
     * 查询使用app的好友
     * @param activity 调用activity
     * @param callback 回调方法
     */
    public void queryFriends(final Activity activity, final WACallback<WAFriendsResult> callback) {
        mActivity = activity;
        mQueryFriendsCallback = callback;

        mPendingAction = PendingAction.QUERY_FRIENDS;
        handleQueryFriends();
    }

    /**
     * 取消正在查询的好友列表，取消后不返回结果
     */
    @SuppressWarnings("unused")
    public void cancelQueryFriends() {
        if(null != mQueryFriendsTask && !mQueryFriendsTask.isCancelled()) {
            mQueryFriendsTask.cancel(true);
        }
    }

    /**
     * 处理查询好友
     */
    private void handleQueryFriends() {
        final PendingAction pendingAction = mPendingAction;
        checkAccount(Collections.singletonList(WAConstants.FB_PERMISSION_USER_FRIENDS),
                new WACallback<WAResult>() {
                    @Override
                    public void onSuccess(int code, String message, WAResult result) {
                        switch (pendingAction) {
                            case QUERY_INVITABLE_FRIENDS:
                                queryInvitableFriends();
                                break;
                            case QUERY_FRIENDS:
                                queryFriends();
                                break;
                            default:
                                if (null != mQueryFriendsCallback) {
                                    mQueryFriendsCallback.onCancel();
                                }
                                break;
                        }
                    }

                    @Override
                    public void onCancel() {
                        if (null != mQueryFriendsCallback) {
                            mQueryFriendsCallback.onCancel();
                        }
                    }

                    @Override
                    public void onError(int code, String message, WAResult result, Throwable throwable) {
                        switch (code) {
                            case CODE_NOT_LOGIN_WITH_FACEBOOK: // 没有登录Facebook
                            case CODE_NO_PERMISSION: // 没有相应权限
                            case CODE_FACEBOOK_ACCOUNT_NOT_MATCH: // 授权的Facebook账户和当前用户关联的Facebook账户不匹配
                                WAFBLogin.getInstance().login(mActivity, true, mLoginCallback,
                                        createLoginExtra());
                                break;
                            case CODE_ERROR:
                            default:
                                if (null != mQueryFriendsCallback) {
                                    mQueryFriendsCallback.onError(code, message, null, null);
                                }
                                break;
                        }
                    }
                });
    }

    /**
     * 生成一个登陆获取user_friends限的登陆额外数据
     * @return
     */
    private String createLoginExtra() {
        JSONObject extInfoJson = new JSONObject();
        try {
            extInfoJson.putOpt("permissionType", "read");
            JSONArray permissions = new JSONArray();
            permissions.put(WAConstants.FB_PERMISSION_USER_FRIENDS);
            extInfoJson.putOpt("permissions", permissions);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return extInfoJson.toString();
    }

    /**
     * 查询可邀请好友列表
     */
    private void queryInvitableFriends() {
        mQueryInvitableFriendsTask = new QueryInvitableFriendsTask();
        Bundle params = new Bundle();
        params.putLong(SDK_PARAMETER_DURATION, mInviteDuration);
        mQueryInvitableFriendsTask.execute(params);
    }

    /**
     * 查询好友（正在玩这个游戏的好友）
     */
    private void queryFriends() {
        mQueryFriendsTask = new QueryFriendsTask();
        mQueryFriendsTask.execute();
    }

    /**
     * 查询可邀请好友的异步线程类
     */
    private class QueryInvitableFriendsTask extends AsyncTask<Bundle, Integer, WAFriendsResult> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected WAFriendsResult doInBackground(Bundle... params) {
            Bundle requestParameters = params.length > 0 ? params[0] : null;

            GraphRequest request = new GraphRequest();
            request.setAccessToken(AccessToken.getCurrentAccessToken());
            request.setGraphPath(FB_GRAPH_PATH_QUERY_INVITABLE_FRIENDS);
            request.setHttpMethod(HttpMethod.GET);

            if(null == requestParameters) {
                requestParameters = new Bundle();
            }
            requestParameters.putString(FB_PARAMETER_FIELDS, "id,name,picture");
            Long inviteDuration = 0L;
            if(requestParameters.containsKey(SDK_PARAMETER_DURATION)) {
                inviteDuration = requestParameters.getLong(SDK_PARAMETER_DURATION);
                requestParameters.remove(SDK_PARAMETER_DURATION); // 移出sdk自身的参数，避免影响fb请求
            }
            String excludedIds = getInviteExcludedIds(inviteDuration).toString();
            requestParameters.putString(FB_PARAMETER_EXCLUDED_IDS, excludedIds);

            request.setParameters(requestParameters);

            GraphResponse response = request.executeAndWait();

            WAFBFriendsResult fbResult = parseResponseData(response);

            WAFBPaging paging = fbResult.getPaging();
            while(null != paging && paging.hasNext()) {
                request = new GraphRequest();
                request.setAccessToken(AccessToken.getCurrentAccessToken());
                request.setGraphPath(FB_GRAPH_PATH_QUERY_INVITABLE_FRIENDS);
                request.setHttpMethod(HttpMethod.GET);
                requestParameters.putString(FB_PARAMETER_AFTER, paging.getAfter());
                request.setParameters(requestParameters);
                response = request.executeAndWait();
                WAFBFriendsResult pageResult = parseResponseData(response);
                fbResult.addFriends(pageResult.getFriends());
                paging = pageResult.getPaging();
            }

            // 将Facebook的结果转换成WA的结果实体类对象
            WAFriendsResult result = new WAFriendsResult();
            result.setCode(fbResult.getCode());
            result.setMessage(fbResult.getMessage());
            result.addFriends(fbResult.getFriends());
            return result;
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            if(null != mQueryFriendsCallback) {
                mQueryFriendsCallback.onCancel();
            }
        }

        @Override
        protected void onPostExecute(WAFriendsResult result) {
            super.onPostExecute(result);
            if(isCancelled()) {
                return;
            }
            switch (result.getCode()) {
                case WACallback.CODE_SUCCESS:
                    if(null != mQueryFriendsCallback) {
                        mQueryFriendsCallback.onSuccess(WACallback.CODE_SUCCESS, result.getMessage(), result);
                    }
                    break;
                case WACallback.CODE_ERROR:
                default:
                    if(null != mQueryFriendsCallback) {
                        mQueryFriendsCallback.onError(WACallback.CODE_ERROR, result.getMessage(), null, null);
                    }
                    break;
            }

        }
        /**
         * 获取排除的idJSONArray数据
         * @param duration 排除事件间隔
         * @return 排除的好友id JSONArray
         */
        @SuppressWarnings("unchecked")
        private JSONArray getInviteExcludedIds(long duration) {
            JSONArray excludedArray = new JSONArray();
            if(null != mSharedPrefHelper) {
                Map<String, Long> recentInviteFriends = (Map<String, Long>) mSharedPrefHelper.getAll();
                if(null == recentInviteFriends || recentInviteFriends.isEmpty()) {
                    return excludedArray;
                }
                Set<String> ids = recentInviteFriends.keySet();
                long currentTimeMillis = System.currentTimeMillis();
                for(String id : ids) {
                    if(currentTimeMillis - recentInviteFriends.get(id) < duration) {
                        excludedArray.put(id);
                    } else {
                        // 将上一次邀请事件大于时间间隔的从缓存中删除
                        mSharedPrefHelper.remove(id);
                    }
                }
            }
            return excludedArray;
        }

        /**
         * 将请求返回的数据解析成实体类数据
         * @param response Facebook请求返回数据实体类
         * @return Ghw返回数据解析后实体类
         */
        private WAFBFriendsResult parseResponseData(GraphResponse response) {
            WAFBFriendsResult result = new WAFBFriendsResult();
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
                        result.setMessage("Query invitable friends failed: return data is null");
                    } else {
                        // 请求失败，error数据不为空，转换为错误信息返回
                        result.setMessage("Query invitable friends failed with exception:" + error.toString());
                    }
                } else {
                    // 请求成功，且数据不为空
                    result.setCode(WACallback.CODE_SUCCESS);
                    result.setMessage("Query invitable friends success!");
                    JSONArray dataJSONArray = jsonObject.optJSONArray("data");
                    List<WAUser> friends = parseFriendsData(dataJSONArray);
                    result.setFriends(friends);

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

        /**
         * 解析分页数据
         * @param jsonObject 好友列表分页JSON数据
         * @return 好友列表分页数据
         */
        private WAFBPaging parsePagingData(JSONObject jsonObject) {
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
    }

    /**
     * 查询可邀请好友的异步线程类
     */
    private class QueryFriendsTask extends AsyncTask<Bundle, Integer, WAFriendsResult> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected WAFriendsResult doInBackground(Bundle... params) {
            Bundle requestParameters = params.length > 0 ? params[0] : null;

            GraphRequest request = new GraphRequest();
            request.setAccessToken(AccessToken.getCurrentAccessToken());
            request.setGraphPath(FB_GRAPH_PATH_QUERY_FRIENDS);
            request.setHttpMethod(HttpMethod.GET);

            if(null == requestParameters) {
                requestParameters = new Bundle();
            }

            requestParameters.putString(FB_PARAMETER_FIELDS, "id,name,picture");
            requestParameters.putInt(FB_PARAMETER_LIMIT, 10000);
            request.setParameters(requestParameters);

            GraphResponse response = request.executeAndWait();

            WAFriendsResult result = parseResponseData(response);
            // 这里将从Facebook查询得到的open id到WA SDK服务器中查询WA用户id，得到新数据
            result.setFriends(queryWAUserIdAndWaite(result.getFriends()));
            return result;
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            if(null != mQueryFriendsCallback) {
                mQueryFriendsCallback.onCancel();
            }
        }

        @Override
        protected void onPostExecute(WAFriendsResult result) {
            super.onPostExecute(result);
            if(isCancelled()) {
                return;
            }
            switch (result.getCode()) {
                case WACallback.CODE_SUCCESS:
                    if(null != mQueryFriendsCallback) {
                        mQueryFriendsCallback.onSuccess(WACallback.CODE_SUCCESS, result.getMessage(), result);
                    }
                    break;
                case WACallback.CODE_ERROR:
                default:
                    if(null != mQueryFriendsCallback) {
                        mQueryFriendsCallback.onError(WACallback.CODE_ERROR, result.getMessage(), null, null);
                    }
                    break;
            }

        }

        /**
         * 将请求返回的数据解析成实体类数据
         * @param response Facebook返回数据实体类
         * @return Ghw解析后的数据实体类
         */
        private WAFriendsResult parseResponseData(GraphResponse response) {
            WAFriendsResult result = new WAFriendsResult();
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
                        result.setMessage("Query friends failed: return data is null");
                    } else {
                        // 请求失败，error数据不为空，转换为错误信息返回
                        result.setMessage("Query friends failed with exception:" + error.toString());
                    }
                } else {
                    // 请求成功，且数据不为空
                    result.setCode(WACallback.CODE_SUCCESS);
                    result.setMessage("Query friends success!");
                    JSONArray dataJSONArray = jsonObject.optJSONArray("data");
                    List<WAUser> friends = parseFriendsData(dataJSONArray);
                    result.setFriends(friends);
                }
            } else {
                result.setCode(WACallback.CODE_ERROR);
                FacebookRequestError error = response.getError();
                if(null == error) {
                    // 请求失败，error数据为空，自定义错误提示
                    result.setMessage("Query friends failed: return data is null");
                } else {
                    // 请求失败，error数据不为空，转换为错误信息返回
                    result.setMessage("Query friends failed with exception:" + error.toString());
                }
            }
            return result;
        }
    }

    /**
     * 解析好友列表数据
     * @param jsonArray 好友列表JSONArray数据
     * @return 好友列表数据
     */
    private List<WAUser> parseFriendsData(JSONArray jsonArray) {
        List<WAUser> friends = new ArrayList<>();
        if(null == jsonArray || jsonArray.length() < 1) {
            return friends;
        }
        JSONObject dataObject;
        JSONObject pictureObject;
        JSONObject pictureDataObject;
        WAUser user;
        for(int i = 0; i < jsonArray.length(); i++) {
            dataObject = jsonArray.optJSONObject(i);
            if(null != dataObject) {
                user = new WAUser(WAConstants.CHANNEL_FACEBOOK);
                user.setId(dataObject.optString("id"));
                user.setName(dataObject.optString("name"));
                pictureObject = dataObject.optJSONObject("picture");
                if(null != pictureObject) {
                    pictureDataObject = pictureObject.optJSONObject("data");
                    if(null != pictureDataObject) {
                        user.setPicture(pictureDataObject.optString("url"));
                    }
                }
                friends.add(user);
            }
        }
        return friends;
    }
}
