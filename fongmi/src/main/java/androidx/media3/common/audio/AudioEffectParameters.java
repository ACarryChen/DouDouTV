package androidx.media3.common.audio;

import androidx.annotation.Nullable;

// FongMi 私改 media 里的效果参数载体，未随公开分支发布（2026-11-23 全分支检索证实）。
// 按锚点 125468e0 的调用面重写：EXO 路径走 AudioEffectProcessor 降级 no-op，
// MPV 二期 MpvAudioEffectFilter 才真正消费这里的数值。
public final class AudioEffectParameters {

    public static final AudioEffectParameters DEFAULT = new Builder().build();

    private final int[] equalizerFrequencies;
    private final short[] equalizerLevels;
    private final float stability;
    private final int boostMillibels;
    private final int preampMillibels;
    private final int centerGainMillibels;
    private final float balance;
    private final int channelMode;
    private final boolean loudnessEnabled;

    private AudioEffectParameters(Builder builder) {
        this.equalizerFrequencies = builder.equalizerFrequencies;
        this.equalizerLevels = builder.equalizerLevels;
        this.stability = builder.stability;
        this.boostMillibels = builder.boostMillibels;
        this.preampMillibels = builder.preampMillibels;
        this.centerGainMillibels = builder.centerGainMillibels;
        this.balance = builder.balance;
        this.channelMode = builder.channelMode;
        this.loudnessEnabled = builder.loudnessEnabled;
    }

    public static boolean isCenterGainAvailable(int channelCount) {
        return channelCount >= 6;
    }

    public boolean hasBands() {
        return equalizerLevels.length > 0;
    }

    public boolean hasEffect() {
        return hasBands() || stability > 0 || boostMillibels != 0 || preampMillibels != 0
                || hasCenterGain(6) || balance != 0 || channelMode != AudioChannelMode.AUTO || loudnessEnabled;
    }

    public boolean hasCenterGain(int channelCount) {
        return isCenterGainAvailable(channelCount) && centerGainMillibels != 0;
    }

    public float getCenterGainFactor() {
        return (float) Math.pow(10, centerGainMillibels / 2000.0);
    }

    public int[] getEqualizerFrequencies() {
        return equalizerFrequencies;
    }

    public short[] getEqualizerLevelsMillibels() {
        return equalizerLevels;
    }

    public float getStability() {
        return stability;
    }

    public int getBoostMillibels() {
        return boostMillibels;
    }

    public int getPreampMillibels() {
        return preampMillibels;
    }

    public float getBalance() {
        return balance;
    }

    public int getChannelMode() {
        return channelMode;
    }

    public boolean isLoudnessEnabled() {
        return loudnessEnabled;
    }

    public boolean shouldLimitOutput(int channelCount) {
        return hasCenterGain(channelCount) || boostMillibels > 0 || preampMillibels > 0;
    }

    public static final class Builder {

        private int[] equalizerFrequencies = new int[0];
        private short[] equalizerLevels = new short[0];
        private float stability;
        private int boostMillibels;
        private int preampMillibels;
        private int centerGainMillibels;
        private float balance;
        private int channelMode = AudioChannelMode.AUTO;
        private boolean loudnessEnabled;

        public Builder setEqualizerBands(int[] frequencies, short[] levels) {
            this.equalizerFrequencies = frequencies;
            this.equalizerLevels = levels;
            return this;
        }

        public Builder setStability(float stability) {
            this.stability = stability;
            return this;
        }

        public Builder setBoostMillibels(int boostMillibels) {
            this.boostMillibels = boostMillibels;
            return this;
        }

        public Builder setPreampMillibels(int preampMillibels) {
            this.preampMillibels = preampMillibels;
            return this;
        }

        public Builder setCenterGainMillibels(int centerGainMillibels) {
            this.centerGainMillibels = centerGainMillibels;
            return this;
        }

        public Builder setBalance(float balance) {
            this.balance = balance;
            return this;
        }

        public Builder setChannelMode(int channelMode) {
            this.channelMode = channelMode;
            return this;
        }

        public Builder setLoudnessEnabled(boolean loudnessEnabled) {
            this.loudnessEnabled = loudnessEnabled;
            return this;
        }

        public AudioEffectParameters build() {
            return new AudioEffectParameters(this);
        }
    }
}
