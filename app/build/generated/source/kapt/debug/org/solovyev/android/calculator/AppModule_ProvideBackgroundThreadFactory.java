package org.solovyev.android.calculator;

import dagger.internal.Factory;
import java.util.concurrent.Executor;
import javax.annotation.Generated;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class AppModule_ProvideBackgroundThreadFactory implements Factory<Executor> {
  private final AppModule module;

  public AppModule_ProvideBackgroundThreadFactory(AppModule module) {  
    assert module != null;
    this.module = module;
  }

  @Override
  public Executor get() {  
    Executor provided = module.provideBackgroundThread();
    if (provided == null) {
      throw new NullPointerException("Cannot return null from a non-@Nullable @Provides method");
    }
    return provided;
  }

  public static Factory<Executor> create(AppModule module) {  
    return new AppModule_ProvideBackgroundThreadFactory(module);
  }
}

