package com.wa.sdk.fb.social.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Facebook请求分页
 * Created by yinglovezhuzhu@gmail.com on 2016/3/23.
 */
public class WAFBPaging implements Parcelable {
    private String before; // 请求上一页的cursor
    private String after; // 请求下一页的cursor
    private String previous; // 请求上一页的地址，可能没有，没有就代表没有上一页
    private String next; // 请求下一页的地址，可能没有，没有就代表没有下一页

    public WAFBPaging() {

    }

    private WAFBPaging(Parcel source) {
        this.before = source.readString();
        this.after = source.readString();
        this.previous = source.readString();
        this.next = source.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(before);
        dest.writeString(after);
        dest.writeString(previous);
        dest.writeString(next);
    }

    public static final Creator<WAFBPaging> CREATOR = new Creator<WAFBPaging>() {
        @Override
        public WAFBPaging createFromParcel(Parcel source) {
            return new WAFBPaging(source);
        }

        @Override
        public WAFBPaging[] newArray(int size) {
            return new WAFBPaging[size];
        }
    };

    public String getBefore() {
        return before;
    }

    public void setBefore(String before) {
        this.before = before;
    }

    public String getAfter() {
        return after;
    }

    public void setAfter(String after) {
        this.after = after;
    }

    public String getPrevious() {
        return previous;
    }

    public void setPrevious(String previous) {
        this.previous = previous;
    }

    public String getNext() {
        return next;
    }

    public void setNext(String next) {
        this.next = next;
    }

    public boolean hasPrevious() {
        return null != previous && !"".equals(previous);
    }

    public boolean hasNext() {
        return null != next && !"".equals(next);
    }

    @Override
    public String toString() {
        return "WAFBPaging{" +
                "before='" + before + '\'' +
                ", after='" + after + '\'' +
                ", previous='" + previous + '\'' +
                ", next='" + next + '\'' +
                '}';
    }
}
