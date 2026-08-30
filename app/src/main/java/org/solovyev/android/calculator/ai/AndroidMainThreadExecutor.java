package org.solovyev.android.calculator.ai;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;

/** Delivers Nova AI callbacks on Android's main thread. */
public final class AndroidMainThreadExecutor implements Executor {

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void execute(Runnable command) {
        if (command == null) throw new NullPointerException("command");
        if (Looper.myLooper() == Looper.getMainLooper()) {
            command.run();
        } else {
            handler.post(command);
        }
    }
}
