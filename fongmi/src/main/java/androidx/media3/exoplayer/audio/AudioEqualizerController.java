package androidx.media3.exoplayer.audio;

import android.content.Context;

import androidx.media3.common.audio.AudioEffectParameters;

// FongMi 私改 media 里的 EXO 均衡器控制器，未随公开分支发布（2026-11-23 全分支检索证实）。
// 编译级降级：apply 恒返 true（FongMi 语义里 false 表示硬件均衡器挂载失败需回退软件路径），
// 释放为空操作。软件均衡器（AudioEffectProcessor 降级版）同样不生效。
public final class AudioEqualizerController {

    public AudioEqualizerController(Context context) {
    }

    public boolean apply(androidx.media3.exoplayer.ExoPlayer player, AudioEffectParameters parameters) {
        return true;
    }

    public void release() {
    }
}
