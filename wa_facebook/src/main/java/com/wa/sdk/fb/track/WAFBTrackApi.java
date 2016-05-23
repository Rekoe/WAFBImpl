package com.wa.sdk.fb.track;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.facebook.FacebookSdk;
import com.facebook.appevents.AppEventsConstants;
import com.facebook.appevents.AppEventsLogger;
import com.wa.sdk.WAConstants;
import com.wa.sdk.common.WACommonProxy;
import com.wa.sdk.common.utils.LogUtil;
import com.wa.sdk.common.utils.StringUtil;
import com.wa.sdk.common.utils.WAUtil;
import com.wa.sdk.fb.WAFBConstants;
import com.wa.sdk.track.WAEventParameterName;
import com.wa.sdk.track.WAEventType;
import com.wa.sdk.track.model.WAEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Facebook事件统计中转类
 * Created by ghw_zhangyy on 2015/8/25.
 */
public class WAFBTrackApi {

    private Map<String, String> mEventTypeMap = new HashMap<>();

    private Map<String, String> mParameterNameMap = new HashMap<>();

    private AppEventsLogger mEventLogger;

    private static WAFBTrackApi mInstance;

    private boolean mEnabled = true;

    private boolean mInitialized = false;

    public static WAFBTrackApi getInstance() {
        if(null == mInstance) {
            mInstance = new WAFBTrackApi();
        }
        return mInstance;
    }

    private WAFBTrackApi() {
        initEventTypeMap();
        initParameterNameMap();
    }

    public void startHeartBeat(Context context) {
        AppEventsLogger.activateApp(context);
    }

    public void stopHeartBeat(Context context) {
        AppEventsLogger.deactivateApp(context);
    }

    /**
     * 初始化AppsFlyer
     * @param context
     */
    public synchronized void initialize(Context context) {
        if(mInitialized) {
            if(mEnabled) {
                mEventLogger.logEvent(AppEventsConstants.EVENT_NAME_ACTIVATED_APP);
                WACommonProxy.logEvent(WAConstants.CHANNEL_FACEBOOK,
                        AppEventsConstants.EVENT_NAME_ACTIVATED_APP, System.currentTimeMillis(), "");
            }
            return;
        }
        Bundle metaData = WAUtil.getMataDatasFromManifest(context);
        if(null == metaData) {
            mEnabled = false;
            return;
        }

        mEnabled = metaData.getBoolean(WAFBConstants.META_KEY_TRACK_ENABLE_FACEBOOK, true);
        if(mEnabled) {
            FacebookSdk.sdkInitialize(context.getApplicationContext());
            mEventLogger = AppEventsLogger.newLogger(context.getApplicationContext());
                WACommonProxy.logEvent(WAConstants.CHANNEL_FACEBOOK,
                        AppEventsConstants.EVENT_NAME_ACTIVATED_APP, System.currentTimeMillis(), "");
        }

        mInitialized = true;
    }

    /**
     *
     * @param context
     * @param eventName
     * @param value
     * @param eventValues
     * @deprecated 从3.3.1开始停止使用
     * @see #trackEvent(Context, WAEvent)
     */
    public void trackEvent(Context context, String eventName, float value, Map<String, Object> eventValues) {
        if(!mInitialized || !FacebookSdk.isInitialized()) {
            Log.d(WAFBConstants.TAG, "FacebookTrack--Facebook uninitialized or disabled");
            return;
        }

        if(!mEnabled) {
            Log.d(WAFBConstants.TAG, "FacebookTrack--Facebook tracking was disabled");
            return;
        }

        Bundle bundle = new Bundle();
        double valueToSum = value;
        if(mEventTypeMap.containsKey(eventName)) {
            String fbEventName = mEventTypeMap.get(eventName);
            if(null != eventValues && !eventValues.isEmpty()) {
                Set<String> eventKeys = eventValues.keySet();
                for(String key : eventKeys) {
                    if(mParameterNameMap.containsKey(key)) {
                        if(WAEventParameterName.PRICE.equals(key)) {
                            if(value <= 0) {
                                try {
                                    valueToSum = Double.valueOf(String.valueOf(eventValues.get(key)));
                                } catch (NumberFormatException e) {
                                    LogUtil.e(WAFBConstants.TAG, "FacebookTrack--" + LogUtil.getStackTrace(e));
                                }
                            }
                            continue;
                        }
                        String fbParamName = mParameterNameMap.get(key);
                        if(AppEventsConstants.EVENT_PARAM_SUCCESS.equals(fbParamName)) {
                            Object obj = eventValues.get(key);
                            if(obj instanceof Boolean) {
                                bundle.putString(fbParamName, (Boolean) obj ? "1" : "0");
                            } else {
                                bundle.putString(fbParamName, "1");
                            }
                        } else {
                            bundle.putString(mParameterNameMap.get(key), String.valueOf(eventValues.get(key)));
                        }
                    }
                }
            }
            mEventLogger.logEvent(fbEventName, valueToSum, bundle);
            WACommonProxy.logEvent(WAConstants.CHANNEL_FACEBOOK, fbEventName, System.currentTimeMillis(),
                    "valueToSum：" + valueToSum + "\n" + bundle.toString());
        } else {
            // PCL 中和Facebook没有对应的eventName的时候，那么在Facebook中当作是自定义事件，eventName
            // 和eventValues中的参数都是直接采用PCL的定义
            if(null != eventValues && !eventValues.isEmpty()) {
                Set<String> eventKeys = eventValues.keySet();
                for(String key : eventKeys) {
                    if(mParameterNameMap.containsKey(key)) {
                        if(WAEventParameterName.PRICE.equals(key)) {
                            if(value <= 0) {
                                try {
                                    valueToSum = Double.valueOf(String.valueOf(eventValues.get(key)));
                                } catch (NumberFormatException e) {
                                    LogUtil.e(WAFBConstants.TAG, "FacebookTrack--" + LogUtil.getStackTrace(e));
                                }
                            }
                            continue;
                        }
                        String fbParamName = mParameterNameMap.get(key);
                        if(AppEventsConstants.EVENT_PARAM_SUCCESS.equals(fbParamName)) {
                            Object obj = eventValues.get(key);
                            if(obj instanceof Boolean) {
                                bundle.putString(fbParamName, (Boolean) obj ? "1" : "0");
                            } else {
                                bundle.putString(fbParamName, "1");
                            }
                        } else {
                            bundle.putString(mParameterNameMap.get(key), String.valueOf(eventValues.get(key)));
                        }
                    } else {
                        bundle.putString(key, String.valueOf(eventValues.get(key)));
                    }
                }
            }
            mEventLogger.logEvent(eventName, valueToSum, bundle);
            WACommonProxy.logEvent(WAConstants.CHANNEL_FACEBOOK, eventName, System.currentTimeMillis(),
                    "valueToSum：" + valueToSum + "\n" + bundle.toString());
        }
    }

    /**
     * 数据收集入口
     * @param context
     * @param event
     * @since 3.3.1
     */
    public void trackEvent(Context context, WAEvent event) {
        if(!mInitialized || !FacebookSdk.isInitialized()) {
            Log.d(WAFBConstants.TAG, "FacebookTrack--Facebook uninitialized or disabled");
            return;
        }

        if(!mEnabled) {
            Log.d(WAFBConstants.TAG, "FacebookTrack--Facebook tracking was disabled");
            return;
        }

        if(!event.isChannelEnabled(WAConstants.CHANNEL_FACEBOOK)) {
            // 事件设置禁用
            Log.d(WAFBConstants.TAG, "FacebookTrack--Facebook tracking was disabled by event");
            return;
        }

        String eventName = event.getChannelEventName(WAConstants.CHANNEL_FACEBOOK);
        if(StringUtil.isEmpty(eventName)) {
            eventName = event.getDefaultEventName();
        }
        if(StringUtil.isEmpty(eventName)) {
            // 事件名称为空
            Log.e(WAFBConstants.TAG, "FacebookTrack--Facebook tracking error: eventName was null");
            return;
        }

        Bundle bundle = new Bundle();
        double valueToSum = event.getChannelValue(WAConstants.CHANNEL_FACEBOOK);
        String fbEventName;
        if(event.isDefaultEventName(WAConstants.CHANNEL_FACEBOOK)) {
            // 事件名称是默认值（非渠道自定义）
            if(mEventTypeMap.containsKey(eventName)) {
                // AppsFlyer事件名称和默认（PCL）事件名称有映射，还原到AppsFlayer事件名称
                fbEventName = mEventTypeMap.get(eventName);

                fbEventValuesMap(event, bundle);
            } else {
                // AppsFlyer事件名称和默认（PCL）事件名称没有映射，直接使用默认（PCL）事件名称
                fbEventName = eventName;

                fbEventValuesMap(event, bundle);
            }
        } else {
            // 非默认（用户自定义渠道名称），直接采用用户自定义
            fbEventName = eventName;

            fbEventValuesMap(event, bundle);
        }

        mEventLogger.logEvent(eventName, valueToSum, bundle);
        WACommonProxy.logEvent(WAConstants.CHANNEL_FACEBOOK, fbEventName, System.currentTimeMillis(),
                "valueToSum：" + valueToSum + "\n" + bundle.toString());
    }

    /**
     * 获取Facebook的参数表（如果有必要，进行默认（PCL）和AppsFlyer的映射转换）
     * @param event
     * @param bundle
     */
    private void fbEventValuesMap(WAEvent event, Bundle bundle) {
        Map<String, Object> eventValues = event.getChannelEventValues(WAConstants.CHANNEL_FACEBOOK);
        if(null == eventValues) {
            // 没有渠道自定参数，那么事件参数将使用默认的
            eventValues = event.getDefaultEventValues();
            // 采用默认的参数（PCL），那么将会进行和AppsFlyer的参数进行一次mapping映射
            if(null != eventValues) {
                Set<String> eventKeys = eventValues.keySet();
                for(String key : eventKeys) {
                    if(mParameterNameMap.containsKey(key)) {
                        String fbParamName = mParameterNameMap.get(key);
                        // Facebook中没有布尔值，只有整型表示。
                        if(AppEventsConstants.EVENT_PARAM_SUCCESS.equals(fbParamName)) {
                            Object obj = eventValues.get(key);
                            if(obj instanceof Boolean) {
                                bundle.putString(fbParamName, (Boolean) obj ? "1" : "0");
                            } else {
                                bundle.putString(fbParamName, "1");
                            }
                        } else {
                            bundle.putString(mParameterNameMap.get(key), String.valueOf(eventValues.get(key)));
                        }
                    } else {
                        bundle.putString(key, String.valueOf(eventValues.get(key)));
                    }
                }
            }
        } else {
            Set<String> eventKeys = eventValues.keySet();
            for(String key : eventKeys) {
                bundle.putString(key, String.valueOf(eventValues.get(key)));
            }
        }

    }


    /**
     * 初始化PCL事件名称与AppsFlyer的对应关系，注释掉的说明在AppsFlyer中没有找到
     */
    private void initEventTypeMap() {

        mEventTypeMap.put(WAEventType.STARTUP, AppEventsConstants.EVENT_NAME_ACTIVATED_APP);
        mEventTypeMap.put(WAEventType.COMPLETE_REGISTRATION, AppEventsConstants.EVENT_NAME_COMPLETED_REGISTRATION);
        mEventTypeMap.put(WAEventType.INITIATED_PAYMENT, AppEventsConstants.EVENT_NAME_INITIATED_CHECKOUT);
        mEventTypeMap.put(WAEventType.COMPLETE_PAYMENT, AppEventsConstants.EVENT_NAME_PURCHASED);
        mEventTypeMap.put(WAEventType.LEVEL_ACHIEVED, AppEventsConstants.EVENT_NAME_ACHIEVED_LEVEL);
        mEventTypeMap.put(WAEventType.ADD_TO_CART, AppEventsConstants.EVENT_NAME_ADDED_TO_CART);
        mEventTypeMap.put(WAEventType.ADD_TO_WISH_LIST, AppEventsConstants.EVENT_NAME_ADDED_TO_WISHLIST);
        mEventTypeMap.put(WAEventType.SEARCH, AppEventsConstants.EVENT_NAME_SEARCHED);
        mEventTypeMap.put(WAEventType.SPENT_CREDITS, AppEventsConstants.EVENT_NAME_SPENT_CREDITS);
        mEventTypeMap.put(WAEventType.ACHIEVEMENT_UNLOCKED, AppEventsConstants.EVENT_NAME_UNLOCKED_ACHIEVEMENT);
        mEventTypeMap.put(WAEventType.CONTENT_VIEW, AppEventsConstants.EVENT_NAME_VIEWED_CONTENT);
    }

    /**
     * 初始化PCL参数名称与AppsFlyer的对应关系，注释掉的说明在AppsFlyer中没有找到
     */
    private void initParameterNameMap() {

        mParameterNameMap.put(WAEventParameterName.SUCCESS, AppEventsConstants.EVENT_PARAM_SUCCESS);
        mParameterNameMap.put(WAEventParameterName.LEVEL, AppEventsConstants.EVENT_PARAM_LEVEL);
        mParameterNameMap.put(WAEventParameterName.CURRENCY_TYPE, AppEventsConstants.EVENT_PARAM_CURRENCY);
        mParameterNameMap.put(WAEventParameterName.ITEM_NAME, AppEventsConstants.EVENT_PARAM_CONTENT_TYPE);
        mParameterNameMap.put(WAEventParameterName.CONTENT_TYPE, AppEventsConstants.EVENT_PARAM_CONTENT_TYPE);
        mParameterNameMap.put(WAEventParameterName.CONTENT_ID, AppEventsConstants.EVENT_PARAM_CONTENT_ID);
        mParameterNameMap.put(WAEventParameterName.QUANTITY, AppEventsConstants.EVENT_PARAM_NUM_ITEMS);
        mParameterNameMap.put(WAEventParameterName.SEARCH_STRING, AppEventsConstants.EVENT_PARAM_SEARCH_STRING);
        mParameterNameMap.put(WAEventParameterName.DESCRIPTION, AppEventsConstants.EVENT_PARAM_DESCRIPTION);
        mParameterNameMap.put(WAEventParameterName.CURRENCY_TYPE, AppEventsConstants.EVENT_PARAM_CURRENCY);
        mParameterNameMap.put(WAEventParameterName.IAP_NAME, AppEventsConstants.EVENT_PARAM_CONTENT_TYPE);
        mParameterNameMap.put(WAEventParameterName.IAP_ID, AppEventsConstants.EVENT_PARAM_CONTENT_ID);
        mParameterNameMap.put(WAEventParameterName.IAP_AMOUNT, AppEventsConstants.EVENT_PARAM_NUM_ITEMS);
    }
}
