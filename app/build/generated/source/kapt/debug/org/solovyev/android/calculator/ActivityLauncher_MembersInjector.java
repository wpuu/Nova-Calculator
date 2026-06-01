package org.solovyev.android.calculator;

import android.app.Application;
import dagger.MembersInjector;
import dagger.internal.DoubleCheckLazy;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.plotter.Plotter;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class ActivityLauncher_MembersInjector implements MembersInjector<ActivityLauncher> {
  private final Provider<Application> applicationProvider;
  private final Provider<Plotter> plotterProvider;
  private final Provider<ErrorReporter> errorReporterProvider;
  private final Provider<Display> displayProvider;
  private final Provider<VariablesRegistry> variablesRegistryProvider;
  private final Provider<Notifier> notifierProvider;

  public ActivityLauncher_MembersInjector(Provider<Application> applicationProvider, Provider<Plotter> plotterProvider, Provider<ErrorReporter> errorReporterProvider, Provider<Display> displayProvider, Provider<VariablesRegistry> variablesRegistryProvider, Provider<Notifier> notifierProvider) {  
    assert applicationProvider != null;
    this.applicationProvider = applicationProvider;
    assert plotterProvider != null;
    this.plotterProvider = plotterProvider;
    assert errorReporterProvider != null;
    this.errorReporterProvider = errorReporterProvider;
    assert displayProvider != null;
    this.displayProvider = displayProvider;
    assert variablesRegistryProvider != null;
    this.variablesRegistryProvider = variablesRegistryProvider;
    assert notifierProvider != null;
    this.notifierProvider = notifierProvider;
  }

  @Override
  public void injectMembers(ActivityLauncher instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    instance.application = applicationProvider.get();
    instance.plotter = DoubleCheckLazy.create(plotterProvider);
    instance.errorReporter = DoubleCheckLazy.create(errorReporterProvider);
    instance.display = DoubleCheckLazy.create(displayProvider);
    instance.variablesRegistry = DoubleCheckLazy.create(variablesRegistryProvider);
    instance.notifier = notifierProvider.get();
  }

  public static MembersInjector<ActivityLauncher> create(Provider<Application> applicationProvider, Provider<Plotter> plotterProvider, Provider<ErrorReporter> errorReporterProvider, Provider<Display> displayProvider, Provider<VariablesRegistry> variablesRegistryProvider, Provider<Notifier> notifierProvider) {  
      return new ActivityLauncher_MembersInjector(applicationProvider, plotterProvider, errorReporterProvider, displayProvider, variablesRegistryProvider, notifierProvider);
  }
}

