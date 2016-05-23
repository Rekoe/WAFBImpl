package com.wa.sdk.fb.social.share;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import com.facebook.AccessToken;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.share.ShareApi;
import com.facebook.share.Sharer;
import com.facebook.share.model.ShareContent;
import com.facebook.share.model.ShareLinkContent;
import com.facebook.share.model.ShareOpenGraphAction;
import com.facebook.share.model.ShareOpenGraphContent;
import com.facebook.share.model.ShareOpenGraphObject;
import com.facebook.share.model.SharePhoto;
import com.facebook.share.model.SharePhotoContent;
import com.facebook.share.model.ShareVideo;
import com.facebook.share.model.ShareVideoContent;
import com.facebook.share.widget.ShareDialog;
import com.wa.sdk.WAConstants;
import com.wa.sdk.common.model.WACallback;
import com.wa.sdk.common.model.WACallbackManagerImpl;
import com.wa.sdk.common.model.WAResult;
import com.wa.sdk.common.utils.FileUtil;
import com.wa.sdk.common.utils.LogUtil;
import com.wa.sdk.fb.WAFBConstants;
import com.wa.sdk.fb.WAFacebook;
import com.wa.sdk.fb.social.share.internal.WAFBShareInternalUtility;
import com.wa.sdk.fb.user.WAFBLogin;
import com.wa.sdk.social.model.WAShareContent;
import com.wa.sdk.social.model.WAShareLinkContent;
import com.wa.sdk.social.model.WAShareOpenGraphAction;
import com.wa.sdk.social.model.WAShareOpenGraphContent;
import com.wa.sdk.social.model.WAShareOpenGraphObject;
import com.wa.sdk.social.model.WASharePhoto;
import com.wa.sdk.social.model.WASharePhotoContent;
import com.wa.sdk.social.model.WAShareResult;
import com.wa.sdk.social.model.WAShareVideo;
import com.wa.sdk.social.model.WAShareVideoContent;
import com.wa.sdk.user.model.WALoginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Facebook 分享
 * Created by yinglovezhuzhu@gmail.com on 2016/3/21.
 */
public class WAFBShare extends WAFacebook {
    /** 分享的图片/视频文件大小最大限制 **/
    private static final long MAX_FILE_SIZE = 1024 * 1024 * 12; //12MB

    private String message;

    private final Activity mActivity;
    private final WAShareContent mShareContent;
    private final WACallback<WAShareResult> mShareCallback;
    private final boolean mShareWithApi; // 通过api分享内容，如果true没有UI界面

    private PendingAction mPendingAction = PendingAction.NONE;
    private boolean mAllowNoToken = false; // 是否可以不需要Token，也就是不需要登陆，可调用对话框


    private ShareDialog mFBShareDialog;
    private FacebookCallback<Sharer.Result> mFBShareCallback = new FacebookCallback<Sharer.Result>() {
        @Override
        public void onCancel() {
            LogUtil.d(WAFBConstants.TAG, "WAFBShare--Share to facebook canceled");
            cancelLoadingDialog();
            if(null != mShareCallback) {
                mShareCallback.onCancel();
            }
        }

        @Override
        public void onError(FacebookException error) {
            LogUtil.d(WAFBConstants.TAG, String.format("WAFBShare--Share to facebook error: %s", error.toString()));
            cancelLoadingDialog();
            if(null != mShareCallback) {
                mShareCallback.onError(WACallback.CODE_EXCEPTION, error.getMessage(), null, error);
            }
        }

        @Override
        public void onSuccess(Sharer.Result result) {
            Log.d(WAFBConstants.TAG, "WAFBShare--Share to facebook success!");
            cancelLoadingDialog();
            if(null != mShareCallback) {
                WAShareResult shareResult = new WAShareResult(WACallback.CODE_SUCCESS,
                        "Share to facebook success!");
                shareResult.setExtra("Post id: " + result.getPostId());
                mShareCallback.onSuccess(WACallback.CODE_SUCCESS,
                        "Share to facebook success!", shareResult);
            }
        }
    };

    private WACallback<WALoginResult> mLoginCallback = new WACallback<WALoginResult>() {
        @Override
        public void onSuccess(int code, String message, WALoginResult result) {
//            showLoadingDialog(null, null);
            checkAccount(!mShareWithApi && mAllowNoToken ?
                            null : Collections.singletonList(WAConstants.FB_PERMISSION_PUBLISH_ACTIONS),
                    new WACallback<WAResult>() {
                        @Override
                        public void onSuccess(int code, String message, WAResult result) {
                            cancelLoadingDialog();
                            handlePendingAction();
                        }

                        @Override
                        public void onCancel() {
                            cancelLoadingDialog();
                            if (null != mShareCallback) {
                                mShareCallback.onCancel();
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
                                    if (null != mShareCallback) {
                                        mShareCallback.onError(code, message, null, null);
                                    }
                                    break;
                            }
                        }
                    });
        }

        @Override
        public void onCancel() {
            cancelLoadingDialog();
            if (mPendingAction != PendingAction.NONE) {
                mPendingAction = PendingAction.NONE;
                // 有分享，但是登陆出现了错误
                if(null != mShareCallback) {
                    mShareCallback.onError(WACallback.CODE_LOGIN_FAILURE,
                            "Login with facebook failed", null, null);
                }
            }
        }

        @Override
        public void onError(int code, String message, WALoginResult result, Throwable throwable) {
            cancelLoadingDialog();
            if (mPendingAction != PendingAction.NONE) {
                // 有分享，但是登陆出现了错误,获取不到权限
                mPendingAction = PendingAction.NONE;
                if(null != mShareCallback) {
                    mShareCallback.onError(WACallback.CODE_LOGIN_FAILURE,
                            "Login with facebook failed:" + (null == throwable ?
                                    "" : throwable.getMessage()), null, throwable);
                }
            } else {
                if(null != mShareCallback) {
                    mShareCallback.onError(WACallback.CODE_LOGIN_FAILURE,
                            "Login with facebook failed:" + (null == throwable ?
                                    "" : throwable.getMessage()), null, throwable);
                }
            }
        }
    };


    /**
     * 分享，如果有可供调用的编辑界面，将会调用编辑界面，如果没有，将会调用api直接发送数据
     * @param activity activity
     * @param shareContent 分享内容
     * @param shareWithApi 是否通过api分享
     * @param callback 回调
     */
    public static void share(Activity activity, WAShareContent shareContent, boolean shareWithApi,
                             WACallback<WAShareResult> callback) {
        new WAFBShare(activity, shareContent, shareWithApi, callback).share();
    }

    /**
     * 是否支持编辑UI
     * @param shareContent
     * @return
     */
    public static boolean editUISupported(WAShareContent shareContent) {
        if(shareContent instanceof WAShareLinkContent) {
            return ShareDialog.canShow(ShareLinkContent.class);
        } else if(shareContent instanceof WASharePhotoContent) {
            return ShareDialog.canShow(SharePhotoContent.class);
        } else if(shareContent instanceof WAShareVideoContent) {
            return ShareDialog.canShow(ShareVideoContent.class);
        } else if(shareContent instanceof WAShareOpenGraphContent) {
            return ShareDialog.canShow(ShareOpenGraphContent.class);
        }
        return false;
    }

    /**
     * Returns the message the person has provided through the custom dialog that will accompany the
     * share content.
     * @return the message.
     */
    public String getMessage() {
        return this.message;
    }

    /**
     * Sets the message the person has provided through the custom dialog that will accompany the
     * share content.
     * @param message the message.
     */
    public void setMessage(final String message) {
        this.message = message;
    }

    /**
     * Returns the content to be shared.
     *
     * @return the content to be shared.
     */
    public WAShareContent getShareContent() {
        return this.mShareContent;
    }

    /**
     * Returns true if the content can be shared. Warns if the access token is missing the
     * publish_actions permission. Doesn't fail when this permission is missing, because the app
     * could have been granted that permission in another installation.
     *
     * @return true if the content can be shared.
     */
    public boolean canShare() {
        if (null == this.mShareContent) {
            return false;
        }
        return true;
    }

    /**
     * 构造函数
     *
     * @param activity activity
     * @param shareContent 分享内容
     * @param shareWithApi 是否通过api分享
     * @param callback 回调
     */
    private WAFBShare(Activity activity, WAShareContent shareContent,
                       boolean shareWithApi, WACallback<WAShareResult> callback) {
        this.mActivity = activity;
        this.mShareContent = shareContent;
        this.mShareWithApi = shareWithApi;
        this.mShareCallback = callback;
        this.setMessage(shareContent.getMessage());
        mFBShareDialog = new ShareDialog(activity);
        mFBShareDialog.registerCallback(mFBCallbackManager, mFBShareCallback,
                WACallbackManagerImpl.RequestCodeOffset.FacebookShare.toRequestCode());

        registerCallbackImpl(mCallbackManager,
                WACallbackManagerImpl.RequestCodeOffset.FacebookShare.toRequestCode(),
                new WACallbackManagerImpl.Callback() {

                    @Override
                    public boolean onActivityResult(int resultCode, Intent data) {
                        mFBCallbackManager.onActivityResult(WACallbackManagerImpl.RequestCodeOffset.FacebookShare.toRequestCode(),
                                resultCode, data);
                        return false;
                    }
                });
    }


    public void share() {
        if(!canShare()) {
            WAFBShareInternalUtility.invokeCallbackWithError(mShareCallback,
                    WACallback.CODE_CONTENT_CAN_NOT_BE_SHARED,
                    "Content can't be shared.");
            return;
        }
        if(!FacebookSdk.isInitialized()) {
            WAFBShareInternalUtility.invokeCallbackWithError(mShareCallback,
                    WACallback.CODE_SDK_UNINITIALIZED,
                    "Facebook sdk uninitialized");
        }

        final WAShareContent shareContent = getShareContent();
        if(shareContent instanceof WAShareLinkContent) { // Facebook 分享链接
            mAllowNoToken = ShareDialog.canShow(ShareLinkContent.class);
            mPendingAction = PendingAction.SHARE_LINK;
        } else if(shareContent instanceof WASharePhotoContent) {
            mAllowNoToken = ShareDialog.canShow(SharePhotoContent.class);
            mPendingAction = PendingAction.SHARE_PHOTO;
        } else if(shareContent instanceof  WAShareVideoContent) {
            mAllowNoToken = ShareDialog.canShow(ShareVideoContent.class);
            mPendingAction = PendingAction.SHARE_VIDEO;
        } else if(shareContent instanceof WAShareOpenGraphContent) {
            mAllowNoToken = ShareDialog.canShow(ShareOpenGraphContent.class);
            mPendingAction = PendingAction.SHARE_OPEN_GRAPH;
        }

        if(!mShareWithApi && !mAllowNoToken) {
            // 非API分享，但是又不支持界面分享的情况
            if(null != mShareCallback) {
                mShareCallback.onError(WACallback.CODE_ERROR,
                        "Share " + shareContent.getClass().getSimpleName()
                                + " by facebook only supported by api", null, null);
            }
            return;
        }

        showLoadingDialog(mActivity, null, null);
        checkAccount(!mShareWithApi && mAllowNoToken ? null : Collections.singletonList(WAConstants.FB_PERMISSION_PUBLISH_ACTIONS),
                new WACallback<WAResult>() {
                    @Override
                    public void onSuccess(int code, String message, WAResult result) {
                        cancelLoadingDialog();
                        if (!mShareWithApi && mAllowNoToken) {
                            handlePendingAction();
                        } else {
                            if (hasPublishPermission() && !isTokenExpiries()) {
                                handlePendingAction();
                            } else {
                                // We need to get new permissions, then complete the action when we get called back.
                                showLoadingDialog(mActivity, null, null);
                                WAFBLogin.getInstance().login(mActivity, true, mLoginCallback, createLoginExtra());
                            }
                        }
                    }

                    @Override
                    public void onCancel() {
                        cancelLoadingDialog();
                        if (null != mShareCallback) {
                            mShareCallback.onCancel();
                        }
                    }

                    @Override
                    public void onError(int code, String message, WAResult result, Throwable throwable) {
                        cancelLoadingDialog();
                        switch (code) {
                            case CODE_NOT_LOGIN_WITH_FACEBOOK: // 没有登录Facebook
                            case CODE_NO_PERMISSION: // 没有相应权限
                            case CODE_FACEBOOK_ACCOUNT_NOT_MATCH: // 授权的Facebook账户和当前用户关联的Facebook账户不匹配
                                // We need to get new permissions, then complete the action when we get called back.
                                showLoadingDialog(mActivity, null, null);
                                WAFBLogin.getInstance().login(mActivity, true, mLoginCallback, createLoginExtra());
                                break;
                            case CODE_ERROR:
                            default:
                                cancelLoadingDialog();
                                if (null != mShareCallback) {
                                    mShareCallback.onError(code, message, null, null);
                                }
                                break;
                        }
                    }
                });
    }



    public void handlePendingAction() {
        PendingAction previouslyPendingAction = mPendingAction;
        // These actions may re-set pendingAction if they are still pending, but we assume they
        // will succeed.
        mPendingAction = PendingAction.NONE;

        switch (previouslyPendingAction) {
            case NONE:
                break;
            case SHARE_LINK:
                shareFBLinkContent((WAShareLinkContent) mShareContent, mShareCallback);
                break;
            case SHARE_PHOTO:
                shareFBPhotoContent((WASharePhotoContent) mShareContent, mShareCallback);
                break;
            case SHARE_VIDEO:
                shareFBVideoContent((WAShareVideoContent) mShareContent, mShareCallback);
                break;
            case SHARE_OPEN_GRAPH:
                shareFBOpenGraphContent((WAShareOpenGraphContent) mShareContent, mShareCallback);
                break;
            default:
                break;
        }
    }

    /**
     * 生成一个登陆获取publish_actions权限的登陆额外数据
     * @return
     */
    private String createLoginExtra() {
        JSONObject extInfoJson = new JSONObject();
        try {
            extInfoJson.putOpt("permissionType", "publish");
            JSONArray permissions = new JSONArray();
            permissions.put(WAConstants.FB_PERMISSION_PUBLISH_ACTIONS);
            extInfoJson.putOpt("permissions", permissions);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return extInfoJson.toString();
    }

    /**
     * 分享链接
     * @param shareLinkContent
     * @param callback
     */
    private void shareFBLinkContent(WAShareLinkContent shareLinkContent,
                                    WACallback<WAShareResult> callback) {
        ShareLinkContent fbShareLinkContent = new ShareLinkContent.Builder()
                .setContentUrl(shareLinkContent.getContentUri())
                .setPeopleIds(shareLinkContent.getPeopleIds())
                .setPlaceId(shareLinkContent.getPlaceId())
                .setRef(shareLinkContent.getRef())
                .setContentTitle(shareLinkContent.getContentTitle())
                .setContentDescription(shareLinkContent.getContentDescription())
                .setImageUrl(shareLinkContent.getImageUri())
                .build();
        if (!mShareWithApi && mAllowNoToken) {
            mFBShareDialog.show(fbShareLinkContent);
        } else if (mShareWithApi && hasPublishPermission() && !isTokenExpiries()) {
            showLoadingDialog(mActivity, null, null);
            shareWithApi(fbShareLinkContent, mFBShareCallback);
        } else {
            cancelLoadingDialog();
            if(null != callback) {
                callback.onError(WACallback.CODE_NO_PERMISSION, "No publish permission", null, null);
            }
        }
    }

    /**
     * 分享图片
     * @param sharePhotoContent 分享图片内容
     * @param callback 回调
     */
    private void shareFBPhotoContent(WASharePhotoContent sharePhotoContent,
                                     WACallback<WAShareResult> callback) {
        List<WASharePhoto> waPhotos = sharePhotoContent.getPhotos();
        List<SharePhoto> fbPhotos = new ArrayList<SharePhoto>();
        SharePhoto fbPhoto;
        long fileSize = 0L;
        if(null != waPhotos) {
            for( WASharePhoto photo : waPhotos) {
                if(null == photo) {
                    continue;
                }
                if(null != photo.getImageUri()) {
                    fileSize += FileUtil.getFileSize(FileUtil.parseUriToFile(mActivity,
                            photo.getImageUri()));
                }
                fbPhoto = new SharePhoto.Builder()
                        .setBitmap(photo.getBitmap())
                        .setImageUrl(photo.getImageUri())
                        .setUserGenerated(photo.getUserGenerated())
                        .build();
                fbPhotos.add(fbPhoto);
            }
        }
        if(fileSize > MAX_FILE_SIZE) {
            cancelLoadingDialog();
            if(null != callback) {
                callback.onError(WACallback.CODE_FILE_SIZE_LIMIT, "File size larger than "
                        + FileUtil.formatByte(mActivity, MAX_FILE_SIZE) + "MB", null, null);
            }
            return;
        }

        SharePhotoContent fbSharePhotoContent = new SharePhotoContent.Builder()
                .setPeopleIds(sharePhotoContent.getPeopleIds())
                .setContentUrl(sharePhotoContent.getContentUri())
                .setPlaceId(sharePhotoContent.getPlaceId())
                .setRef(sharePhotoContent.getRef())
                .setPhotos(fbPhotos)
                .build();
        if (!mShareWithApi && mAllowNoToken) {
            mFBShareDialog.show(fbSharePhotoContent);
        } else if (mShareWithApi && hasPublishPermission() && !isTokenExpiries()) {
            showLoadingDialog(mActivity, null, null);
            shareWithApi(fbSharePhotoContent, mFBShareCallback);
        } else {
            cancelLoadingDialog();
            if(null != callback) {
                callback.onError(WACallback.CODE_NO_PERMISSION, "No publish permission", null, null);
            }
        }
    }

    /**
     * 分享视频
     * @param shareVideoContent
     * @param callback
     */
    public void shareFBVideoContent(WAShareVideoContent shareVideoContent,
                                    WACallback<WAShareResult> callback) {
        WAShareVideo waVideo = shareVideoContent.getVideo();
        if(null != waVideo && null != waVideo.getLocalUri()) {
            long fileSize = FileUtil.getFileSize(FileUtil.parseUriToFile(mActivity,
                    waVideo.getLocalUri()));
            if(fileSize > MAX_FILE_SIZE) {
                cancelLoadingDialog();
                if(null != callback) {
                    callback.onError(WACallback.CODE_FILE_SIZE_LIMIT, "File size larger than "
                            + FileUtil.formatByte(mActivity, MAX_FILE_SIZE) + "MB", null, null);
                }
                return;
            }
        }
        WASharePhoto waPhoto = shareVideoContent.getPreviewPhoto();
        ShareVideo fbShareVideo = null;
        if(null != waVideo) {
            fbShareVideo = new ShareVideo.Builder()
                    .setLocalUrl(waVideo.getLocalUri())
                    .build();
        }
        SharePhoto fbSharePhoto = null;
        if(null != waPhoto) {
            fbSharePhoto = new SharePhoto.Builder()
                    .setBitmap(waPhoto.getBitmap())
                    .setImageUrl(waPhoto.getImageUri())
                    .setUserGenerated(waPhoto.getUserGenerated())
                    .build();
        }

        ShareVideoContent fbShareVideoContent = new ShareVideoContent.Builder()
                .setPeopleIds(shareVideoContent.getPeopleIds())
                .setPlaceId(shareVideoContent.getPlaceId())
                .setContentUrl(shareVideoContent.getContentUri())
                .setRef(shareVideoContent.getRef())
                .setContentTitle(shareVideoContent.getContentTitle())
                .setContentDescription(shareVideoContent.getContentDescription())
                .setVideo(fbShareVideo)
                .setPreviewPhoto(fbSharePhoto)
                .build();
        if (!mShareWithApi && mAllowNoToken) {
            mFBShareDialog.show(fbShareVideoContent);
        } else if (mShareWithApi && hasPublishPermission() && !isTokenExpiries()) {
            showLoadingDialog(mActivity, null, null);
            shareWithApi(fbShareVideoContent, mFBShareCallback);
        } else {
            cancelLoadingDialog();
            if(null != callback) {
                callback.onError(WACallback.CODE_NO_PERMISSION, "No publish permission", null, null);
            }
        }
    }

    /**
     * 分享Facebook OpenGraph
     * @param shareOpenGraphContent
     * @param callback
     */
    public void shareFBOpenGraphContent(WAShareOpenGraphContent shareOpenGraphContent,
                                        WACallback<WAShareResult> callback) {
        WAShareOpenGraphAction waAction = shareOpenGraphContent.getAction();
        ShareOpenGraphContent fbShareOpenGraphContent = new ShareOpenGraphContent.Builder()
                .setAction(getFBOpenGraphAction(waAction))
                .setPreviewPropertyName(shareOpenGraphContent.getPreviewPropertyName())
                .setContentUrl(shareOpenGraphContent.getContentUri())
                .setPeopleIds(shareOpenGraphContent.getPeopleIds())
                .setPlaceId(shareOpenGraphContent.getPlaceId())
                .setRef(shareOpenGraphContent.getRef())
                .build();
        if (!mShareWithApi && mAllowNoToken) {
            mFBShareDialog.show(fbShareOpenGraphContent);
        } else if (mShareWithApi && hasPublishPermission() && !isTokenExpiries()) {
            shareWithApi(fbShareOpenGraphContent, mFBShareCallback);
        } else {
            cancelLoadingDialog();
            if(null != callback) {
                callback.onError(WACallback.CODE_NO_PERMISSION, "No publish permission", null, null);
            }
        }
    }

    private boolean hasPublishPermission() {
        return hasPermission(WAConstants.FB_PERMISSION_PUBLISH_ACTIONS);
    }

    /**
     * Token是否过期
     * @return
     */
    private boolean isTokenExpiries() {
        AccessToken accessToken = AccessToken.getCurrentAccessToken();
        return null == accessToken || System.currentTimeMillis() > accessToken.getExpires().getTime();
    }

    private void shareWithApi(ShareContent shareContent, FacebookCallback<Sharer.Result> callback) {
        ShareApi shareApi = new ShareApi(shareContent);
        shareApi.setMessage(this.getMessage());
        shareApi.share(callback);
    }

    /**
     * 将{@linkplain WAShareOpenGraphAction}转换成Facebook的{@linkplain ShareOpenGraphAction}
     * @param action
     * @return
     */
    private ShareOpenGraphAction getFBOpenGraphAction(WAShareOpenGraphAction action) {
        if(null == action) {
            return null;
        }
        ShareOpenGraphAction.Builder builder = new ShareOpenGraphAction.Builder();
        builder.setActionType(action.getActionType());
        Map<String, Object> map = action.getMap();
        Set<String> keys = map.keySet();
        Object obj;
        for (String key : keys) {
            obj = map.get(key);
            if(null == obj) {
                continue;
            }
            if(obj instanceof Boolean) {
                builder.putBoolean(key, (boolean) obj);
            } else if(obj instanceof Boolean []) {
                builder.putBooleanArray(key, (boolean[]) obj);
            } else if(obj instanceof Double) {
                builder.putDouble(key, (double) obj);
            } else if(obj instanceof Double []) {
                builder.putDoubleArray(key, (double []) obj);
            } else if(obj instanceof Integer) {
                builder.putInt(key, (int) obj);
            } else if(obj instanceof Integer []) {
                builder.putIntArray(key, (int[]) obj);
            } else if(obj instanceof Long) {
                builder.putLong(key, (long) obj);
            } else if(obj instanceof Long []) {
                builder.putLongArray(key, (long[]) obj);
            } else if(obj instanceof WAShareOpenGraphObject) {
                builder.putObject(key, getFBOpenGraphObject((WAShareOpenGraphObject) obj));
            } else if(obj instanceof WASharePhoto) {
                builder.putPhoto(key, getFBSharePhoto((WASharePhoto) obj));
            } else if(obj instanceof String) {
                builder.putString(key, (String) obj);
            } else if(obj instanceof ArrayList) {
                ArrayList list = (ArrayList) obj;
                if(list.isEmpty()) {
                    continue;
                }
                Object item = list.get(0);
                if(item instanceof WAShareOpenGraphObject) {
                    builder.putObjectArrayList(key,
                            getFBShareOpenGraphObjectList((ArrayList<WAShareOpenGraphObject>) obj));
                } else if(item instanceof  WASharePhoto) {
                    builder.putPhotoArrayList(key, getFBSharePhotoList((ArrayList<WASharePhoto>) obj));
                } else if(item instanceof String) {
                    builder.putStringArrayList(key, (ArrayList<String>) obj);
                }
            }
        }
        return builder.build();
    }

    /**
     * 将{@linkplain WAShareOpenGraphObject}对象转换成Facebook的{@linkplain ShareOpenGraphObject}对象
     * @param object
     * @return
     */
    private ShareOpenGraphObject getFBOpenGraphObject(WAShareOpenGraphObject object) {
        ShareOpenGraphObject.Builder builder = new ShareOpenGraphObject.Builder();
        Map<String, Object> map = object.getMap();
        Set<String> keys = map.keySet();
        Object obj;
        for (String key : keys) {
            obj = map.get(key);
            if(null == obj) {
                continue;
            }
            if(obj instanceof Boolean) {
                builder.putBoolean(key, (boolean) obj);
            } else if(obj instanceof Boolean []) {
                builder.putBooleanArray(key, (boolean[]) obj);
            } else if(obj instanceof Double) {
                builder.putDouble(key, (double) obj);
            } else if(obj instanceof Double []) {
                builder.putDoubleArray(key, (double []) obj);
            } else if(obj instanceof Integer) {
                builder.putInt(key, (int) obj);
            } else if(obj instanceof Integer []) {
                builder.putIntArray(key, (int[]) obj);
            } else if(obj instanceof Long) {
                builder.putLong(key, (long) obj);
            } else if(obj instanceof Long []) {
                builder.putLongArray(key, (long[]) obj);
            } else if(obj instanceof WAShareOpenGraphObject) {
                builder.putObject(key, getFBOpenGraphObject((WAShareOpenGraphObject) obj));
            } else if(obj instanceof WASharePhoto) {
                builder.putPhoto(key, getFBSharePhoto((WASharePhoto) obj));
            } else if(obj instanceof String) {
                builder.putString(key, (String) obj);
            } else if(obj instanceof ArrayList) {
                ArrayList list = (ArrayList) obj;
                if(list.isEmpty()) {
                    continue;
                }
                Object item = list.get(0);
                if(item instanceof WAShareOpenGraphObject) {
                    builder.putObjectArrayList(key,
                            getFBShareOpenGraphObjectList((ArrayList<WAShareOpenGraphObject>) obj));
                } else if(item instanceof  WASharePhoto) {
                    builder.putPhotoArrayList(key, getFBSharePhotoList((ArrayList<WASharePhoto>) obj));
                } else if(item instanceof String) {
                    builder.putStringArrayList(key, (ArrayList<String>) obj);
                }
            }
        }
        return builder.build();
    }

    /**
     * 将{@linkplain WAShareOpenGraphObject}对象Array转换成Facebook的{@linkplain ShareOpenGraphObject}对象Array
     * @param objects
     * @return
     */
    private ArrayList<ShareOpenGraphObject> getFBShareOpenGraphObjectList(ArrayList<WAShareOpenGraphObject> objects) {
        if(null == objects || objects.isEmpty()) {
            return null;
        }
        ArrayList<ShareOpenGraphObject> fbObjects = new ArrayList<>();
        for (WAShareOpenGraphObject object : objects) {
            fbObjects.add(getFBOpenGraphObject(object));
        }
        return fbObjects;
    }

    /**
     * 将{@linkplain WASharePhoto}对象转换成Facebook的{@linkplain SharePhoto}
     * @param photo
     * @return
     */
    private SharePhoto getFBSharePhoto(WASharePhoto photo) {
        if(null == photo) {
            return null;
        }
        return new SharePhoto.Builder()
                .setBitmap(photo.getBitmap())
                .setImageUrl(photo.getImageUri())
                .setUserGenerated(photo.getUserGenerated())
                .setCaption(photo.getCaption())
                .build();
    }

    /**
     * 将{@linkplain WASharePhoto}对象Array转换成Facebook的{@linkplain SharePhoto} Array
     * @param photos
     * @return
     */
    private ArrayList<SharePhoto> getFBSharePhotoList(ArrayList<WASharePhoto> photos) {
        if(null == photos || photos.isEmpty()) {
            return null;
        }
        ArrayList<SharePhoto> sharePhotos = new ArrayList<>();
        for (WASharePhoto photo : photos) {
            sharePhotos.add(getFBSharePhoto(photo));
        }
        return sharePhotos;
    }

    protected enum PendingAction {
        NONE,
        SHARE_LINK,
        SHARE_PHOTO,
        SHARE_VIDEO,
        SHARE_OPEN_GRAPH;
    }
}
