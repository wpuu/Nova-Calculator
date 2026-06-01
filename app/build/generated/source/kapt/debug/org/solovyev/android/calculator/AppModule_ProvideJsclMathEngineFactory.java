package org.solovyev.android.calculator;

import dagger.internal.Factory;
import javax.annotation.Generated;
import jscl.JsclMathEngine;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class AppModule_ProvideJsclMathEngineFactory implements Factory<JsclMathEngine> {
  private final AppModule module;

  public AppModule_ProvideJsclMathEngineFactory(AppModule module) {  
    assert module != null;
    this.module = module;
  }

  @Override
  public JsclMathEngine get() {  
    JsclMathEngine provided = module.provideJsclMathEngine();
    if (provided == null) {
      throw new NullPointerException("Cannot return null from a non-@Nullable @Provides method");
    }
    return provided;
  }

  public static Factory<JsclMathEngine> create(AppModule module) {  
    return new AppModule_ProvideJsclMathEngineFactory(module);
  }
}

