package org.solovyev.android.calculator.wizard;

import android.graphics.Typeface;
import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class DragButtonWizardStep_MembersInjector implements MembersInjector<DragButtonWizardStep> {
  private final MembersInjector<WizardFragment> supertypeInjector;
  private final Provider<Typeface> typefaceProvider;

  public DragButtonWizardStep_MembersInjector(MembersInjector<WizardFragment> supertypeInjector, Provider<Typeface> typefaceProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert typefaceProvider != null;
    this.typefaceProvider = typefaceProvider;
  }

  @Override
  public void injectMembers(DragButtonWizardStep instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.typeface = typefaceProvider.get();
  }

  public static MembersInjector<DragButtonWizardStep> create(MembersInjector<WizardFragment> supertypeInjector, Provider<Typeface> typefaceProvider) {  
      return new DragButtonWizardStep_MembersInjector(supertypeInjector, typefaceProvider);
  }
}

