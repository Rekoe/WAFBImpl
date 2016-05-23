package com.wa.sdk.fb.social.share.internal;

/**
 * 判断数据有效性的类
 * Created by ghw_zhangyy on 2015/6/5.
 */
public final class Validate {


    public static void notNull(Object arg, String name) {
        if (arg == null) {
            throw new NullPointerException("Argument '" + name + "' cannot be null");
        }
    }

}
