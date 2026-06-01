package org.solovyev.android.calculator.wizard;

import android.graphics.Typeface;
import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class ChooseModeWizardStep_MembersInjector implements MembersInjector<ChooseModeWizardStep> {
  private final MembersInjector<WizardFragment> supertypeInjector;
  private final Provider<Typeface> typefaceProvider;

  public ChooseModeWizardStep_MembersInjector(MembersInjector<WizardFragment> supertypeInjector, Provider<Typeface> typefaceProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert typefaceProvider != null;
    this.typefaceProvider = typefaceProvider;
  }

  @Override
  public void injectMembers(ChooseModeWizardStep instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.typeface = typefaceProvider.get();
  }

  public static MembersInjector<ChooseModeWizardStep> create(MembersInjector<WizardFragment> supertypeInjector, Provider<Typeface> typefaceProvider) {  
      return new ChooseModeWizardStep_MembersInjector(supertypeInjector, typefaceProvider);
  }
}

