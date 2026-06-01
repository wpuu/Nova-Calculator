package org.solovyev.android.calculator;

import android.content.SharedPreferences;
import android.graphics.Typeface;
import androidx.fragment.app.DialogFragment;
import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.ga.Ga;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class BaseDialogFragment_MembersInjector implements MembersInjector<BaseDialogFragment> {
  private final MembersInjector<DialogFragment> supertypeInjector;
  private final Provider<SharedPreferences> preferencesProvider;
  private final Provider<Ga> gaProvider;
  private final Provider<Typeface> typefaceProvider;

  public BaseDialogFragment_MembersInjector(MembersInjector<DialogFragment> supertypeInjector, Provider<SharedPreferences> preferencesProvider, Provider<Ga> gaProvider, Provider<Typeface> typefaceProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert preferencesProvider != null;
    this.preferencesProvider = preferencesProvider;
    assert gaProvider != null;
    this.gaProvider = gaProvider;
    assert typefaceProvider != null;
    this.typefaceProvider = typefaceProvider;
  }

  @Override
  public void injectMembers(BaseDialogFragment instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.preferences = preferencesProvider.get();
    instance.ga = gaProvider.get();
    instance.typeface = typefaceProvider.get();
  }

  public static MembersInjector<BaseDialogFragment> create(MembersInjector<DialogFragment> supertypeInjector, Provider<SharedPreferences> preferencesProvider, Provider<Ga> gaProvider, Provider<Typeface> typefaceProvider) {  
      return new BaseDialogFragment_MembersInjector(supertypeInjector, preferencesProvider, gaProvider, typefaceProvider);
  }
}

