package androidx.media3.common.audio;

// FongMi 私改 media 里有此类的实现，未随公开分支发布（2026-11-23 全分支检索证实）。
// 这里按锚点 125468e0 的调用面重写同签名版本：常量语义沿用 FongMi
// AudioSetting 的用法（AUTO 自动降级、STEREO/MONO/REVERSE 显式指定）。
public final class AudioChannelMode {

    public static final int AUTO = 0;
    public static final int STEREO = 1;
    public static final int MONO = 2;
    public static final int REVERSE = 3;

    private AudioChannelMode() {}

    public static int clamp(int mode) {
        return Math.max(AUTO, Math.min(REVERSE, mode));
    }

    public static boolean isAuto(int mode) {
        return mode == AUTO;
    }

    public static boolean canBalance(int mode) {
        return resolve(mode, 2) == STEREO;
    }

    public static boolean isAvailable(int mode, int channelCount) {
        if (channelCount < 2) return false;
        if (isAuto(mode)) return true;
        return mode == STEREO || mode == MONO || mode == REVERSE;
    }

    public static int resolve(int mode, int channelCount) {
        if (channelCount < 2) return MONO;
        return isAuto(mode) ? STEREO : mode;
    }
}
