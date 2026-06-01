package org.solovyev.android.calculator;

import dagger.MembersInjector;
import dagger.internal.Factory;
import javax.annotation.Generated;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class ActivityLauncher_Factory implements Factory<ActivityLauncher> {
  private final MembersInjector<ActivityLauncher> membersInjector;

  public ActivityLauncher_Factory(MembersInjector<ActivityLauncher> membersInjector) {  
    assert membersInjector != null;
    this.membersInjector = membersInjector;
  }

  @Override
  public ActivityLauncher get() {  
    ActivityLauncher instance = new ActivityLauncher();
    membersInjector.injectMembers(instance);
    return instance;
  }

  public static Factory<ActivityLauncher> create(MembersInjector<ActivityLauncher> membersInjector) {  
    return new ActivityLauncher_Factory(membersInjector);
  }
}

