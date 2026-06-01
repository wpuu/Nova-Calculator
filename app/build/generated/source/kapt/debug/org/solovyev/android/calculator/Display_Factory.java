package org.solovyev.android.calculator;

import com.squareup.otto.Bus;
import dagger.MembersInjector;
import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class Display_Factory implements Factory<Display> {
  private final MembersInjector<Display> membersInjector;
  private final Provider<Bus> busProvider;

  public Display_Factory(MembersInjector<Display> membersInjector, Provider<Bus> busProvider) {  
    assert membersInjector != null;
    this.membersInjector = membersInjector;
    assert busProvider != null;
    this.busProvider = busProvider;
  }

  @Override
  public Display get() {  
    Display instance = new Display(busProvider.get());
    membersInjector.injectMembers(instance);
    return instance;
  }

  public static Factory<Display> create(MembersInjector<Display> membersInjector, Provider<Bus> busProvider) {  
    return new Display_Factory(membersInjector, busProvider);
  }
}

