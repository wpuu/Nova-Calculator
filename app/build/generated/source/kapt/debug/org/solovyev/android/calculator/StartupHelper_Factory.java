package org.solovyev.android.calculator;

import dagger.MembersInjector;
import dagger.internal.Factory;
import javax.annotation.Generated;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class StartupHelper_Factory implements Factory<StartupHelper> {
  private final MembersInjector<StartupHelper> membersInjector;

  public StartupHelper_Factory(MembersInjector<StartupHelper> membersInjector) {  
    assert membersInjector != null;
    this.membersInjector = membersInjector;
  }

  @Override
  public StartupHelper get() {  
    StartupHelper instance = new StartupHelper();
    membersInjector.injectMembers(instance);
    return instance;
  }

  public static Factory<StartupHelper> create(MembersInjector<StartupHelper> membersInjector) {  
    return new StartupHelper_Factory(membersInjector);
  }
}

