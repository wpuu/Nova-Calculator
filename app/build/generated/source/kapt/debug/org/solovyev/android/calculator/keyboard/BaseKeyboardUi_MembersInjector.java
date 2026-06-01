package org.solovyev.android.calculator.keyboard;

import android.content.SharedPreferences;
import dagger.MembersInjector;
import dagger.internal.DoubleCheckLazy;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.ActivityLauncher;
import org.solovyev.android.calculator.Calculator;
import org.solovyev.android.calculator.Editor;
import org.solovyev.android.calculator.Keyboard;
import org.solovyev.android.calculator.memory.Memory;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class BaseKeyboardUi_MembersInjector implements MembersInjector<BaseKeyboardUi> {
  private final Provider<SharedPreferences> preferencesProvider;
  private final Provider<Keyboard> keyboardProvider;
  private final Provider<Editor> editorProvider;
  private final Provider<Calculator> calculatorProvider;
  private final Provider<ActivityLauncher> launcherProvider;
  private final Provider<Memory> memoryProvider;

  public BaseKeyboardUi_MembersInjector(Provider<SharedPreferences> preferencesProvider, Provider<Keyboard> keyboardProvider, Provider<Editor> editorProvider, Provider<Calculator> calculatorProvider, Provider<ActivityLauncher> launcherProvider, Provider<Memory> memoryProvider) {  
    assert preferencesProvider != null;
    this.preferencesProvider = preferencesProvider;
    assert keyboardProvider != null;
    this.keyboardProvider = keyboardProvider;
    assert editorProvider != null;
    this.editorProvider = editorProvider;
    assert calculatorProvider != null;
    this.calculatorProvider = calculatorProvider;
    assert launcherProvider != null;
    this.launcherProvider = launcherProvider;
    assert memoryProvider != null;
    this.memoryProvider = memoryProvider;
  }

  @Override
  public void injectMembers(BaseKeyboardUi instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    instance.preferences = preferencesProvider.get();
    instance.keyboard = keyboardProvider.get();
    instance.editor = editorProvider.get();
    instance.calculator = calculatorProvider.get();
    instance.launcher = launcherProvider.get();
    instance.memory = DoubleCheckLazy.create(memoryProvider);
  }

  public static MembersInjector<BaseKeyboardUi> create(Provider<SharedPreferences> preferencesProvider, Provider<Keyboard> keyboardProvider, Provider<Editor> editorProvider, Provider<Calculator> calculatorProvider, Provider<ActivityLauncher> launcherProvider, Provider<Memory> memoryProvider) {  
      return new BaseKeyboardUi_MembersInjector(preferencesProvider, keyboardProvider, editorProvider, calculatorProvider, launcherProvider, memoryProvider);
  }
}

