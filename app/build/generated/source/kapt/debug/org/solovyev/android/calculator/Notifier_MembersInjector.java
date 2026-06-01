package org.solovyev.android.calculator;

import android.app.Application;
import android.os.Handler;
import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class Notifier_MembersInjector implements MembersInjector<Notifier> {
  private final Provider<Application> applicationProvider;
  private final Provider<Handler> handlerProvider;

  public Notifier_MembersInjector(Provider<Application> applicationProvider, Provider<Handler> handlerProvider) {  
    assert applicationProvider != null;
    this.applicationProvider = applicationProvider;
    assert handlerProvider != null;
    this.handlerProvider = handlerProvider;
  }

  @Override
  public void injectMembers(Notifier instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    instance.application = applicationProvider.get();
    instance.handler = handlerProvider.get();
  }

  public static MembersInjector<Notifier> create(Provider<Application> applicationProvider, Provider<Handler> handlerProvider) {  
      return new Notifier_MembersInjector(applicationProvider, handlerProvider);
  }
}

