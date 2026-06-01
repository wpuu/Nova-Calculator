package org.solovyev.android.calculator.wizard;

import android.content.SharedPreferences;
import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.BaseActivity;
import org.solovyev.android.calculator.language.Languages;
import org.solovyev.android.wizard.Wizards;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class WizardActivity_MembersInjector implements MembersInjector<WizardActivity> {
  private final MembersInjector<BaseActivity> supertypeInjector;
  private final Provider<SharedPreferences> preferencesProvider;
  private final Provider<Languages> languagesProvider;
  private final Provider<Wizards> wizardsProvider;

  public WizardActivity_MembersInjector(MembersInjector<BaseActivity> supertypeInjector, Provider<SharedPreferences> preferencesProvider, Provider<Languages> languagesProvider, Provider<Wizards> wizardsProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert preferencesProvider != null;
    this.preferencesProvider = preferencesProvider;
    assert languagesProvider != null;
    this.languagesProvider = languagesProvider;
    assert wizardsProvider != null;
    this.wizardsProvider = wizardsProvider;
  }

  @Override
  public void injectMembers(WizardActivity instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.preferences = preferencesProvider.get();
    instance.languages = languagesProvider.get();
    instance.wizards = wizardsProvider.get();
  }

  public static MembersInjector<WizardActivity> create(MembersInjector<BaseActivity> supertypeInjector, Provider<SharedPreferences> preferencesProvider, Provider<Languages> languagesProvider, Provider<Wizards> wizardsProvider) {  
      return new WizardActivity_MembersInjector(supertypeInjector, preferencesProvider, languagesProvider, wizardsProvider);
  }
}

