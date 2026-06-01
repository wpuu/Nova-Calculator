package org.solovyev.android.calculator;

import android.os.Handler;
import dagger.internal.Factory;
import javax.annotation.Generated;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class AppModule_ProvideHandlerFactory implements Factory<Handler> {
  private final AppModule module;

  public AppModule_ProvideHandlerFactory(AppModule module) {  
    assert module != null;
    this.module = module;
  }

  @Override
  public Handler get() {  
    Handler provided = module.provideHandler();
    if (provided == null) {
      throw new NullPointerException("Cannot return null from a non-@Nullable @Provides method");
    }
    return provided;
  }

  public static Factory<Handler> create(AppModule module) {  
    return new AppModule_ProvideHandlerFactory(module);
  }
}

