package org.solovyev.android.calculator;

import dagger.internal.Factory;
import javax.annotation.Generated;
import org.solovyev.android.calculator.language.Languages;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class AppModule_ProvideLanguagesFactory implements Factory<Languages> {
  private final AppModule module;

  public AppModule_ProvideLanguagesFactory(AppModule module) {  
    assert module != null;
    this.module = module;
  }

  @Override
  public Languages get() {  
    Languages provided = module.provideLanguages();
    if (provided == null) {
      throw new NullPointerException("Cannot return null from a non-@Nullable @Provides method");
    }
    return provided;
  }

  public static Factory<Languages> create(AppModule module) {  
    return new AppModule_ProvideLanguagesFactory(module);
  }
}

