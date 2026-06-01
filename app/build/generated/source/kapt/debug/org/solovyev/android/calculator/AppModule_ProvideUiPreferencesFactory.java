package org.solovyev.android.calculator;

import android.content.SharedPreferences;
import dagger.internal.Factory;
import javax.annotation.Generated;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class AppModule_ProvideUiPreferencesFactory implements Factory<SharedPreferences> {
  private final AppModule module;

  public AppModule_ProvideUiPreferencesFactory(AppModule module) {  
    assert module != null;
    this.module = module;
  }

  @Override
  public SharedPreferences get() {  
    SharedPreferences provided = module.provideUiPreferences();
    if (provided == null) {
      throw new NullPointerException("Cannot return null from a non-@Nullable @Provides method");
    }
    return provided;
  }

  public static Factory<SharedPreferences> create(AppModule module) {  
    return new AppModule_ProvideUiPreferencesFactory(module);
  }
}

