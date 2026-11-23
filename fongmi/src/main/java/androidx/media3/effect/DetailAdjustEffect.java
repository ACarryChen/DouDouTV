package androidx.media3.effect;

import androidx.media3.common.Effect;

// FongMi 私改 media 的细节增强（锐化/暗部提升）效果，未随公开分支发布（2026-11-23 全分支检索证实）。
// 编译级降级：参数收下不挂 GL 管线，见 VideoAdjustment 同款说明。
public final class DetailAdjustEffect implements Effect {

    private VideoAdjustment adjustment;

    public DetailAdjustEffect setAdjustment(VideoAdjustment adjustment) {
        this.adjustment = adjustment;
        return this;
    }

    public VideoAdjustment getAdjustment() {
        return adjustment;
    }

    @Override
    public long getDurationAfterEffectApplied(long durationUs) {
        return durationUs;
    }
}
