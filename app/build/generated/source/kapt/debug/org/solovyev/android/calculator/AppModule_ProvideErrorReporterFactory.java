package org.solovyev.android.calculator;

import dagger.internal.Factory;
import javax.annotation.Generated;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class AppModule_ProvideErrorReporterFactory implements Factory<ErrorReporter> {
  private final AppModule module;

  public AppModule_ProvideErrorReporterFactory(AppModule module) {  
    assert module != null;
    this.module = module;
  }

  @Override
  public ErrorReporter get() {  
    ErrorReporter provided = module.provideErrorReporter();
    if (provided == null) {
      throw new NullPointerException("Cannot return null from a non-@Nullable @Provides method");
    }
    return provided;
  }

  public static Factory<ErrorReporter> create(AppModule module) {  
    return new AppModule_ProvideErrorReporterFactory(module);
  }
}

