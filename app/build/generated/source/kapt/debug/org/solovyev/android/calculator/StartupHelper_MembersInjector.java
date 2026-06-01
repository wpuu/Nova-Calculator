package org.solovyev.android.calculator;

import android.content.SharedPreferences;
import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.wizard.Wizards;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class StartupHelper_MembersInjector implements MembersInjector<StartupHelper> {
  private final Provider<SharedPreferences> uiPreferencesProvider;
  private final Provider<SharedPreferences> preferencesProvider;
  private final Provider<Wizards> wizardsProvider;

  public StartupHelper_MembersInjector(Provider<SharedPreferences> uiPreferencesProvider, Provider<SharedPreferences> preferencesProvider, Provider<Wizards> wizardsProvider) {  
    assert uiPreferencesProvider != null;
    this.uiPreferencesProvider = uiPreferencesProvider;
    assert preferencesProvider != null;
    this.preferencesProvider = preferencesProvider;
    assert wizardsProvider != null;
    this.wizardsProvider = wizardsProvider;
  }

  @Override
  public void injectMembers(StartupHelper instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    instance.uiPreferences = uiPreferencesProvider.get();
    instance.preferences = preferencesProvider.get();
    instance.wizards = wizardsProvider.get();
  }

  public static MembersInjector<StartupHelper> create(Provider<SharedPreferences> uiPreferencesProvider, Provider<SharedPreferences> preferencesProvider, Provider<Wizards> wizardsProvider) {  
      return new StartupHelper_MembersInjector(uiPreferencesProvider, preferencesProvider, wizardsProvider);
  }
}

