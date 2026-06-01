package org.solovyev.android.calculator;

import android.app.Application;
import android.content.SharedPreferences;
import dagger.MembersInjector;
import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class Editor_Factory implements Factory<Editor> {
  private final MembersInjector<Editor> membersInjector;
  private final Provider<Application> applicationProvider;
  private final Provider<SharedPreferences> preferencesProvider;
  private final Provider<Engine> engineProvider;

  public Editor_Factory(MembersInjector<Editor> membersInjector, Provider<Application> applicationProvider, Provider<SharedPreferences> preferencesProvider, Provider<Engine> engineProvider) {  
    assert membersInjector != null;
    this.membersInjector = membersInjector;
    assert applicationProvider != null;
    this.applicationProvider = applicationProvider;
    assert preferencesProvider != null;
    this.preferencesProvider = preferencesProvider;
    assert engineProvider != null;
    this.engineProvider = engineProvider;
  }

  @Override
  public Editor get() {  
    Editor instance = new Editor(applicationProvider.get(), preferencesProvider.get(), engineProvider.get());
    membersInjector.injectMembers(instance);
    return instance;
  }

  public static Factory<Editor> create(MembersInjector<Editor> membersInjector, Provider<Application> applicationProvider, Provider<SharedPreferences> preferencesProvider, Provider<Engine> engineProvider) {  
    return new Editor_Factory(membersInjector, applicationProvider, preferencesProvider, engineProvider);
  }
}

