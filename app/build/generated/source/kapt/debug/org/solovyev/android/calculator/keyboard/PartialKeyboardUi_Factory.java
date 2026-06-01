package org.solovyev.android.calculator.keyboard;

import android.app.Application;
import dagger.MembersInjector;
import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class PartialKeyboardUi_Factory implements Factory<PartialKeyboardUi> {
  private final MembersInjector<PartialKeyboardUi> membersInjector;
  private final Provider<Application> applicationProvider;

  public PartialKeyboardUi_Factory(MembersInjector<PartialKeyboardUi> membersInjector, Provider<Application> applicationProvider) {  
    assert membersInjector != null;
    this.membersInjector = membersInjector;
    assert applicationProvider != null;
    this.applicationProvider = applicationProvider;
  }

  @Override
  public PartialKeyboardUi get() {  
    PartialKeyboardUi instance = new PartialKeyboardUi(applicationProvider.get());
    membersInjector.injectMembers(instance);
    return instance;
  }

  public static Factory<PartialKeyboardUi> create(MembersInjector<PartialKeyboardUi> membersInjector, Provider<Application> applicationProvider) {  
    return new PartialKeyboardUi_Factory(membersInjector, applicationProvider);
  }
}

