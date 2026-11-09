package cn.peng.powerwallet;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.content.AttributionSource;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Runs as Android's ADB shell identity; never inside the ordinary app process. */
public final class Helper {
    private static final String KEY="double_click_power_key", MARKER="google_pay";
    private static final File STATE=new File("/data/local/tmp/powerwallet-state.properties");
    private final PressDetector detector=new PressDetector();
    private volatile boolean running=true;
    private volatile String last="尚未触发";
    private volatile int launches=0;
    private int appUid;
    private String original, inputPath;
    private Process reader;
    private LocalServerSocket server;
    private Object activityManager;
    private volatile Object companionProvider;
    private volatile boolean watchingKeyguard;
    private final IBinder providerToken=new Binder();
    private final Binder control=new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if(code!=1 && code!=2) return super.onTransact(code,data,reply,flags);
            int caller=Binder.getCallingUid();
            if(caller!=appUid && caller!=2000) throw new SecurityException("Untrusted caller");
            data.enforceInterface("cn.peng.powerwallet.Control");
            try {
                String value=code==2 ? (restore()?"已停用，并恢复原快捷键":"已停用，保留当前新快捷键") : status();
                reply.writeNoException(); reply.writeString(value);
                if(code==2) timer.schedule(() -> shutdown(),100,TimeUnit.MILLISECONDS);
            } catch(Exception e) {reply.writeException(new IllegalStateException(e));}
            return true;
        }
    };
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();

    public static void main(String[] args) {
        try { execute(args); }
        catch(Exception failure) {
            System.err.println("ERROR "+failure.getClass().getSimpleName()+": "+failure.getMessage());
            System.exit(1);
        }
    }
    private static void execute(String[] args) throws Exception {
        if(args.length>0 && "--probe".equals(args[0])) {probe();return;}
        if(args.length>0 && ("--stop".equals(args[0]) || "--status".equals(args[0]) || "--restore".equals(args[0]))) {
            boolean restoring="--restore".equals(args[0]);
            try(LocalSocket s=new LocalSocket()) {
                s.connect(new LocalSocketAddress(MainActivity.SOCKET)); s.setSoTimeout(4000);
                s.getOutputStream().write(("--status".equals(args[0])?"STATUS\n":"STOP\n").getBytes(StandardCharsets.UTF_8));
                String reply=new BufferedReader(new InputStreamReader(s.getInputStream())).readLine();
                if(reply==null) throw new IOException("Helper closed without a response");
                System.out.println(reply);
            } catch(IOException unavailable) {
                if(!restoring) throw unavailable;
                if(!MARKER.equals(setting())) {System.out.println("已停用，保留当前快捷键");return;}
                Properties backup=new Properties();
                try(FileInputStream in=new FileInputStream(STATE)){backup.load(in);}
                String previous=backup.getProperty("original");
                if(previous==null) throw new IOException("No original shortcut backup; no changes made");
                putSetting(previous);
                System.out.println("已停用，并恢复原快捷键");
            }
            return;
        }
        Helper helper=new Helper();
        try { helper.run(); }
        catch(Exception failure) {
            if(helper.original!=null) try{helper.restore();}catch(Exception rollback){System.err.println("RESTORE_ERROR "+rollback);}
            helper.shutdown();
            throw failure;
        }
    }
    /** Read-only device compatibility report; never changes the shortcut setting. */
    private static void probe() throws Exception {
        if(android.os.Process.myUid()!=2000) throw new SecurityException("ADB shell required");
        String device=command("/system/bin/getprop","ro.product.device");
        String build=command("/system/bin/getprop","ro.build.version.incremental");
        String release=command("/system/bin/getprop","ro.build.version.release");
        String sdk=command("/system/bin/getprop","ro.build.version.sdk");
        boolean verified=("ishtar".equals(device) && "OS3.0.308.0.WMACNXM".equals(build)) ||
                         ("onyx".equals(device) && "OS3.0.306.0.WOLCNXM".equals(build));
        boolean hyperOs=build!=null && build.startsWith("OS");
        System.out.println("PROBE uid="+android.os.Process.myUid()+" device="+device+" build="+build+" android="+release+" sdk="+sdk+" verified="+verified+" hyperOs="+hyperOs);
        Helper helper=new Helper();
        helper.inputPath=helper.findPowerDevice();
        System.out.println("PROBE input="+helper.inputPath+" setting="+setting());
        Process events=new ProcessBuilder("/system/bin/getevent","-lt",helper.inputPath).redirectErrorStream(true).start();
        Thread.sleep(250);
        System.out.println("PROBE getevent_alive="+events.isAlive());
        events.destroy();
        try {
            helper.bindProvider();
            Bundle state=helper.keyguardState();
            System.out.println("PROBE keyguard showing="+state.getBoolean("showing")+" deviceLocked="+state.getBoolean("deviceLocked")+" interactive="+state.getBoolean("interactive"));
        } finally {helper.shutdown();}
        System.out.println("PROBE OK");
    }
    private static String command(String... args) throws IOException, InterruptedException {
        Process p=new ProcessBuilder(args).redirectErrorStream(true).start();
        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] b=new byte[4096]; int n;
        while((n=p.getInputStream().read(b))!=-1) out.write(b,0,n);
        int code=p.waitFor(); String value=out.toString("UTF-8").trim();
        if(code!=0) throw new IOException("Command failed: "+args[0]+" "+value);
        return value;
    }
    private static String setting() throws Exception {return command("/system/bin/settings","--user","0","get","system",KEY);}
    private static void putSetting(String value) throws Exception {
        if("null".equals(value)) command("/system/bin/settings","--user","0","delete","system",KEY);
        else command("/system/bin/settings","--user","0","put","system",KEY,value);
        if(!value.equals(setting())) throw new IOException("Shortcut setting did not persist");
    }
    private void run() throws Exception {
        if(android.os.Process.myUid()!=2000) throw new SecurityException("ADB activation required");
        String device=command("/system/bin/getprop","ro.product.device");
        String build=command("/system/bin/getprop","ro.build.version.incremental");
        boolean verified=("ishtar".equals(device) && "OS3.0.308.0.WMACNXM".equals(build)) ||
                         ("onyx".equals(device) && "OS3.0.306.0.WOLCNXM".equals(build));
        // Keep the original validated path, and allow other HyperOS builds to use
        // the same reversible mechanism. Non-HyperOS firmware is still refused.
        if(!verified && (build==null || !build.startsWith("OS")))
            throw new IOException("PowerWallet requires HyperOS; device="+device+" build="+build);
        System.out.println("DEVICE device="+device+" build="+build+(verified?" verified":" unverified-hyperos"));
        server=new LocalServerSocket(MainActivity.SOCKET); // Exclusive instance before any changes.
        String packages=command("/system/bin/cmd","package","list","packages","-U", "cn.peng.powerwallet");
        Matcher uid=Pattern.compile("package:cn\\.peng\\.powerwallet uid:(\\d+)").matcher(packages);
        if(!uid.find()) throw new IOException("Install the companion app first");
        appUid=Integer.parseInt(uid.group(1));
        inputPath=findPowerDevice();
        Properties props=new Properties();
        if(STATE.exists()) try(FileInputStream in=new FileInputStream(STATE)){props.load(in);}
        original=props.getProperty("original");
        if(original==null || !MARKER.equals(setting())) original=setting();
        props.setProperty("original",original); props.setProperty("device",inputPath);
        try(FileOutputStream out=new FileOutputStream(STATE)){props.store(out,"PowerWallet reversible shortcut backup");}
        STATE.setReadable(false,false); STATE.setReadable(true,true); STATE.setWritable(false,false); STATE.setWritable(true,true);
        reader=new ProcessBuilder("/system/bin/getevent","-lt",inputPath).redirectErrorStream(true).start();
        Thread.sleep(100);
        if(!reader.isAlive()) throw new IOException("Power-key input is unavailable");
        putSetting(MARKER); // This exact ROM implements this action as a no-op, consuming the original wallet shortcut.
        bindProvider();
        new Thread(() -> socketLoop(),"wallet-control").start();
        timer.scheduleAtFixedRate(() -> {
            if(running && detector.poll(SystemClock.uptimeMillis())) launch();
        },20,20,TimeUnit.MILLISECONDS);
        System.out.println("READY uid="+appUid+" input="+inputPath+" original="+original);
        Pattern event=Pattern.compile("\\[\\s*([0-9]+\\.[0-9]+)\\]\\s+(?:/dev/input/event\\d+:\\s+)?EV_KEY\\s+KEY_POWER\\s+(DOWN|UP)");
        try(BufferedReader in=new BufferedReader(new InputStreamReader(reader.getInputStream()))) {
            String line;
            while(running && (line=in.readLine())!=null) {
                Matcher m=event.matcher(line);
                if(m.find()) detector.event("DOWN".equals(m.group(2)),Math.round(Double.parseDouble(m.group(1))*1000));
            }
        } finally {
            if(running) restore();
            shutdown();
        }
    }
    private void bindProvider() throws Exception {
        Class<?> amClass=Class.forName("android.app.IActivityManager");
        activityManager=Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
        Object holder=amClass.getMethod("getContentProviderExternal",String.class,int.class,IBinder.class,String.class)
            .invoke(activityManager,"cn.peng.powerwallet.bridge",0,providerToken,"PowerWallet");
        if(holder==null) throw new IOException("Companion provider is unavailable");
        Object provider=holder.getClass().getField("provider").get(holder);
        companionProvider=provider;
        Bundle args=new Bundle(); args.putBinder("helper",control);
        AttributionSource attribution=new AttributionSource.Builder(2000).setPackageName("com.android.shell").build();
        Class<?> cp=Class.forName("android.content.IContentProvider");
        Object result=cp.getMethod("call",AttributionSource.class,String.class,String.class,String.class,Bundle.class)
            .invoke(provider,attribution,"cn.peng.powerwallet.bridge","bind",null,args);
        if(!(result instanceof Bundle) || !((Bundle)result).getBoolean("ok")) throw new IOException("Companion rejected the connection");
        IBinder providerBinder=(IBinder)cp.getMethod("asBinder").invoke(provider);
        providerBinder.linkToDeath(() -> {
            if(running) timer.schedule(() -> { try{bindProvider();}catch(Exception e){System.out.println("REBIND_ERROR "+e);} },1,TimeUnit.SECONDS);
        },0);
        System.out.println("BRIDGE_READY");
    }
    private String status() throws Exception {
        return (MARKER.equals(setting()) ? "已激活 · 数据线可拔出" : "快捷键已改变 · 请重新激活")+"|已触发 "+launches+" 次 · "+last;
    }
    private String findPowerDevice() throws Exception {
        String desc=command("/system/bin/getevent","-lp");
        Matcher devices=Pattern.compile("add device \\d+: (/dev/input/event\\d+)(.*?)(?=add device|\\z)",Pattern.DOTALL).matcher(desc);
        Pattern namePattern=Pattern.compile("name:\\s+\"([^\"]*)\"");
        String best=null, bestName="";
        int bestScore=-1;
        while(devices.find()) {
            String details=devices.group(2);
            if(!details.contains("KEY_POWER")) continue;
            Matcher named=namePattern.matcher(details);
            String name=named.find()?named.group(1):"";
            String lower=name.toLowerCase(Locale.ROOT);
            int score;
            if(lower.contains("bark") || lower.contains("goodix") || lower.contains("touch")) score=5;
            else if(name.equals("pmic_pwrkey")) score=100;
            else if(lower.contains("pwrkey") || lower.contains("powerkey") || lower.contains("power_key")) score=85;
            else if(lower.contains("qpnp_pon") || (lower.contains("pmic") && lower.contains("key"))) score=70;
            else if(lower.contains("gpio-keys") || lower.contains("gpio_keys")) score=60;
            else if(lower.contains("uinput") || lower.contains("virtual")) score=15;
            else score=45;
            if(score>bestScore) {bestScore=score;best=devices.group(1);bestName=name;}
        }
        if(best!=null && bestScore>=15) {
            System.out.println("INPUT_DEVICE "+best+" name="+bestName+" score="+bestScore);
            return best;
        }
        throw new IOException("No physical power key found");
    }
    private void launch() {
        try {
            if(!MARKER.equals(setting())) {last="快捷键已被系统或用户更改，请重新激活"; return;}
            long setupStart=SystemClock.uptimeMillis();
            vibrate();
            Bundle before=keyguardState();
            boolean secureLock=before.getBoolean("showing") && before.getBoolean("deviceLocked");
            if(!before.getBoolean("interactive")) {
                command("/system/bin/input","keyevent","KEYCODE_WAKEUP");
                before=keyguardState();
            }
            if(secureLock && before.getBoolean("showing") && before.getBoolean("deviceLocked")) {
                // Keep the lock screen and its fingerprint/face UI in front. Starting
                // Wallet behind a secure keyguard makes it flash and then disappear.
                // Wait for authentication, then bring Wallet up.
                last="等待解锁后打开钱包";
                watchUnlock();
                System.out.println("LOCKED_WAIT before="+stateText(before));
                return;
            }
            // Start Wallet as early as possible; keyguard handling happens while it loads.
            startWallet();
            long setupMs=SystemClock.uptimeMillis()-setupStart;
            boolean repaired=dismissAuthenticatedKeyguard(before);
            // Facial recognition can finish after the second press or while Wallet starts.
            // On fingerprint-only devices authentication can take a few seconds, so
            // keep watching the keyguard for up to ~4s before giving up.
            for(int attempt=0;attempt<20;attempt++) {
                Thread.sleep(200);
                Bundle current=keyguardState();
                if(!current.getBoolean("showing")) break;
                if(dismissAuthenticatedKeyguard(current)) {
                    startWallet(); repaired=true; break;
                }
            }
            launches++; last="已发送钱包启动请求";
            Bundle after=keyguardState();
            boolean waitForUnlock=after.getBoolean("showing") && after.getBoolean("deviceLocked");
            if(waitForUnlock) watchUnlock();
            System.out.println("LAUNCH "+launches+" before="+stateText(before)+" after="+stateText(after)+" dismiss="+repaired+" unlockWatch="+waitForUnlock+" setupMs="+setupMs);
        } catch(Exception e) {last="启动失败，请检查钱包及调试授权"; System.out.println("LAUNCH_ERROR "+e);}
    }
    /** Bring Wallet to the front once the user finishes unlocking a secure keyguard. */
    private void watchUnlock() {
        if(watchingKeyguard) return;
        watchingKeyguard=true;
        Thread watcher=new Thread(() -> {
            try {
                // Fingerprint/PIN can take several seconds after the double press.
                // Poll quickly so Wallet starts as soon as authentication succeeds.
                for(int attempt=0;attempt<300 && running;attempt++) {
                    Thread.sleep(50);
                    if(!running) break;
                    Bundle state=keyguardState();
                    if(!state.getBoolean("showing") || !state.getBoolean("deviceLocked")) {
                        // Start Wallet immediately; overlap the keyguard exit animation
                        // instead of waiting for the lock screen to fully disappear.
                        startWallet();
                        launches++; last="已发送钱包启动请求";
                        if(state.getBoolean("showing")) command("/system/bin/wm","dismiss-keyguard");
                        System.out.println("UNLOCK_LAUNCH showing="+state.getBoolean("showing")+" locked="+state.getBoolean("deviceLocked"));
                        break;
                    }
                }
            } catch(Exception e) {System.out.println("UNLOCK_WATCH_ERROR "+e);}
            finally {watchingKeyguard=false;}
        },"wallet-unlock-watch");
        watcher.start();
    }
    private void startWallet() throws Exception {
        // cmd activity starts from the native command binary and avoids the extra
        // app_process/JVM startup that `am start` performs. No -W: don't block on
        // the Wallet cold start before handling the keyguard.
        String result=command("/system/bin/cmd","activity","start-activity","--user","0","--activity-no-animation","-a","com.google.android.apps.wallet.main.QUICKDRAW","-p",MainActivity.WALLET);
        if(result.contains("Error:") || result.contains("Exception")) throw new IOException(result);
    }
    private Bundle keyguardState() throws Exception {
        AttributionSource attribution=new AttributionSource.Builder(2000).setPackageName("com.android.shell").build();
        return (Bundle)Class.forName("android.content.IContentProvider")
            .getMethod("call",AttributionSource.class,String.class,String.class,String.class,Bundle.class)
            .invoke(companionProvider,attribution,"cn.peng.powerwallet.bridge","keyguard",null,null);
    }
    private boolean dismissAuthenticatedKeyguard(Bundle state) throws Exception {
        if(!state.getBoolean("showing") || state.getBoolean("deviceLocked")) return false;
        command("/system/bin/wm","dismiss-keyguard");
        for(int i=0;i<10;i++) {
            Thread.sleep(35);
            if(!keyguardState().getBoolean("showing")) return true;
        }
        System.out.println("KEYGUARD_DISMISS_PENDING");
        return false;
    }
    private void vibrate() {
        if(companionProvider==null) return;
        try {
            AttributionSource attribution=new AttributionSource.Builder(2000).setPackageName("com.android.shell").build();
            Class.forName("android.content.IContentProvider")
                .getMethod("call",AttributionSource.class,String.class,String.class,String.class,Bundle.class)
                .invoke(companionProvider,attribution,"cn.peng.powerwallet.bridge","vibrate","auto",null);
        } catch(Exception e) {System.out.println("VIBRATE_ERROR "+e);}
    }
    private String stateText(Bundle state) {
        return "showing:"+state.getBoolean("showing")+",locked:"+state.getBoolean("deviceLocked")+",interactive:"+state.getBoolean("interactive");
    }
    private void socketLoop() {
        while(running) {
            try(LocalSocket socket=server.accept()) {
                socket.setSoTimeout(1500);
                int peer=socket.getPeerCredentials().getUid();
                if(peer!=appUid && peer!=2000) continue;
                String cmd=new BufferedReader(new InputStreamReader(socket.getInputStream())).readLine();
                String reply;
                if("STATUS".equals(cmd)) reply=status();
                else if("STOP".equals(cmd)) reply=restore() ? "已停用，并恢复原快捷键" : "已停用，保留当前新快捷键";
                else reply="Unsupported request";
                socket.getOutputStream().write((reply+"\n").getBytes(StandardCharsets.UTF_8));
                socket.getOutputStream().flush();
                if("STOP".equals(cmd)) {shutdown(); return;}
            } catch(Exception e) {if(running) System.out.println("CONTROL_ERROR "+e);}
        }
    }
    private synchronized boolean restore() throws Exception {
        if(!MARKER.equals(setting())) return false;
        putSetting(original); return true;
    }
    private void shutdown() {
        running=false; timer.shutdownNow();
        if(reader!=null) reader.destroy();
        try{server.close();}catch(Exception ignored){}
        try{Class.forName("android.app.IActivityManager").getMethod("removeContentProviderExternal",String.class,IBinder.class).invoke(activityManager,"cn.peng.powerwallet.bridge",providerToken);}catch(Exception ignored){}
    }
}
