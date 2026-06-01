package org.solovyev.android.calculator.history;

import dagger.MembersInjector;
import dagger.internal.Factory;
import javax.annotation.Generated;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class History_Factory implements Factory<History> {
  private final MembersInjector<History> membersInjector;

  public History_Factory(MembersInjector<History> membersInjector) {  
    assert membersInjector != null;
    this.membersInjector = membersInjector;
  }

  @Override
  public History get() {  
    History instance = new History();
    membersInjector.injectMembers(instance);
    return instance;
  }

  public static Factory<History> create(MembersInjector<History> membersInjector) {  
    return new History_Factory(membersInjector);
  }
}

