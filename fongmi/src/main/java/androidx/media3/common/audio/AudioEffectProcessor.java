package androidx.media3.common.audio;

import androidx.annotation.Nullable;

import java.nio.ByteBuffer;

// FongMi 私改 media 里的效果处理器，未随公开分支发布（2026-11-23 全分支检索证实）。
// 这里是编译级降级：EXO-only 阶段不做均衡器/声道效果，configure 直通输入格式，
// 数据原样进出；MPV 二期的音频效果走 MpvAudioEffectFilter，不经此处理器。
public final class AudioEffectProcessor extends BaseAudioProcessor {

    @Nullable
    private AudioEffectParameters parameters;

    @Override
    protected AudioProcessor.AudioFormat onConfigure(AudioProcessor.AudioFormat inputAudioFormat) {
        return inputAudioFormat;
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        if (inputBuffer.hasRemaining()) replaceOutputBuffer(inputBuffer.remaining()).put(inputBuffer).flip();
    }

    public void setParameters(AudioEffectParameters parameters, boolean softwareEqualizer) {
        this.parameters = parameters;
    }

    public void resetParameters() {
        this.parameters = null;
    }

    @Nullable
    public AudioEffectParameters getParameters() {
        return parameters;
    }
}
