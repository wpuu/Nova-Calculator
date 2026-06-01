package org.solovyev.android.calculator;

import android.os.Handler;
import dagger.internal.Factory;
import java.util.concurrent.Executor;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class AppModule_ProvideUiThreadFactory implements Factory<Executor> {
  private final AppModule module;
  private final Provider<Handler> handlerProvider;

  public AppModule_ProvideUiThreadFactory(AppModule module, Provider<Handler> handlerProvider) {  
    assert module != null;
    this.module = module;
    assert handlerProvider != null;
    this.handlerProvider = handlerProvider;
  }

  @Override
  public Executor get() {  
    Executor provided = module.provideUiThread(handlerProvider.get());
    if (provided == null) {
      throw new NullPointerException("Cannot return null from a non-@Nullable @Provides method");
    }
    return provided;
  }

  public static Factory<Executor> create(AppModule module, Provider<Handler> handlerProvider) {  
    return new AppModule_ProvideUiThreadFactory(module, handlerProvider);
  }
}

