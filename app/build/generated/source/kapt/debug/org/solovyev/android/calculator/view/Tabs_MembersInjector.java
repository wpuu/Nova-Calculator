package org.solovyev.android.calculator.view;

import android.content.SharedPreferences;
import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class Tabs_MembersInjector implements MembersInjector<Tabs> {
  private final Provider<SharedPreferences> preferencesProvider;

  public Tabs_MembersInjector(Provider<SharedPreferences> preferencesProvider) {  
    assert preferencesProvider != null;
    this.preferencesProvider = preferencesProvider;
  }

  @Override
  public void injectMembers(Tabs instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    instance.preferences = preferencesProvider.get();
  }

  public static MembersInjector<Tabs> create(Provider<SharedPreferences> preferencesProvider) {  
      return new Tabs_MembersInjector(preferencesProvider);
  }
}

