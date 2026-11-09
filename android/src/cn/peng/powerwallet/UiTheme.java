package cn.peng.powerwallet;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

/** Light/dark palette shared by the phone UI. */
final class UiTheme {
    static final String PREFS="powerwallet_ui", KEY="theme";
    static final String SYSTEM="system", DARK="dark", LIGHT="light";
    private UiTheme() {}

    static String mode(Context context) {
        String value=SYSTEM;
        try {value=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY,SYSTEM);}
        catch(Exception ignored) {}
        if(DARK.equals(value) || LIGHT.equals(value) || SYSTEM.equals(value)) return value;
        return SYSTEM;
    }
    static void setMode(Context context,String value) {
        try {context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY,value).apply();}
        catch(Exception ignored) {}
    }
    static boolean isDark(Context context) {
        String mode=mode(context);
        if(DARK.equals(mode)) return true;
        if(LIGHT.equals(mode)) return false;
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
    }
    static Palette palette(Context context) {return isDark(context)?Palette.DARK:Palette.LIGHT;}

    static final class Palette {
        static final Palette LIGHT=new Palette(
            "#F3F6F1","#FFFFFF","#EAF0E8","#19382B","#617669","#D6E0D5",
            "#225B3D","#FFFFFF","#E0EDDF","#96601E","#B3261E","#1F225B3D");
        static final Palette DARK=new Palette(
            "#101816","#18231F","#24342D","#F0F6F1","#A2B7AA","#33453B",
            "#B1E5C5","#123324","#243F30","#F0C281","#FFB4AB","#33B1E5C5");
        final int bg,card,control,ink,muted,line,accent,accentInk,soft,warn,danger,ripple;
        private Palette(String bg,String card,String control,String ink,String muted,String line,
                        String accent,String accentInk,String soft,String warn,String danger,String ripple) {
            this.bg=Color.parseColor(bg); this.card=Color.parseColor(card); this.control=Color.parseColor(control);
            this.ink=Color.parseColor(ink); this.muted=Color.parseColor(muted); this.line=Color.parseColor(line);
            this.accent=Color.parseColor(accent); this.accentInk=Color.parseColor(accentInk); this.soft=Color.parseColor(soft);
            this.warn=Color.parseColor(warn); this.danger=Color.parseColor(danger); this.ripple=Color.parseColor(ripple);
        }
    }
}
