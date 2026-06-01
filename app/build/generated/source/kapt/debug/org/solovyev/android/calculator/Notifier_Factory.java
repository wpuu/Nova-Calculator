package org.solovyev.android.calculator;

import dagger.MembersInjector;
import dagger.internal.Factory;
import javax.annotation.Generated;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class Notifier_Factory implements Factory<Notifier> {
  private final MembersInjector<Notifier> membersInjector;

  public Notifier_Factory(MembersInjector<Notifier> membersInjector) {  
    assert membersInjector != null;
    this.membersInjector = membersInjector;
  }

  @Override
  public Notifier get() {  
    Notifier instance = new Notifier();
    membersInjector.injectMembers(instance);
    return instance;
  }

  public static Factory<Notifier> create(MembersInjector<Notifier> membersInjector) {  
    return new Notifier_Factory(membersInjector);
  }
}

