package org.solovyev.android.checkout;

import dagger.MembersInjector;
import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class CppCheckout_Factory implements Factory<CppCheckout> {
  private final MembersInjector<CppCheckout> membersInjector;
  private final Provider<Billing> billingProvider;

  public CppCheckout_Factory(MembersInjector<CppCheckout> membersInjector, Provider<Billing> billingProvider) {  
    assert membersInjector != null;
    this.membersInjector = membersInjector;
    assert billingProvider != null;
    this.billingProvider = billingProvider;
  }

  @Override
  public CppCheckout get() {  
    CppCheckout instance = new CppCheckout(billingProvider.get());
    membersInjector.injectMembers(instance);
    return instance;
  }

  public static Factory<CppCheckout> create(MembersInjector<CppCheckout> membersInjector, Provider<Billing> billingProvider) {  
    return new CppCheckout_Factory(membersInjector, billingProvider);
  }
}

