package org.solovyev.android.calculator;

import android.content.SharedPreferences;
import com.squareup.otto.Bus;
import dagger.MembersInjector;
import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class Calculator_Factory implements Factory<Calculator> {
  private final MembersInjector<Calculator> membersInjector;
  private final Provider<SharedPreferences> preferencesProvider;
  private final Provider<Bus> busProvider;

  public Calculator_Factory(MembersInjector<Calculator> membersInjector, Provider<SharedPreferences> preferencesProvider, Provider<Bus> busProvider) {  
    assert membersInjector != null;
    this.membersInjector = membersInjector;
    assert preferencesProvider != null;
    this.preferencesProvider = preferencesProvider;
    assert busProvider != null;
    this.busProvider = busProvider;
  }

  @Override
  public Calculator get() {  
    Calculator instance = new Calculator(preferencesProvider.get(), busProvider.get());
    membersInjector.injectMembers(instance);
    return instance;
  }

  public static Factory<Calculator> create(MembersInjector<Calculator> membersInjector, Provider<SharedPreferences> preferencesProvider, Provider<Bus> busProvider) {  
    return new Calculator_Factory(membersInjector, preferencesProvider, busProvider);
  }
}

