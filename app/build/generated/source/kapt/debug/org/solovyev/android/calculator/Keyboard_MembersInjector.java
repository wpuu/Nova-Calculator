package org.solovyev.android.calculator;

import dagger.MembersInjector;
import dagger.internal.DoubleCheckLazy;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.ga.Ga;
import org.solovyev.android.calculator.history.History;
import org.solovyev.android.calculator.memory.Memory;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class Keyboard_MembersInjector implements MembersInjector<Keyboard> {
  private final Provider<Editor> editorProvider;
  private final Provider<Display> displayProvider;
  private final Provider<History> historyProvider;
  private final Provider<Memory> memoryProvider;
  private final Provider<Calculator> calculatorProvider;
  private final Provider<Engine> engineProvider;
  private final Provider<Ga> gaProvider;
  private final Provider<Clipboard> clipboardProvider;
  private final Provider<ActivityLauncher> launcherProvider;

  public Keyboard_MembersInjector(Provider<Editor> editorProvider, Provider<Display> displayProvider, Provider<History> historyProvider, Provider<Memory> memoryProvider, Provider<Calculator> calculatorProvider, Provider<Engine> engineProvider, Provider<Ga> gaProvider, Provider<Clipboard> clipboardProvider, Provider<ActivityLauncher> launcherProvider) {  
    assert editorProvider != null;
    this.editorProvider = editorProvider;
    assert displayProvider != null;
    this.displayProvider = displayProvider;
    assert historyProvider != null;
    this.historyProvider = historyProvider;
    assert memoryProvider != null;
    this.memoryProvider = memoryProvider;
    assert calculatorProvider != null;
    this.calculatorProvider = calculatorProvider;
    assert engineProvider != null;
    this.engineProvider = engineProvider;
    assert gaProvider != null;
    this.gaProvider = gaProvider;
    assert clipboardProvider != null;
    this.clipboardProvider = clipboardProvider;
    assert launcherProvider != null;
    this.launcherProvider = launcherProvider;
  }

  @Override
  public void injectMembers(Keyboard instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    instance.editor = editorProvider.get();
    instance.display = displayProvider.get();
    instance.history = historyProvider.get();
    instance.memory = DoubleCheckLazy.create(memoryProvider);
    instance.calculator = calculatorProvider.get();
    instance.engine = engineProvider.get();
    instance.ga = DoubleCheckLazy.create(gaProvider);
    instance.clipboard = DoubleCheckLazy.create(clipboardProvider);
    instance.launcher = launcherProvider.get();
  }

  public static MembersInjector<Keyboard> create(Provider<Editor> editorProvider, Provider<Display> displayProvider, Provider<History> historyProvider, Provider<Memory> memoryProvider, Provider<Calculator> calculatorProvider, Provider<Engine> engineProvider, Provider<Ga> gaProvider, Provider<Clipboard> clipboardProvider, Provider<ActivityLauncher> launcherProvider) {  
      return new Keyboard_MembersInjector(editorProvider, displayProvider, historyProvider, memoryProvider, calculatorProvider, engineProvider, gaProvider, clipboardProvider, launcherProvider);
  }
}

