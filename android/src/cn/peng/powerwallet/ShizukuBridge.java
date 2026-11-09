package cn.peng.powerwallet;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import java.io.*;
import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuService;
import rikka.shizuku.Shizuku;

/** Phone-side activation through Shizuku's shell identity, no PC required. */
final class ShizukuBridge {
    private static final String JAR="/data/local/tmp/powerwallet-helper.jar";
    private static final String RUN="CLASSPATH="+JAR+" /system/bin/app_process /system/bin cn.peng.powerwallet.Helper ";
    private ShizukuBridge() {}

    static boolean available() {
        try {return Shizuku.pingBinder();}
        catch(Throwable e) {return false;}
    }
    static boolean authorized() {
        try {return Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED;}
        catch(Throwable e) {return false;}
    }
    static void requestPermission(int code) {
        try {Shizuku.requestPermission(code);}
        catch(Throwable ignored) {}
    }
    static void addPermissionListener(Shizuku.OnRequestPermissionResultListener listener) {
        try {Shizuku.addRequestPermissionResultListener(listener);}
        catch(Throwable ignored) {}
    }
    static void removePermissionListener(Shizuku.OnRequestPermissionResultListener listener) {
        try {Shizuku.removeRequestPermissionResultListener(listener);}
        catch(Throwable ignored) {}
    }
    static byte[] readOwnApk(Context context) throws IOException {
        try(InputStream in=new FileInputStream(context.getApplicationInfo().sourceDir);
            ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];int count;
            while((count=in.read(buffer))!=-1) out.write(buffer,0,count);
            return out.toByteArray();
        }
    }
    private static IShizukuService service() {
        IBinder binder=Shizuku.getBinder();
        if(binder==null) throw new IllegalStateException("Shizuku 服务未运行");
        return IShizukuService.Stub.asInterface(binder);
    }
    private static String run(String command,byte[] stdin) throws Exception {
        IRemoteProcess remote=service().newProcess(new String[]{"sh","-c",command},null,null);
        ParcelFileDescriptor stdinFd=remote.getOutputStream();
        ParcelFileDescriptor stdoutFd=remote.getInputStream();
        ParcelFileDescriptor stderrFd=remote.getErrorStream();
        if(stdin!=null) {
            OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(stdinFd);
            out.write(stdin);out.close();
        } else {
            stdinFd.close();
        }
        ByteArrayOutputStream stdout=new ByteArrayOutputStream(),stderr=new ByteArrayOutputStream();
        Thread outThread=new Thread(()->drain(new ParcelFileDescriptor.AutoCloseInputStream(stdoutFd),stdout));
        Thread errThread=new Thread(()->drain(new ParcelFileDescriptor.AutoCloseInputStream(stderrFd),stderr));
        outThread.start();errThread.start();
        int code=remote.waitFor();
        outThread.join(3000);errThread.join(3000);
        String out=stdout.toString("UTF-8").trim(),err=stderr.toString("UTF-8").trim();
        if(out.isEmpty() && code!=0) return err.isEmpty()?"ERROR exit="+code:err;
        return out.isEmpty()?err:out;
    }
    private static void drain(InputStream in,ByteArrayOutputStream out) {
        try(InputStream input=in) {
            byte[] buffer=new byte[4096];int count;
            while((count=input.read(buffer))!=-1) out.write(buffer,0,count);
        } catch(IOException ignored) {}
    }

    static String activate(byte[] apk) {
        try {
            try {run("if [ -f "+JAR+" ]; then "+RUN+"--stop >/dev/null 2>&1; fi",null);}
            catch(Exception ignored) {}
            String launch="if [ -x /system/bin/setsid ]; then CLASSPATH="+JAR+" setsid nohup /system/bin/app_process /system/bin cn.peng.powerwallet.Helper </dev/null >>/data/local/tmp/powerwallet.log 2>&1 & else CLASSPATH="+JAR+" nohup /system/bin/app_process /system/bin cn.peng.powerwallet.Helper </dev/null >>/data/local/tmp/powerwallet.log 2>&1 & fi";
            String output=run("cat > "+JAR+" && chmod 600 "+JAR+" && "+launch,apk);
            for(int i=0;i<20;i++) {
                Thread.sleep(200);
                String value=status();
                if(value.startsWith("已激活")) return value;
            }
            return "未激活\n"+(output.isEmpty()?"助手未启动，请确认 Shizuku 已授权。":output);
        } catch(Exception e) {return "未激活\n"+e.getMessage();}
    }
    static String status() {
        try {return run(RUN+"--status",null);}
        catch(Exception e) {return "";}
    }
    static String restore() {
        try {return run(RUN+"--restore",null);}
        catch(Exception e) {return "";}
    }
    static String stop() {
        try {return run(RUN+"--stop",null);}
        catch(Exception e) {return "";}
    }
}
