package org.solovyev.android.calculator;

import com.squareup.otto.Bus;
import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.history.History;
import org.solovyev.android.calculator.keyboard.PartialKeyboardUi;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class CalculatorActivity_MembersInjector implements MembersInjector<CalculatorActivity> {
  private final MembersInjector<BaseActivity> supertypeInjector;
  private final Provider<Keyboard> keyboardProvider;
  private final Provider<PartialKeyboardUi> partialKeyboardUiProvider;
  private final Provider<History> historyProvider;
  private final Provider<ActivityLauncher> launcherProvider;
  private final Provider<StartupHelper> startupHelperProvider;
  private final Provider<Bus> busProvider;

  public CalculatorActivity_MembersInjector(MembersInjector<BaseActivity> supertypeInjector, Provider<Keyboard> keyboardProvider, Provider<PartialKeyboardUi> partialKeyboardUiProvider, Provider<History> historyProvider, Provider<ActivityLauncher> launcherProvider, Provider<StartupHelper> startupHelperProvider, Provider<Bus> busProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert keyboardProvider != null;
    this.keyboardProvider = keyboardProvider;
    assert partialKeyboardUiProvider != null;
    this.partialKeyboardUiProvider = partialKeyboardUiProvider;
    assert historyProvider != null;
    this.historyProvider = historyProvider;
    assert launcherProvider != null;
    this.launcherProvider = launcherProvider;
    assert startupHelperProvider != null;
    this.startupHelperProvider = startupHelperProvider;
    assert busProvider != null;
    this.busProvider = busProvider;
  }

  @Override
  public void injectMembers(CalculatorActivity instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.keyboard = keyboardProvider.get();
    instance.partialKeyboardUi = partialKeyboardUiProvider.get();
    instance.history = historyProvider.get();
    instance.launcher = launcherProvider.get();
    instance.startupHelper = startupHelperProvider.get();
    instance.bus = busProvider.get();
  }

  public static MembersInjector<CalculatorActivity> create(MembersInjector<BaseActivity> supertypeInjector, Provider<Keyboard> keyboardProvider, Provider<PartialKeyboardUi> partialKeyboardUiProvider, Provider<History> historyProvider, Provider<ActivityLauncher> launcherProvider, Provider<StartupHelper> startupHelperProvider, Provider<Bus> busProvider) {  
      return new CalculatorActivity_MembersInjector(supertypeInjector, keyboardProvider, partialKeyboardUiProvider, historyProvider, launcherProvider, startupHelperProvider, busProvider);
  }
}

