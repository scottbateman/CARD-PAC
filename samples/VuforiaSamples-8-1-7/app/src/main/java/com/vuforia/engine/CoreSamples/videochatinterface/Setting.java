package com.vuforia.engine.CoreSamples.videochatinterface;

import android.view.View;

public class Setting {

    private String settingTitle;
    private int value;
    private View.OnClickListener onClickListener;

    public final static int SETTING_LANGUAGE_ENGLISH = 0,
                             SETTING_LANGUAGE_FRENCH = 1;

    public Setting(String title, int defaultValue, View.OnClickListener onClickIn){
        settingTitle = title;
        value = defaultValue;
        onClickListener = onClickIn;
    }

    public String getTitle(){
        return settingTitle;
    }

    public int getValue(){
        return value;
    }

    public View.OnClickListener getOnClickListener(){
        return onClickListener;
    }

    public void setTitle(String title){
        settingTitle = title;
    }

    public void setValue(int valueIn){
        value = valueIn;
    }

    public void setOnClickListener(View.OnClickListener onClickIn){
        onClickListener = onClickIn;
    }

}
