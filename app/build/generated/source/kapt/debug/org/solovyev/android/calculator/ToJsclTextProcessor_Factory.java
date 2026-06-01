package org.solovyev.android.calculator;

import dagger.MembersInjector;
import dagger.internal.Factory;
import javax.annotation.Generated;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class ToJsclTextProcessor_Factory implements Factory<ToJsclTextProcessor> {
  private final MembersInjector<ToJsclTextProcessor> membersInjector;

  public ToJsclTextProcessor_Factory(MembersInjector<ToJsclTextProcessor> membersInjector) {  
    assert membersInjector != null;
    this.membersInjector = membersInjector;
  }

  @Override
  public ToJsclTextProcessor get() {  
    ToJsclTextProcessor instance = new ToJsclTextProcessor();
    membersInjector.injectMembers(instance);
    return instance;
  }

  public static Factory<ToJsclTextProcessor> create(MembersInjector<ToJsclTextProcessor> membersInjector) {  
    return new ToJsclTextProcessor_Factory(membersInjector);
  }
}

