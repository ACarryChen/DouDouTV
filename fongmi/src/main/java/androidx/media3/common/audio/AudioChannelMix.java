package androidx.media3.common.audio;

// FongMi 私改 media 里的声道混合矩阵工厂，未随公开分支发布（2026-11-23 全分支检索证实）。
// 按锚点 125468e0 的调用面重写：矩阵布局按标准 WAVE/FFmpeg 顺序
// （FL, FR, FC, LFE, BL, BR, ...），EXO 路径不消费，MPV 二期经 PanMix 使用。
public final class AudioChannelMix {

    private AudioChannelMix() {}

    public static float[][] compose(float[][] first, float[][] second) {
        int rows = first.length;
        int cols = second.length > 0 ? second[0].length : 0;
        float[][] result = new float[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int k = 0; k < second.length; k++) {
                float scale = first[i][k];
                if (scale == 0) continue;
                for (int j = 0; j < cols; j++) result[i][j] += scale * second[k][j];
            }
        }
        return result;
    }

    public static float[][] createFrontCenterGainMix(int channelCount, float gain) {
        float[][] mix = identity(channelCount);
        if (channelCount >= 3) mix[0][2] = gain;
        return mix;
    }

    public static float[][] createStereoMix(int channelCount, boolean reverse) {
        float[][] mix = new float[channelCount][2];
        for (int channel = 0; channel < channelCount; channel++) {
            boolean left = channel % 2 == 0 != reverse;
            mix[channel][left ? 0 : 1] = 1.0f;
        }
        return mix;
    }

    public static float[][] createMonoMix(int channelCount) {
        float[][] mix = new float[channelCount][1];
        float weight = 1.0f / channelCount;
        for (int channel = 0; channel < channelCount; channel++) mix[channel][0] = weight;
        return mix;
    }

    public static float[][] createFrontBalanceMix(int channelCount, float balance) {
        float[][] mix = identity(channelCount);
        if (channelCount >= 2) {
            mix[0][0] = balance < 0 ? 1.0f : 1.0f - balance;
            mix[1][1] = balance < 0 ? 1.0f + balance : 1.0f;
        }
        return mix;
    }

    private static float[][] identity(int size) {
        float[][] mix = new float[size][size];
        for (int channel = 0; channel < size; channel++) mix[channel][channel] = 1.0f;
        return mix;
    }
}
