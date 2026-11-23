package androidx.media3.effect;

import androidx.media3.common.Effect;

// FongMi 私改 media 里的视频微调效果（未随公开分支发布，2026-11-23 全分支检索证实）。
// EXO-only 阶段编译级降级：VideoAdjustment 参数全部收下但不挂 GL 管线，
// getDurationAfterEffectApplied 报 0 表示无处理延迟；画质档位效果等 FongMi
// 上游公开 media 补丁后重对齐。
public final class VideoAdjustment implements Effect {

    private final float saturation;
    private final float contrast;
    private final float brightness;
    private final float gamma;
    private final float hue;
    private final float redGain;
    private final float blueGain;
    private final float sharpness;
    private final float threshold;
    private final float shadowLift;

    private VideoAdjustment(Builder builder) {
        this.saturation = builder.saturation;
        this.contrast = builder.contrast;
        this.brightness = builder.brightness;
        this.gamma = builder.gamma;
        this.hue = builder.hue;
        this.redGain = builder.redGain;
        this.blueGain = builder.blueGain;
        this.sharpness = builder.sharpness;
        this.threshold = builder.threshold;
        this.shadowLift = builder.shadowLift;
    }

    @Override
    public long getDurationAfterEffectApplied(long durationUs) {
        return durationUs;
    }

    public float getSaturation() {
        return saturation;
    }

    public float getContrast() {
        return contrast;
    }

    public float getBrightness() {
        return brightness;
    }

    public float getGamma() {
        return gamma;
    }

    public float getHue() {
        return hue;
    }

    public float getRedGain() {
        return redGain;
    }

    public float getBlueGain() {
        return blueGain;
    }

    public float getSharpness() {
        return sharpness;
    }

    public float getThreshold() {
        return threshold;
    }

    public float getShadowLift() {
        return shadowLift;
    }

    public static final class Builder {

        private float saturation = 1.0f;
        private float contrast = 1.0f;
        private float brightness;
        private float gamma = 1.0f;
        private float hue;
        private float redGain = 1.0f;
        private float blueGain = 1.0f;
        private float sharpness;
        private float threshold;
        private float shadowLift;

        public Builder setSaturation(float saturation) {
            this.saturation = saturation;
            return this;
        }

        public Builder setContrast(float contrast) {
            this.contrast = contrast;
            return this;
        }

        public Builder setBrightness(float brightness) {
            this.brightness = brightness;
            return this;
        }

        public Builder setGamma(float gamma) {
            this.gamma = gamma;
            return this;
        }

        public Builder setHue(float hue) {
            this.hue = hue;
            return this;
        }

        public Builder setRedGain(float redGain) {
            this.redGain = redGain;
            return this;
        }

        public Builder setBlueGain(float blueGain) {
            this.blueGain = blueGain;
            return this;
        }

        public Builder setSharpness(float sharpness) {
            this.sharpness = sharpness;
            return this;
        }

        public Builder setThreshold(float threshold) {
            this.threshold = threshold;
            return this;
        }

        public Builder setShadowLift(float shadowLift) {
            this.shadowLift = shadowLift;
            return this;
        }

        public VideoAdjustment build() {
            return new VideoAdjustment(this);
        }
    }
}
