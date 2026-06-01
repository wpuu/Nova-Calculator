package org.solovyev.android.calculator.errors;

import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.BaseDialogFragment;
import org.solovyev.android.calculator.UiPreferences;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class FixableErrorFragment_MembersInjector implements MembersInjector<FixableErrorFragment> {
  private final MembersInjector<BaseDialogFragment> supertypeInjector;
  private final Provider<UiPreferences> uiPreferencesProvider;

  public FixableErrorFragment_MembersInjector(MembersInjector<BaseDialogFragment> supertypeInjector, Provider<UiPreferences> uiPreferencesProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert uiPreferencesProvider != null;
    this.uiPreferencesProvider = uiPreferencesProvider;
  }

  @Override
  public void injectMembers(FixableErrorFragment instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.uiPreferences = uiPreferencesProvider.get();
  }

  public static MembersInjector<FixableErrorFragment> create(MembersInjector<BaseDialogFragment> supertypeInjector, Provider<UiPreferences> uiPreferencesProvider) {  
      return new FixableErrorFragment_MembersInjector(supertypeInjector, uiPreferencesProvider);
  }
}

