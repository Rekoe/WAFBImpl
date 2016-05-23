package com.wa.sdk.fb;

import com.wa.sdk.common.utils.LogUtil;
/**
 * Created by yinglovezhuzhu@gmail.com on 2016/3/2.
 */
public final class WAFBConstants {

    public static String TAG = LogUtil.TAG + "_" + WAFBVersion.SDK_VER;

    /** 提交Facebook邀请信息接口 **/
    public static final String URL_CREATE_INVITE = "/v1/create_invite.do";

    /** 第一次上报邀请，奖励邀请人 **/
    public static final String URL_INVITE_REWARD = "/v1/fb_invite_install.do";

    /** 根据事件奖励邀请人 **/
    public static final String URL_INVITE_EVENT_REWARD = "/v1/fb_invite_reward.do";

    /** 数据收集启用/禁用 Facebook **/
    public static final String META_KEY_TRACK_ENABLE_FACEBOOK = "com.wa.sdk.track.ENABLE_FACEBOOK";

}
