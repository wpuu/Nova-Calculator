package org.solovyev.android.calculator;

import android.content.SharedPreferences;
import com.squareup.otto.Bus;
import dagger.MembersInjector;
import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class Keyboard_Factory implements Factory<Keyboard> {
  private final MembersInjector<Keyboard> membersInjector;
  private final Provider<SharedPreferences> preferencesProvider;
  private final Provider<Bus> busProvider;

  public Keyboard_Factory(MembersInjector<Keyboard> membersInjector, Provider<SharedPreferences> preferencesProvider, Provider<Bus> busProvider) {  
    assert membersInjector != null;
    this.membersInjector = membersInjector;
    assert preferencesProvider != null;
    this.preferencesProvider = preferencesProvider;
    assert busProvider != null;
    this.busProvider = busProvider;
  }

  @Override
  public Keyboard get() {  
    Keyboard instance = new Keyboard(preferencesProvider.get(), busProvider.get());
    membersInjector.injectMembers(instance);
    return instance;
  }

  public static Factory<Keyboard> create(MembersInjector<Keyboard> membersInjector, Provider<SharedPreferences> preferencesProvider, Provider<Bus> busProvider) {  
    return new Keyboard_Factory(membersInjector, preferencesProvider, busProvider);
  }
}

