/*
 * Copyright 2013 serso aka se.solovyev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.solovyev.android.calculator;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.squareup.otto.Bus;

import org.solovyev.android.calculator.ai.AiExplainCoordinator;
import org.solovyev.android.calculator.ai.AiGatewayClient;
import org.solovyev.android.calculator.ai.AiGatewayFeatureConfig;
import org.solovyev.android.calculator.ai.AiSessionTokenProvider;
import org.solovyev.android.calculator.ai.DisabledAiGatewayClient;
import org.solovyev.android.calculator.ai.DisabledInstallationProofProvider;
import org.solovyev.android.calculator.ai.HttpAiGatewayClient;
import org.solovyev.android.calculator.ai.InstallationProofProvider;
import org.solovyev.android.calculator.language.Languages;
import org.solovyev.android.calculator.wizard.CalculatorWizards;
import org.solovyev.android.plotter.Plot;
import org.solovyev.android.plotter.Plotter;
import org.solovyev.android.wizard.Wizards;

import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;
import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import jscl.JsclMathEngine;

@Module
public class AppModule {

    public static final String THREAD_INIT = "thread-init";
    public static final String THREAD_UI = "thread-ui";
    public static final String THREAD_BACKGROUND = "thread-background";
    public static final String DIR_FILES = "dir-files";
    public static final String PREFS_FLOATING = "prefs-floating";
    public static final String PREFS_TABS = "prefs-tabs";
    public static final String PREFS_UI = "prefs-ui";

    @NonNull
    private final Application application;
    @NonNull
    private final Languages languages;

    public AppModule(@NonNull Application application, @NonNull Languages languages) {
        this.application = application;
        this.languages = languages;
    }

    @Provides
    @Singleton
    Handler provideHandler() {
        return new Handler(Looper.getMainLooper());
    }

    @Provides
    @Singleton
    Application provideApplication() {
        return application;
    }

    @Provides
    @Singleton
    Bus provideBus(Handler handler) {
        return new AppBus(handler);
    }

    @Provides
    @Singleton
    SharedPreferences providePreferences() {
        return PreferenceManager.getDefaultSharedPreferences(application);
    }

    @Provides
    @Singleton
    @Named(PREFS_FLOATING)
    SharedPreferences provideFloatingPreferences() {
        return application.getSharedPreferences("floating-calculator", Context.MODE_PRIVATE);
    }

    @Provides
    @Singleton
    @Named(PREFS_TABS)
    SharedPreferences provideTabsPreferences() {
        return application.getSharedPreferences("tabs", Context.MODE_PRIVATE);
    }

    @Provides
    @Singleton
    @Named(PREFS_UI)
    SharedPreferences provideUiPreferences() {
        return provideUiPreferences(application);
    }

    @NonNull
    public static SharedPreferences provideUiPreferences(@NonNull Application application) {
        return application.getSharedPreferences("ui", Context.MODE_PRIVATE);
    }

    @Provides
    @Singleton
    @Named(THREAD_INIT)
    Executor provideInitThread() {
        return Executors.newSingleThreadExecutor(r -> new Thread(r, "Init"));
    }

    @Provides
    @Singleton
    @Named(THREAD_BACKGROUND)
    Executor provideBackgroundThread() {
        return Executors.newFixedThreadPool(5, new ThreadFactory() {
            @NonNull
            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public Thread newThread(@Nonnull Runnable r) {
                return new Thread(r, "Background #" + counter.getAndIncrement());
            }
        });
    }

    @Provides
    @Singleton
    ErrorReporter provideErrorReporter() {
        return new ErrorReporter() {
            @Override
            public void onException(@Nonnull Throwable e) {
            }

            @Override
            public void onError(@Nonnull String message) {
            }
        };
    }

    @Provides
    @Singleton
    Wizards provideWizards(@Nonnull Application application) {
        return new CalculatorWizards(application);
    }

    @Provides
    @Singleton
    @Named(THREAD_UI)
    Executor provideUiThread(@NonNull final Handler handler) {
        return command -> {
            if (App.isUiThread()) {
                command.run();
            } else {
                handler.post(command);
            }
        };
    }

    @Provides
    @Singleton
    InstallationProofProvider provideInstallationProofProvider() {
        // Production AI remains hidden until a real app/device proof adapter is wired.
        return new DisabledInstallationProofProvider();
    }

    @Provides
    @Singleton
    AiGatewayFeatureConfig provideAiGatewayFeatureConfig(InstallationProofProvider proofProvider) {
        return AiGatewayFeatureConfig.fromNullableUrls(
                BuildConfig.NOVA_AI_GATEWAY_URL,
                BuildConfig.NOVA_ANONYMOUS_SESSION_URL,
                proofProvider.isAvailable());
    }

    @Provides
    @Singleton
    AiSessionTokenProvider provideAiSessionTokenProvider() {
        // The feature remains disabled while InstallationProofProvider is unavailable. A cached,
        // proof-gated Nova session provider will replace this placeholder when that adapter lands.
        return () -> null;
    }

    @Provides
    @Singleton
    AiGatewayClient provideAiGatewayClient(AiGatewayFeatureConfig config,
                                           AiSessionTokenProvider tokenProvider,
                                           @Named(THREAD_BACKGROUND) Executor background,
                                           @Named(THREAD_UI) Executor ui) {
        if (!config.isEnabled()) {
            return new DisabledAiGatewayClient();
        }
        return new HttpAiGatewayClient(config.requireEndpoint(), tokenProvider, background, ui);
    }

    @Provides
    @Singleton
    AiExplainCoordinator provideAiExplainCoordinator(AiGatewayClient client) {
        return new AiExplainCoordinator(client);
    }

    @Provides
    @Singleton
    JsclMathEngine provideJsclMathEngine() {
        return JsclMathEngine.getInstance();
    }

    @Singleton
    @Provides
    Typeface provideTypeface() {
        return Typeface.createFromAsset(application.getAssets(), "fonts/Roboto-Regular.ttf");
    }

    @Singleton
    @Provides
    @Named(DIR_FILES)
    File provideFilesDir(@Named(THREAD_INIT) Executor initThread) {
        final File filesDir = makeFilesDir();
        initThread.execute(() -> {
            if (!filesDir.exists() && !filesDir.mkdirs()) {
                Log.e(App.TAG, "Can't create files dirs");
            }
        });
        return filesDir;
    }

    @Provides
    @Singleton
    Plotter providePlotter() {
        return Plot.newPlotter(application);
    }

    @Provides
    @Singleton
    @NonNull
    public Languages provideLanguages() {
        return languages;
    }

    @Nonnull
    private File makeFilesDir() {
        final File filesDir = application.getFilesDir();
        if (filesDir == null) {
            return new File(application.getApplicationInfo().dataDir, "files");
        }
        return filesDir;
    }

    private static class AppBus extends Bus {

        @NonNull
        private final Handler handler;

        AppBus(@Nonnull Handler handler) {
            super(com.squareup.otto.ThreadEnforcer.ANY);
            this.handler = handler;
        }

        @Override
        public void post(final Object event) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                super.post(event);
                return;
            }
            handler.post(() -> AppBus.super.post(event));
        }
    }
}
