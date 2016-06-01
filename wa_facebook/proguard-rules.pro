# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in D:\Android\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-keep class * extends android.app.Activity

#-keep class com.facebook.**
-keep class com.facebook.FacebookContentProvider

-keep class com.wa.sdk.fb.core.WAFBCore {
    public *;
}
-keep class com.wa.sdk.fb.core.WAFBTool {
    public *;
}
-keep class com.wa.sdk.fb.social.WAFBSocial {
    public *;
}
-keep class com.wa.sdk.fb.track.WAFBTrack {
    public *;
}
-keep class com.wa.sdk.fb.user.WAFBUser {
    public *;
}