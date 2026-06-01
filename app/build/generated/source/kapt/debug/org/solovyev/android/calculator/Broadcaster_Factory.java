package org.solovyev.android.calculator;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Handler;
import com.squareup.otto.Bus;
import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class Broadcaster_Factory implements Factory<Broadcaster> {
  private final Provider<Application> applicationProvider;
  private final Provider<SharedPreferences> preferencesProvider;
  private final Provider<Bus> busProvider;
  private final Provider<Handler> handlerProvider;

  public Broadcaster_Factory(Provider<Application> applicationProvider, Provider<SharedPreferences> preferencesProvider, Provider<Bus> busProvider, Provider<Handler> handlerProvider) {  
    assert applicationProvider != null;
    this.applicationProvider = applicationProvider;
    assert preferencesProvider != null;
    this.preferencesProvider = preferencesProvider;
    assert busProvider != null;
    this.busProvider = busProvider;
    assert handlerProvider != null;
    this.handlerProvider = handlerProvider;
  }

  @Override
  public Broadcaster get() {  
    return new Broadcaster(applicationProvider.get(), preferencesProvider.get(), busProvider.get(), handlerProvider.get());
  }

  public static Factory<Broadcaster> create(Provider<Application> applicationProvider, Provider<SharedPreferences> preferencesProvider, Provider<Bus> busProvider, Provider<Handler> handlerProvider) {  
    return new Broadcaster_Factory(applicationProvider, preferencesProvider, busProvider, handlerProvider);
  }
}

