package cn.peng.powerwallet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.*;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    public static final String SOCKET="cn.peng.powerwallet.v1";
    public static final String WALLET="com.google.android.apps.walletnfcrel";
    private static final int SHIZUKU_REQUEST=100;

    private UiTheme.Palette p;
    private ScrollView page;
    private LinearLayout themeGroup,statusCard,infoCard,shizukuCard;
    private TextView chipSystem,chipDark,chipLight;
    private ImageView logo;
    private TextView brandName,eyebrow,title,subtitle;
    private TextView statusPill,statusTitle,statusDetail,refreshLink;
    private TextView openButton,refreshButton,restoreButton;
    private TextView shizukuTitle,shizukuStatus,shizukuButton,shizukuHint;
    private TextView infoTitle,steps,footer,keepWarning;
    private ProgressBar progress;
    private int statusTone;
    private boolean busy;
    private final Shizuku.OnRequestPermissionResultListener shizukuListener=(code,result)->{
        if(code!=SHIZUKU_REQUEST) return;
        runOnUiThread(() -> {
            refreshShizuku();
            if(result==PackageManager.PERMISSION_GRANTED) activateWithShizuku();
        });
    };

    @Override protected void onCreate(Bundle saved) {
        setTheme(UiTheme.isDark(this)?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(saved);
        p=UiTheme.palette(this);
        buildUi();
        applyWindow();
        ShizukuBridge.addPermissionListener(shizukuListener);
        refreshShizuku();
    }
    @Override protected void onResume() {super.onResume();refreshShizuku();request("STATUS");}
    @Override protected void onDestroy() {
        ShizukuBridge.removePermissionListener(shizukuListener);
        super.onDestroy();
    }
    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if(UiTheme.SYSTEM.equals(UiTheme.mode(this))) applyTheme();
    }

    private void buildUi() {
        page=new ScrollView(this);
        page.setFillViewport(true);
        page.setBackgroundColor(p.bg);
        page.setVerticalScrollBarEnabled(true);
        page.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22),dp(18),dp(22),dp(30));
        page.addView(root,new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.WRAP_CONTENT));
        setContentView(page);

        LinearLayout header=new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout brand=new LinearLayout(this);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        logo=new ImageView(this);
        int powerIcon=getResources().getIdentifier("ic_power","drawable",getPackageName());
        logo.setImageResource(powerIcon!=0?powerIcon:android.R.drawable.ic_lock_power_off);
        logo.setImageTintList(ColorStateList.valueOf(p.accent));
        brand.addView(logo,new LinearLayout.LayoutParams(dp(26),dp(26)));
        brandName=text("PowerWallet",22,p.ink,true);
        LinearLayout.LayoutParams nameLp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        nameLp.leftMargin=dp(8);
        brand.addView(brandName,nameLp);
        header.addView(brand,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));
        header.addView(themeControl(),new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(header);

        eyebrow=text("POWER × 2  →  GOOGLE WALLET",11,p.muted,true);
        eyebrow.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams eyeLp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        eyeLp.topMargin=dp(26);
        root.addView(eyebrow,eyeLp);

        title=text("电源键，换个用法。",30,p.ink,true);
        title.setLineSpacing(dp(2),1f);
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin=dp(8);
        root.addView(title,titleLp);

        subtitle=text("双击电源键，直达 Google 钱包刷卡页面。",15,p.muted,false);
        subtitle.setLineSpacing(dp(3),1f);
        LinearLayout.LayoutParams subLp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin=dp(8);subLp.bottomMargin=dp(20);
        root.addView(subtitle,subLp);

        statusCard=new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(20),dp(18),dp(20),dp(20));
        statusCard.setBackground(shape(p.card,20,dp(1),p.line));
        root.addView(statusCard,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout statusTop=new LinearLayout(this);
        statusTop.setOrientation(LinearLayout.HORIZONTAL);
        statusTop.setGravity(Gravity.CENTER_VERTICAL);
        statusPill=text("正在检查",11,p.accent,true);
        statusPill.setPadding(dp(10),dp(5),dp(10),dp(5));
        statusTop.addView(statusPill);
        statusTop.addView(new View(this),new LinearLayout.LayoutParams(0,1,1f));
        refreshLink=text("刷新",12,p.accent,true);
        refreshLink.setPadding(dp(8),dp(4),dp(0),dp(4));
        refreshLink.setClickable(true);refreshLink.setFocusable(true);
        refreshLink.setOnClickListener(v->request("STATUS"));
        statusTop.addView(refreshLink);
        statusCard.addView(statusTop);

        statusTitle=text("正在读取助手状态",22,p.ink,true);
        statusTitle.setLineSpacing(dp(2),1f);
        LinearLayout.LayoutParams stLp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        stLp.topMargin=dp(12);
        statusCard.addView(statusTitle,stLp);

        statusDetail=text("请稍候…",14,p.muted,false);
        statusDetail.setLineSpacing(dp(3),1f);
        LinearLayout.LayoutParams sdLp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        sdLp.topMargin=dp(6);
        statusCard.addView(statusDetail,sdLp);

        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(ColorStateList.valueOf(p.accent));
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progLp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(3));
        progLp.topMargin=dp(14);
        statusCard.addView(progress,progLp);

        openButton=action("打开 Google 钱包",16,p.accent,p.accentInk,16,false,true);
        LinearLayout.LayoutParams openLp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(54));
        openLp.topMargin=dp(16);
        root.addView(openButton,openLp);
        openButton.setOnClickListener(v->openWallet());

        LinearLayout secondary=new LinearLayout(this);
        secondary.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams secLp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        secLp.topMargin=dp(10);
        root.addView(secondary,secLp);
        refreshButton=action("刷新状态",14,p.control,p.ink,14,true,false);
        restoreButton=action("停用并恢复",14,p.control,p.warn,14,true,false);
        LinearLayout.LayoutParams leftLp=new LinearLayout.LayoutParams(0,dp(48),1f);leftLp.rightMargin=dp(5);
        LinearLayout.LayoutParams rightLp=new LinearLayout.LayoutParams(0,dp(48),1f);rightLp.leftMargin=dp(5);
        secondary.addView(refreshButton,leftLp);
        secondary.addView(restoreButton,rightLp);
        refreshButton.setOnClickListener(v->request("STATUS"));
        restoreButton.setOnClickListener(v->confirmRestore());

        shizukuCard=new LinearLayout(this);
        shizukuCard.setOrientation(LinearLayout.VERTICAL);
        shizukuCard.setPadding(dp(18),dp(16),dp(18),dp(18));
        shizukuCard.setBackground(shape(p.card,18,dp(1),p.line));
        LinearLayout.LayoutParams shizukuLp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        shizukuLp.topMargin=dp(14);
        root.addView(shizukuCard,shizukuLp);
        shizukuTitle=text("Shizuku 激活",15,p.ink,true);
        shizukuCard.addView(shizukuTitle);
        shizukuStatus=text("正在检查…",12,p.muted,false);
        LinearLayout.LayoutParams shizukuStatusLp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        shizukuStatusLp.topMargin=dp(4);
        shizukuCard.addView(shizukuStatus,shizukuStatusLp);
        shizukuButton=action("用 Shizuku 激活",14,p.soft,p.accent,14,false,true);
        LinearLayout.LayoutParams shizukuBtnLp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(46));
        shizukuBtnLp.topMargin=dp(10);
        shizukuCard.addView(shizukuButton,shizukuBtnLp);
        shizukuButton.setOnClickListener(v->activateWithShizuku());
        shizukuHint=text("无需电脑。首次使用需在 Shizuku 中允许本应用；Shizuku 需保持运行。",12,p.muted,false);
        shizukuHint.setLineSpacing(dp(2),1f);
        LinearLayout.LayoutParams hintLp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin=dp(8);
        shizukuCard.addView(shizukuHint,hintLp);

        infoCard=new LinearLayout(this);
        infoCard.setOrientation(LinearLayout.VERTICAL);
        infoCard.setPadding(dp(18),dp(17),dp(18),dp(18));
        infoCard.setBackground(shape(p.card,18,dp(1),p.line));
        LinearLayout.LayoutParams infoLp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        infoLp.topMargin=dp(18);
        root.addView(infoCard,infoLp);
        infoTitle=text("使用说明",15,p.ink,true);
        infoCard.addView(infoTitle);
        steps=text("① 锁屏双击电源键，完成指纹/人脸/密码验证后自动打开 Google 钱包。\n\n② 解锁状态下双击直接打开 Wallet。\n\n③ 手机重启或助手失效后，点上方“用 Shizuku 激活”即可恢复，无需电脑；也可连接电脑重新激活。\n\n④ 卸载前请先点“停用并恢复原快捷键”。",13,p.muted,false);
        steps.setLineSpacing(dp(4),1f);
        LinearLayout.LayoutParams stepsLp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        stepsLp.topMargin=dp(10);
        infoCard.addView(steps,stepsLp);

        keepWarning=text("建议保持 Shizuku 运行。关闭开发者选项后系统可能清理助手；若双击失效，用 Shizuku 重新激活即可，无需电脑。",13,p.warn,false);
        keepWarning.setLineSpacing(dp(4),1f);
        LinearLayout.LayoutParams keepLp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        keepLp.topMargin=dp(12);
        infoCard.addView(keepWarning,keepLp);

        footer=text("PowerWallet "+versionName()+" · 双击轻触震动",11,p.muted,false);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerLp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        footerLp.topMargin=dp(18);
        root.addView(footer,footerLp);
        styleStatusPill();
    }

    private View themeControl() {
        themeGroup=new LinearLayout(this);
        themeGroup.setOrientation(LinearLayout.HORIZONTAL);
        themeGroup.setGravity(Gravity.CENTER_VERTICAL);
        themeGroup.setPadding(dp(4),dp(3),dp(4),dp(3));
        chipSystem=themeChip("系统",UiTheme.SYSTEM);
        chipDark=themeChip("深色",UiTheme.DARK);
        chipLight=themeChip("浅色",UiTheme.LIGHT);
        themeGroup.addView(chipSystem);
        themeGroup.addView(chipDark);
        themeGroup.addView(chipLight);
        styleThemeChips();
        return themeGroup;
    }
    private TextView themeChip(String label,String value) {
        TextView chip=text(label,12,p.muted,false);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(11),dp(7),dp(11),dp(7));
        chip.setClickable(true);chip.setFocusable(true);
        chip.setOnClickListener(v->selectTheme(value));
        return chip;
    }
    private void selectTheme(String value) {
        if(value.equals(UiTheme.mode(this))) return;
        UiTheme.setMode(this,value);
        applyTheme();
    }
    private void styleThemeChips() {
        if(themeGroup==null) return;
        themeGroup.setBackground(shape(p.control,999,0,0));
        String mode=UiTheme.mode(this);
        styleChip(chipSystem,UiTheme.SYSTEM.equals(mode));
        styleChip(chipDark,UiTheme.DARK.equals(mode));
        styleChip(chipLight,UiTheme.LIGHT.equals(mode));
    }
    private void styleChip(TextView chip,boolean selected) {
        chip.setTextColor(selected?p.accent:p.muted);
        chip.setTypeface(Typeface.DEFAULT,selected?Typeface.BOLD:Typeface.NORMAL);
        chip.setBackground(selected?shape(p.soft,999,0,0):null);
    }

    private void applyTheme() {
        if(page==null) return;
        p=UiTheme.palette(this);
        page.setBackgroundColor(p.bg);
        brandName.setTextColor(p.ink);
        logo.setImageTintList(ColorStateList.valueOf(p.accent));
        eyebrow.setTextColor(p.muted);
        title.setTextColor(p.ink);
        subtitle.setTextColor(p.muted);
        statusCard.setBackground(shape(p.card,20,dp(1),p.line));
        refreshLink.setTextColor(p.accent);
        statusTitle.setTextColor(p.ink);
        statusDetail.setTextColor(p.muted);
        progress.setIndeterminateTintList(ColorStateList.valueOf(p.accent));
        openButton.setTextColor(p.accentInk);
        openButton.setBackground(ripple(p.accent,16,0,p.line));
        refreshButton.setTextColor(p.ink);
        refreshButton.setBackground(ripple(p.control,14,dp(1),p.line));
        restoreButton.setTextColor(p.warn);
        restoreButton.setBackground(ripple(p.control,14,dp(1),p.line));
        infoCard.setBackground(shape(p.card,18,dp(1),p.line));
        infoTitle.setTextColor(p.ink);
        steps.setTextColor(p.muted);
        footer.setTextColor(p.muted);
        if(keepWarning!=null) keepWarning.setTextColor(p.warn);
        if(shizukuCard!=null) {
            shizukuCard.setBackground(shape(p.card,18,dp(1),p.line));
            shizukuTitle.setTextColor(p.ink);
            shizukuHint.setTextColor(p.muted);
            refreshShizuku();
        }
        styleThemeChips();
        styleStatusPill();
        applyWindowBars();
        setBusy(busy);
    }
    private void styleStatusPill() {
        int bg,fg;
        if(statusTone==1) {bg=p.soft;fg=p.accent;}
        else if(statusTone==2) {bg=p.control;fg=p.muted;}
        else if(statusTone==3) {bg=p.soft;fg=p.warn;}
        else {bg=p.soft;fg=p.accent;}
        statusPill.setTextColor(fg);
        statusPill.setBackground(shape(bg,999,0,0));
    }

    private void applyWindow() {
        applyWindowBars();
        if(Build.VERSION.SDK_INT>=30) {
            page.setOnApplyWindowInsetsListener((v,insets)->{
                Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                v.setPadding(bars.left,bars.top,bars.right,bars.bottom);
                return insets;
            });
            page.requestApplyInsets();
        }
    }
    private void applyWindowBars() {
        getWindow().setBackgroundDrawable(new ColorDrawable(p.bg));
        if(Build.VERSION.SDK_INT>=30) {
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
            WindowInsetsController controller=getWindow().getInsetsController();
            if(controller!=null) {
                int light=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(UiTheme.isDark(this)?0:light,light);
            }
        } else {
            getWindow().setStatusBarColor(p.bg);
            getWindow().setNavigationBarColor(p.bg);
            int flags=UiTheme.isDark(this)?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    private TextView text(String value,float size,int color,boolean bold) {
        TextView t=new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setLineSpacing(dp(2),1f);
        t.setTypeface(Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL);
        return t;
    }
    private TextView action(String label,int sizeSp,int bg,int fg,int radiusDp,boolean border,boolean bold) {
        TextView v=text(label,sizeSp,fg,bold);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(16),dp(10),dp(16),dp(10));
        v.setBackground(ripple(bg,radiusDp,border?dp(1):0,p.line));
        v.setClickable(true);v.setFocusable(true);
        return v;
    }
    private GradientDrawable shape(int color,int radiusDp,int strokePx,int strokeColor) {
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if(strokePx>0) d.setStroke(strokePx,strokeColor);
        return d;
    }
    private Drawable ripple(int color,int radiusDp,int strokePx,int strokeColor) {
        GradientDrawable content=shape(color,radiusDp,strokePx,strokeColor);
        return new RippleDrawable(ColorStateList.valueOf(p.ripple),content,content);
    }
    private int dp(int value) {return Math.round(value*getResources().getDisplayMetrics().density);}
    private String versionName() {
        try {return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}
        catch(Exception e) {return "2.0.0";}
    }

    private void refreshShizuku() {
        if(shizukuStatus==null) return;
        boolean running=ShizukuBridge.available();
        boolean granted=running && ShizukuBridge.authorized();
        if(!running) {
            shizukuStatus.setText("Shizuku 未运行；启动后可无需电脑激活");
            shizukuStatus.setTextColor(p.muted);
        } else if(!granted) {
            shizukuStatus.setText("已连接；首次使用需要授权");
            shizukuStatus.setTextColor(p.warn);
        } else {
            shizukuStatus.setText("已连接并授权，可在手机上激活");
            shizukuStatus.setTextColor(p.accent);
        }
        shizukuButton.setText(granted?"用 Shizuku 重新激活":"用 Shizuku 激活");
        shizukuButton.setTextColor(p.accent);
        shizukuButton.setBackground(ripple(p.soft,14,0,0));
        shizukuButton.setEnabled(!busy);
        shizukuButton.setAlpha(busy?0.5f:1f);
    }
    private void activateWithShizuku() {
        if(busy) return;
        if(!ShizukuBridge.available()) {
            Toast.makeText(this,"请先启动 Shizuku",Toast.LENGTH_SHORT).show();
            openShizuku();
            return;
        }
        if(!ShizukuBridge.authorized()) {
            ShizukuBridge.requestPermission(SHIZUKU_REQUEST);
            Toast.makeText(this,"请在 Shizuku 中允许“电源键钱包”",Toast.LENGTH_LONG).show();
            return;
        }
        busy=true;
        setBusy(true);
        new Thread(() -> {
            String result;
            try {result=ShizukuBridge.activate(ShizukuBridge.readOwnApk(this));}
            catch(Exception e) {result="未激活\n"+e.getMessage();}
            final String value=result;
            runOnUiThread(() -> {busy=false;setBusy(false);refreshShizuku();showStatus(value);});
        },"shizuku-activate").start();
    }
    private void openShizuku() {
        Intent intent=getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
        if(intent==null) {Toast.makeText(this,"请先安装并启动 Shizuku",Toast.LENGTH_SHORT).show();return;}
        try {startActivity(intent);} catch(Exception e) {Toast.makeText(this,"请手动打开 Shizuku",Toast.LENGTH_SHORT).show();}
    }

    private void request(final String command) {
        if(busy) return;
        busy=true;
        setBusy(true);
        new Thread(() -> {
            String reply;
            try {
                IBinder helper=BridgeProvider.helper;
                if(helper==null || !helper.isBinderAlive()) throw new IOException();
                Parcel data=Parcel.obtain(),response=Parcel.obtain();
                try {
                    data.writeInterfaceToken("cn.peng.powerwallet.Control");
                    if(!helper.transact("STOP".equals(command)?2:1,data,response,0)) throw new IOException();
                    response.readException();reply=response.readString();
                    if(reply==null) throw new IOException();
                } finally {data.recycle();response.recycle();}
            } catch(Exception e) {
                reply=null;
                if(ShizukuBridge.available() && ShizukuBridge.authorized()) {
                    String value="STOP".equals(command)?ShizukuBridge.restore():ShizukuBridge.status();
                    if(value!=null) {
                        value=value.trim();
                        if(value.startsWith("已激活") || value.startsWith("快捷键已改变") || value.startsWith("已停用")) reply=value;
                    }
                }
                if(reply==null) reply="未激活\n助手未运行；可用下方 Shizuku 激活，或连接电脑激活。";
            }
            final String result=reply;
            runOnUiThread(() -> {busy=false;setBusy(false);showStatus(result);});
        },"wallet-status").start();
    }
    private void setBusy(boolean value) {
        if(progress==null) return;
        progress.setVisibility(value?View.VISIBLE:View.GONE);
        refreshLink.setEnabled(!value);
        refreshButton.setEnabled(!value);
        restoreButton.setEnabled(!value);
        refreshLink.setAlpha(value?0.45f:1f);
        refreshButton.setAlpha(value?0.5f:1f);
        restoreButton.setAlpha(value?0.5f:1f);
        if(shizukuButton!=null) {shizukuButton.setEnabled(!value);shizukuButton.setAlpha(value?0.5f:1f);}
    }
    private void showStatus(String reply) {
        String first=reply==null?"":reply.trim(),second="";
        int pipe=first.indexOf('|');
        if(pipe>=0) {second=first.substring(pipe+1).trim();first=first.substring(0,pipe).trim();}
        else {
            int nl=first.indexOf('\n');
            if(nl>=0) {second=first.substring(nl+1).trim();first=first.substring(0,nl).trim();}
        }
        boolean active=first.startsWith("已激活");
        boolean stopped=first.startsWith("已停用");
        boolean changed=first.startsWith("快捷键已改变");
        String pill,title,detail;
        if(active) {
            statusTone=1;
            pill=first;title="钱包快捷键已就绪";
            detail=second.isEmpty()?"助手正在运行，可以拔掉数据线。":second;
        } else if(stopped) {
            statusTone=2;
            boolean kept=first.contains("保留");
            pill="已停用";title=kept?"已停用，保留当前设置":"已恢复原快捷键";
            detail=second.isEmpty()?(kept?"你后来设置的快捷键未被覆盖。":"需要再次使用时，可用 Shizuku 或连接电脑重新激活。"):second;
        } else {
            statusTone=3;
            pill=changed?"需要重新激活":"未激活";
            title=changed?"快捷键已改变":"尚未激活";
            detail=second.isEmpty()?"请点“用 Shizuku 激活”，或连接电脑运行桌面工具重新激活。":second;
        }
        statusPill.setText(pill);
        statusTitle.setText(title);
        statusDetail.setText(detail);
        styleStatusPill();
    }
    private int dialogTheme() {
        return UiTheme.isDark(this)?android.R.style.Theme_Material_Dialog_Alert:android.R.style.Theme_Material_Light_Dialog_Alert;
    }
    private void confirmRestore() {
        AlertDialog dialog=new AlertDialog.Builder(this,dialogTheme())
            .setTitle("恢复原快捷键？")
            .setMessage("停止按键助手，并恢复激活前的双击电源键设置。")
            .setPositiveButton("恢复",(d,w)->request("STOP"))
            .setNegativeButton("取消",null)
            .create();
        dialog.setOnShowListener(x->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(p.accent);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(p.muted);
        });
        dialog.show();
    }
    private void openWallet() {
        try {startActivity(new Intent("com.google.android.apps.wallet.main.QUICKDRAW").setPackage(WALLET));}
        catch(Exception first) {
            try {
                Intent i=getPackageManager().getLaunchIntentForPackage(WALLET);
                if(i==null) throw first;
                startActivity(i);
            } catch(Exception e) {
                new AlertDialog.Builder(this,dialogTheme()).setMessage("无法打开 Google 钱包，请检查是否已经安装并启用。").setPositiveButton("知道了",null).show();
            }
        }
    }
}
