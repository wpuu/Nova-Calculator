package org.solovyev.android.calculator.preferences;

import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.BaseActivity;
import org.solovyev.android.calculator.language.Languages;
import org.solovyev.android.checkout.Billing;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class PreferencesActivity_MembersInjector implements MembersInjector<PreferencesActivity> {
  private final MembersInjector<BaseActivity> supertypeInjector;
  private final Provider<Billing> billingProvider;
  private final Provider<Languages> languagesProvider;

  public PreferencesActivity_MembersInjector(MembersInjector<BaseActivity> supertypeInjector, Provider<Billing> billingProvider, Provider<Languages> languagesProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert billingProvider != null;
    this.billingProvider = billingProvider;
    assert languagesProvider != null;
    this.languagesProvider = languagesProvider;
  }

  @Override
  public void injectMembers(PreferencesActivity instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.billing = billingProvider.get();
    instance.languages = languagesProvider.get();
  }

  public static MembersInjector<PreferencesActivity> create(MembersInjector<BaseActivity> supertypeInjector, Provider<Billing> billingProvider, Provider<Languages> languagesProvider) {  
      return new PreferencesActivity_MembersInjector(supertypeInjector, billingProvider, languagesProvider);
  }
}

