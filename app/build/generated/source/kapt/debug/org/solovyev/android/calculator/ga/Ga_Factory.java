package org.solovyev.android.calculator.ga;

import android.app.Application;
import android.content.SharedPreferences;
import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class Ga_Factory implements Factory<Ga> {
  private final Provider<Application> applicationProvider;
  private final Provider<SharedPreferences> preferencesProvider;

  public Ga_Factory(Provider<Application> applicationProvider, Provider<SharedPreferences> preferencesProvider) {  
    assert applicationProvider != null;
    this.applicationProvider = applicationProvider;
    assert preferencesProvider != null;
    this.preferencesProvider = preferencesProvider;
  }

  @Override
  public Ga get() {  
    return new Ga(applicationProvider.get(), preferencesProvider.get());
  }

  public static Factory<Ga> create(Provider<Application> applicationProvider, Provider<SharedPreferences> preferencesProvider) {  
    return new Ga_Factory(applicationProvider, preferencesProvider);
  }
}

