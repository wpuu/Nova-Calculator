package org.solovyev.android.calculator.errors;

import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatActivity;
import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.UiPreferences;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class FixableErrorsActivity_MembersInjector implements MembersInjector<FixableErrorsActivity> {
  private final MembersInjector<AppCompatActivity> supertypeInjector;
  private final Provider<SharedPreferences> preferencesProvider;
  private final Provider<UiPreferences> uiPreferencesProvider;

  public FixableErrorsActivity_MembersInjector(MembersInjector<AppCompatActivity> supertypeInjector, Provider<SharedPreferences> preferencesProvider, Provider<UiPreferences> uiPreferencesProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert preferencesProvider != null;
    this.preferencesProvider = preferencesProvider;
    assert uiPreferencesProvider != null;
    this.uiPreferencesProvider = uiPreferencesProvider;
  }

  @Override
  public void injectMembers(FixableErrorsActivity instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.preferences = preferencesProvider.get();
    instance.uiPreferences = uiPreferencesProvider.get();
  }

  public static MembersInjector<FixableErrorsActivity> create(MembersInjector<AppCompatActivity> supertypeInjector, Provider<SharedPreferences> preferencesProvider, Provider<UiPreferences> uiPreferencesProvider) {  
      return new FixableErrorsActivity_MembersInjector(supertypeInjector, preferencesProvider, uiPreferencesProvider);
  }
}

