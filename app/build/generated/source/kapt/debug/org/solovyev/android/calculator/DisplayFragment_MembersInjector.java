package org.solovyev.android.calculator;

import android.content.SharedPreferences;
import com.squareup.otto.Bus;
import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class DisplayFragment_MembersInjector implements MembersInjector<DisplayFragment> {
  private final MembersInjector<BaseFragment> supertypeInjector;
  private final Provider<SharedPreferences> preferencesProvider;
  private final Provider<ErrorReporter> errorReporterProvider;
  private final Provider<Display> displayProvider;
  private final Provider<ActivityLauncher> launcherProvider;
  private final Provider<Bus> busProvider;
  private final Provider<Calculator> calculatorProvider;
  private final Provider<Engine> engineProvider;

  public DisplayFragment_MembersInjector(MembersInjector<BaseFragment> supertypeInjector, Provider<SharedPreferences> preferencesProvider, Provider<ErrorReporter> errorReporterProvider, Provider<Display> displayProvider, Provider<ActivityLauncher> launcherProvider, Provider<Bus> busProvider, Provider<Calculator> calculatorProvider, Provider<Engine> engineProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert preferencesProvider != null;
    this.preferencesProvider = preferencesProvider;
    assert errorReporterProvider != null;
    this.errorReporterProvider = errorReporterProvider;
    assert displayProvider != null;
    this.displayProvider = displayProvider;
    assert launcherProvider != null;
    this.launcherProvider = launcherProvider;
    assert busProvider != null;
    this.busProvider = busProvider;
    assert calculatorProvider != null;
    this.calculatorProvider = calculatorProvider;
    assert engineProvider != null;
    this.engineProvider = engineProvider;
  }

  @Override
  public void injectMembers(DisplayFragment instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.preferences = preferencesProvider.get();
    instance.errorReporter = errorReporterProvider.get();
    instance.display = displayProvider.get();
    instance.launcher = launcherProvider.get();
    instance.bus = busProvider.get();
    instance.calculator = calculatorProvider.get();
    instance.engine = engineProvider.get();
  }

  public static MembersInjector<DisplayFragment> create(MembersInjector<BaseFragment> supertypeInjector, Provider<SharedPreferences> preferencesProvider, Provider<ErrorReporter> errorReporterProvider, Provider<Display> displayProvider, Provider<ActivityLauncher> launcherProvider, Provider<Bus> busProvider, Provider<Calculator> calculatorProvider, Provider<Engine> engineProvider) {  
      return new DisplayFragment_MembersInjector(supertypeInjector, preferencesProvider, errorReporterProvider, displayProvider, launcherProvider, busProvider, calculatorProvider, engineProvider);
  }
}

