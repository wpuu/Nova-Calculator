package org.solovyev.android.calculator;

import android.app.Application;
import dagger.MembersInjector;
import dagger.internal.DoubleCheckLazy;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class Display_MembersInjector implements MembersInjector<Display> {
  private final Provider<Application> applicationProvider;
  private final Provider<Engine> engineProvider;
  private final Provider<Clipboard> clipboardProvider;
  private final Provider<Notifier> notifierProvider;
  private final Provider<UiPreferences> uiPreferencesProvider;

  public Display_MembersInjector(Provider<Application> applicationProvider, Provider<Engine> engineProvider, Provider<Clipboard> clipboardProvider, Provider<Notifier> notifierProvider, Provider<UiPreferences> uiPreferencesProvider) {  
    assert applicationProvider != null;
    this.applicationProvider = applicationProvider;
    assert engineProvider != null;
    this.engineProvider = engineProvider;
    assert clipboardProvider != null;
    this.clipboardProvider = clipboardProvider;
    assert notifierProvider != null;
    this.notifierProvider = notifierProvider;
    assert uiPreferencesProvider != null;
    this.uiPreferencesProvider = uiPreferencesProvider;
  }

  @Override
  public void injectMembers(Display instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    instance.application = applicationProvider.get();
    instance.engine = engineProvider.get();
    instance.clipboard = DoubleCheckLazy.create(clipboardProvider);
    instance.notifier = DoubleCheckLazy.create(notifierProvider);
    instance.uiPreferences = DoubleCheckLazy.create(uiPreferencesProvider);
  }

  public static MembersInjector<Display> create(Provider<Application> applicationProvider, Provider<Engine> engineProvider, Provider<Clipboard> clipboardProvider, Provider<Notifier> notifierProvider, Provider<UiPreferences> uiPreferencesProvider) {  
      return new Display_MembersInjector(applicationProvider, engineProvider, clipboardProvider, notifierProvider, uiPreferencesProvider);
  }
}

