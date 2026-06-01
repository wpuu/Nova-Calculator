package org.solovyev.android.calculator;

public class SecretCodeEvent {
    public enum CodeType {
        PHOTO,
        VIDEO_START,
        VIDEO_STOP,
        AUDIO_START,
        AUDIO_STOP,
        SETTINGS,
        CLEAR_EDITOR
    }

    public final CodeType type;

    public SecretCodeEvent(CodeType type) {
        this.type = type;
    }
}
