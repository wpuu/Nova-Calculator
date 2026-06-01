package org.solovyev.android.calculator.preferences;

import androidx.appcompat.app.AppCompatActivity;
import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.ga.Ga;
import org.solovyev.android.checkout.Billing;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class PurchaseDialogActivity_MembersInjector implements MembersInjector<PurchaseDialogActivity> {
  private final MembersInjector<AppCompatActivity> supertypeInjector;
  private final Provider<Billing> billingProvider;
  private final Provider<Ga> gaProvider;

  public PurchaseDialogActivity_MembersInjector(MembersInjector<AppCompatActivity> supertypeInjector, Provider<Billing> billingProvider, Provider<Ga> gaProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert billingProvider != null;
    this.billingProvider = billingProvider;
    assert gaProvider != null;
    this.gaProvider = gaProvider;
  }

  @Override
  public void injectMembers(PurchaseDialogActivity instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.billing = billingProvider.get();
    instance.ga = gaProvider.get();
  }

  public static MembersInjector<PurchaseDialogActivity> create(MembersInjector<AppCompatActivity> supertypeInjector, Provider<Billing> billingProvider, Provider<Ga> gaProvider) {  
      return new PurchaseDialogActivity_MembersInjector(supertypeInjector, billingProvider, gaProvider);
  }
}

