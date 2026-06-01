package org.solovyev.android.calculator;

import android.graphics.Typeface;
import dagger.internal.Factory;
import javax.annotation.Generated;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class AppModule_ProvideTypefaceFactory implements Factory<Typeface> {
  private final AppModule module;

  public AppModule_ProvideTypefaceFactory(AppModule module) {  
    assert module != null;
    this.module = module;
  }

  @Override
  public Typeface get() {  
    Typeface provided = module.provideTypeface();
    if (provided == null) {
      throw new NullPointerException("Cannot return null from a non-@Nullable @Provides method");
    }
    return provided;
  }

  public static Factory<Typeface> create(AppModule module) {  
    return new AppModule_ProvideTypefaceFactory(module);
  }
}

