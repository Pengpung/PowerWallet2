package cn.peng.powerwallet;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/** Double-tap vibration choice, fixed at build time. */
final class Haptics {
    static final String DEFAULT="tick";
    static final String[] VALUES={"double_click","click","tick","short","double_short","off"};
    private Haptics() {}
    static boolean isValid(String value) {
        for(String candidate:VALUES) if(candidate.equals(value)) return true;
        return false;
    }
    private static Vibrator vibrator(Context context) {
        if(Build.VERSION.SDK_INT>=31) {
            VibratorManager manager=(VibratorManager)context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            if(manager!=null) return manager.getDefaultVibrator();
        }
        return (Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
    }
    static void play(Context context,String value) {
        Vibrator vibrator=vibrator(context);
        if(vibrator==null || !vibrator.hasVibrator() || "off".equals(value)) return;
        VibrationEffect effect;
        if(Build.VERSION.SDK_INT>=29) {
            switch(value) {
                case "double_click": effect=VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK); break;
                case "click": effect=VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK); break;
                case "tick": effect=VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK); break;
                case "double_short": effect=VibrationEffect.createWaveform(new long[]{0,22,45,22},-1); break;
                case "short":
                default: effect=VibrationEffect.createOneShot(35,VibrationEffect.DEFAULT_AMPLITUDE); break;
            }
        } else if("double_short".equals(value)) {
            effect=VibrationEffect.createWaveform(new long[]{0,22,45,22},-1);
        } else {
            effect=VibrationEffect.createOneShot(35,VibrationEffect.DEFAULT_AMPLITUDE);
        }
        try {vibrator.vibrate(effect);} catch(Exception ignored) {}
    }
}
