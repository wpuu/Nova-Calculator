package org.solovyev.android.calculator.keyboard;

import android.app.Application;
import dagger.MembersInjector;
import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class KeyboardUi_Factory implements Factory<KeyboardUi> {
  private final MembersInjector<KeyboardUi> membersInjector;
  private final Provider<Application> applicationProvider;

  public KeyboardUi_Factory(MembersInjector<KeyboardUi> membersInjector, Provider<Application> applicationProvider) {  
    assert membersInjector != null;
    this.membersInjector = membersInjector;
    assert applicationProvider != null;
    this.applicationProvider = applicationProvider;
  }

  @Override
  public KeyboardUi get() {  
    KeyboardUi instance = new KeyboardUi(applicationProvider.get());
    membersInjector.injectMembers(instance);
    return instance;
  }

  public static Factory<KeyboardUi> create(MembersInjector<KeyboardUi> membersInjector, Provider<Application> applicationProvider) {  
    return new KeyboardUi_Factory(membersInjector, applicationProvider);
  }
}

