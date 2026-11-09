package cn.peng.powerwallet;

import android.content.ContentProvider;
import android.app.KeyguardManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;

/** Only ADB shell can deliver the narrowly scoped status/stop Binder. */
public final class BridgeProvider extends ContentProvider {
    public static volatile IBinder helper;
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        if(Binder.getCallingUid()!=2000) throw new SecurityException("Shell only");
        if("keyguard".equals(method)) {
            KeyguardManager km=getContext().getSystemService(KeyguardManager.class);
            PowerManager pm=getContext().getSystemService(PowerManager.class);
            Bundle state=new Bundle();
            state.putBoolean("showing",km.isKeyguardLocked());
            state.putBoolean("deviceLocked",km.isDeviceLocked());
            state.putBoolean("interactive",pm!=null && pm.isInteractive());
            return state;
        }
        if("vibrate".equals(method)) {
            Haptics.play(getContext(),arg!=null && Haptics.isValid(arg)?arg:Haptics.DEFAULT);
            Bundle response=new Bundle(); response.putBoolean("ok",true); return response;
        }
        if(!"bind".equals(method) || extras==null) throw new IllegalArgumentException();
        IBinder candidate=extras.getBinder("helper");
        if(candidate!=null && (helper==null || !helper.isBinderAlive())) helper=candidate;
        Bundle response=new Bundle(); response.putBoolean("ok",helper!=null && helper.isBinderAlive()); return response;
    }
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o) {return null;}
    @Override public String getType(Uri u) {return null;}
    @Override public Uri insert(Uri u,ContentValues v) {throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a) {throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a) {throw new UnsupportedOperationException();}
}
