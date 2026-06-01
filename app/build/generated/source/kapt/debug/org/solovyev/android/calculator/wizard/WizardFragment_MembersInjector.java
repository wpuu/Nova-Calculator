package org.solovyev.android.calculator.wizard;

import android.content.SharedPreferences;
import android.graphics.Typeface;
import androidx.fragment.app.Fragment;
import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class WizardFragment_MembersInjector implements MembersInjector<WizardFragment> {
  private final MembersInjector<Fragment> supertypeInjector;
  private final Provider<SharedPreferences> preferencesProvider;
  private final Provider<Typeface> typefaceProvider;

  public WizardFragment_MembersInjector(MembersInjector<Fragment> supertypeInjector, Provider<SharedPreferences> preferencesProvider, Provider<Typeface> typefaceProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert preferencesProvider != null;
    this.preferencesProvider = preferencesProvider;
    assert typefaceProvider != null;
    this.typefaceProvider = typefaceProvider;
  }

  @Override
  public void injectMembers(WizardFragment instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.preferences = preferencesProvider.get();
    instance.typeface = typefaceProvider.get();
  }

  public static MembersInjector<WizardFragment> create(MembersInjector<Fragment> supertypeInjector, Provider<SharedPreferences> preferencesProvider, Provider<Typeface> typefaceProvider) {  
      return new WizardFragment_MembersInjector(supertypeInjector, preferencesProvider, typefaceProvider);
  }
}

