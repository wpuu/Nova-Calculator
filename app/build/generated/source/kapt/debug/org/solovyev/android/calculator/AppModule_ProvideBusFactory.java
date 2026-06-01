package org.solovyev.android.calculator;

import android.os.Handler;
import com.squareup.otto.Bus;
import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class AppModule_ProvideBusFactory implements Factory<Bus> {
  private final AppModule module;
  private final Provider<Handler> handlerProvider;

  public AppModule_ProvideBusFactory(AppModule module, Provider<Handler> handlerProvider) {  
    assert module != null;
    this.module = module;
    assert handlerProvider != null;
    this.handlerProvider = handlerProvider;
  }

  @Override
  public Bus get() {  
    Bus provided = module.provideBus(handlerProvider.get());
    if (provided == null) {
      throw new NullPointerException("Cannot return null from a non-@Nullable @Provides method");
    }
    return provided;
  }

  public static Factory<Bus> create(AppModule module, Provider<Handler> handlerProvider) {  
    return new AppModule_ProvideBusFactory(module, handlerProvider);
  }
}

