package org.solovyev.android.calculator;

import android.app.Application;
import android.os.Handler;
import com.squareup.otto.Bus;
import dagger.MembersInjector;
import dagger.internal.DoubleCheckLazy;
import java.util.concurrent.Executor;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.ga.Ga;
import org.solovyev.android.calculator.history.History;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class CalculatorApplication_MembersInjector implements MembersInjector<CalculatorApplication> {
  private final MembersInjector<Application> supertypeInjector;
  private final Provider<Executor> initThreadProvider;
  private final Provider<Executor> uiThreadProvider;
  private final Provider<Handler> handlerProvider;
  private final Provider<Editor> editorProvider;
  private final Provider<Display> displayProvider;
  private final Provider<Bus> busProvider;
  private final Provider<Calculator> calculatorProvider;
  private final Provider<Engine> engineProvider;
  private final Provider<Keyboard> keyboardProvider;
  private final Provider<History> historyProvider;
  private final Provider<Broadcaster> broadcasterProvider;
  private final Provider<ErrorReporter> errorReporterProvider;
  private final Provider<ActivityLauncher> launcherProvider;
  private final Provider<Ga> gaProvider;

  public CalculatorApplication_MembersInjector(MembersInjector<Application> supertypeInjector, Provider<Executor> initThreadProvider, Provider<Executor> uiThreadProvider, Provider<Handler> handlerProvider, Provider<Editor> editorProvider, Provider<Display> displayProvider, Provider<Bus> busProvider, Provider<Calculator> calculatorProvider, Provider<Engine> engineProvider, Provider<Keyboard> keyboardProvider, Provider<History> historyProvider, Provider<Broadcaster> broadcasterProvider, Provider<ErrorReporter> errorReporterProvider, Provider<ActivityLauncher> launcherProvider, Provider<Ga> gaProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert initThreadProvider != null;
    this.initThreadProvider = initThreadProvider;
    assert uiThreadProvider != null;
    this.uiThreadProvider = uiThreadProvider;
    assert handlerProvider != null;
    this.handlerProvider = handlerProvider;
    assert editorProvider != null;
    this.editorProvider = editorProvider;
    assert displayProvider != null;
    this.displayProvider = displayProvider;
    assert busProvider != null;
    this.busProvider = busProvider;
    assert calculatorProvider != null;
    this.calculatorProvider = calculatorProvider;
    assert engineProvider != null;
    this.engineProvider = engineProvider;
    assert keyboardProvider != null;
    this.keyboardProvider = keyboardProvider;
    assert historyProvider != null;
    this.historyProvider = historyProvider;
    assert broadcasterProvider != null;
    this.broadcasterProvider = broadcasterProvider;
    assert errorReporterProvider != null;
    this.errorReporterProvider = errorReporterProvider;
    assert launcherProvider != null;
    this.launcherProvider = launcherProvider;
    assert gaProvider != null;
    this.gaProvider = gaProvider;
  }

  @Override
  public void injectMembers(CalculatorApplication instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.initThread = initThreadProvider.get();
    instance.uiThread = uiThreadProvider.get();
    instance.handler = handlerProvider.get();
    instance.editor = editorProvider.get();
    instance.display = displayProvider.get();
    instance.bus = busProvider.get();
    instance.calculator = calculatorProvider.get();
    instance.engine = engineProvider.get();
    instance.keyboard = keyboardProvider.get();
    instance.history = historyProvider.get();
    instance.broadcaster = broadcasterProvider.get();
    instance.errorReporter = errorReporterProvider.get();
    instance.launcher = launcherProvider.get();
    instance.ga = DoubleCheckLazy.create(gaProvider);
  }

  public static MembersInjector<CalculatorApplication> create(MembersInjector<Application> supertypeInjector, Provider<Executor> initThreadProvider, Provider<Executor> uiThreadProvider, Provider<Handler> handlerProvider, Provider<Editor> editorProvider, Provider<Display> displayProvider, Provider<Bus> busProvider, Provider<Calculator> calculatorProvider, Provider<Engine> engineProvider, Provider<Keyboard> keyboardProvider, Provider<History> historyProvider, Provider<Broadcaster> broadcasterProvider, Provider<ErrorReporter> errorReporterProvider, Provider<ActivityLauncher> launcherProvider, Provider<Ga> gaProvider) {  
      return new CalculatorApplication_MembersInjector(supertypeInjector, initThreadProvider, uiThreadProvider, handlerProvider, editorProvider, displayProvider, busProvider, calculatorProvider, engineProvider, keyboardProvider, historyProvider, broadcasterProvider, errorReporterProvider, launcherProvider, gaProvider);
  }
}

