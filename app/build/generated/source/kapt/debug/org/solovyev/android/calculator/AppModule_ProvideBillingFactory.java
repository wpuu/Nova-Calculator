package org.solovyev.android.calculator;

import dagger.internal.Factory;
import javax.annotation.Generated;
import org.solovyev.android.checkout.Billing;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class AppModule_ProvideBillingFactory implements Factory<Billing> {
  private final AppModule module;

  public AppModule_ProvideBillingFactory(AppModule module) {  
    assert module != null;
    this.module = module;
  }

  @Override
  public Billing get() {  
    Billing provided = module.provideBilling();
    if (provided == null) {
      throw new NullPointerException("Cannot return null from a non-@Nullable @Provides method");
    }
    return provided;
  }

  public static Factory<Billing> create(AppModule module) {  
    return new AppModule_ProvideBillingFactory(module);
  }
}

