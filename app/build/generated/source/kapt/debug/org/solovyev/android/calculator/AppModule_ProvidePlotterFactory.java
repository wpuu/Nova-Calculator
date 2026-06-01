package org.solovyev.android.calculator;

import dagger.internal.Factory;
import javax.annotation.Generated;
import org.solovyev.android.plotter.Plotter;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class AppModule_ProvidePlotterFactory implements Factory<Plotter> {
  private final AppModule module;

  public AppModule_ProvidePlotterFactory(AppModule module) {  
    assert module != null;
    this.module = module;
  }

  @Override
  public Plotter get() {  
    Plotter provided = module.providePlotter();
    if (provided == null) {
      throw new NullPointerException("Cannot return null from a non-@Nullable @Provides method");
    }
    return provided;
  }

  public static Factory<Plotter> create(AppModule module) {  
    return new AppModule_ProvidePlotterFactory(module);
  }
}

