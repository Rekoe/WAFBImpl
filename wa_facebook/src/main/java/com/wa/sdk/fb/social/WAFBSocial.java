package com.wa.sdk.fb.social;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.facebook.share.model.GameRequestContent;
import com.wa.sdk.common.model.WACallback;
import com.wa.sdk.common.model.WAResult;
import com.wa.sdk.fb.WAFacebook;
import com.wa.sdk.fb.social.gifting.WAFBGifting;
import com.wa.sdk.fb.social.invite.WAFBAppInvite;
import com.wa.sdk.fb.social.invite.WAFBGameInvite;
import com.wa.sdk.fb.social.invite.WAFBInviteReward;
import com.wa.sdk.fb.social.share.WAFBShare;
import com.wa.sdk.social.WAISocial;
import com.wa.sdk.social.model.WAFBGameRequestResult;
import com.wa.sdk.social.model.WAFBGraphObjectResult;
import com.wa.sdk.social.model.WAFriendsResult;
import com.wa.sdk.social.model.WAGiftingResult;
import com.wa.sdk.social.model.WAInviteResult;
import com.wa.sdk.social.model.WAShareContent;
import com.wa.sdk.social.model.WAShareResult;

import java.util.Collection;
import java.util.List;

/**
 * Facebook社交实现
 * Created by yinglovezhuzhu@gmail.com on 16-3-18.
 */
public class WAFBSocial implements WAISocial {

    @Override
    public void share(Activity activity, WAShareContent shareContent, boolean shareWithApi,
                      String extInfo, WACallback<WAShareResult> callback) {
        WAFBShare.share(activity, shareContent, shareWithApi, callback);
    }

    @Override
    public void appInvite(Activity activity, String appLinkUrl, String previewImageUrl,
                          WACallback<WAResult> callback) {
        WAFBAppInvite.newInstance().appInvite(activity, appLinkUrl, previewImageUrl, callback);
    }

    @Override
    public void queryInvitableFriends(Activity activity, long duration, WACallback<WAFriendsResult> callback) {
        WAFBFriends.newInstance().queryInvitableFriends(activity, duration, callback);
    }

    @Override
    public void gameInvite(Activity activity, String title, String message, List<String> ids,
                           WACallback<WAInviteResult> callback) {
        WAFBGameInvite.newInstance().gameInvite(activity, title, message, ids, callback);
    }

    @Override
    public void createInviteRecord(Context context, String requestId, Collection<String> recipients,
                                 WACallback<WAResult> callback) {
        WAFBInviteReward.getInstance().createInviteRecord(context, requestId, recipients, callback);
    }

    @Override
    public void inviteInstallReward(Activity activity, WACallback<WAResult> callback) {
        WAFBInviteReward.getInstance().inviteInstallReward(activity, callback);
    }

    @Override
    public void inviteEventReward(Activity activity, String eventName, WACallback<WAResult> callback) {
        WAFBInviteReward.getInstance().inviteEventReward(activity, eventName, callback);
    }

    @Override
    public void queryFriends(Activity activity, WACallback<WAFriendsResult> callback) {
        WAFBFriends.newInstance().queryFriends(activity, callback);
    }

    @Override
    public void queryFBGraphObjects(Activity activity, String objectType, WACallback<WAFBGraphObjectResult> callback) {
        WAFBGraphObjects.newInstance().queryFBObjects(activity, objectType, callback);
    }

    @Override
    public void fbSendGift(Activity activity, String title, String message, String objectId,
                           List<String> receipts, WACallback<WAGiftingResult> callback) {
        WAFBGifting.newInstance().giftingRequest(activity, title, message,
                GameRequestContent.ActionType.SEND, objectId, receipts, callback);
    }

    @Override
    public void fbAskForGift(Activity activity, String title, String message, String objectId,
                             List<String> receipts, WACallback<WAGiftingResult> callback) {
        WAFBGifting.newInstance().giftingRequest(activity, title, message,
                GameRequestContent.ActionType.ASKFOR, objectId, receipts, callback);
    }

    @Override
    public void fbQueryReceivedGifts(Activity activity, WACallback<WAFBGameRequestResult> callback) {
        WAFBGameRequest.newInstance().queryRequests(activity,
                GameRequestContent.ActionType.SEND.name(), callback);
    }

    @Override
    public void fbQueryAskForGiftRequests(Activity activity, WACallback<WAFBGameRequestResult> callback) {
        WAFBGameRequest.newInstance().queryRequests(activity,
                GameRequestContent.ActionType.ASKFOR.name(), callback);
    }

    @Override
    public void fbDeleteRequest(Activity activity, String requestId, WACallback<WAResult> callback) {
        WAFBGameRequest.newInstance().deleteRequest(requestId, callback);
    }


    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        return WAFacebook.onActivityResult(requestCode, resultCode, data);
    }
}
