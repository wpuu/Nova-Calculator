package org.solovyev.android.calculator.variables;

import android.graphics.Typeface;
import dagger.MembersInjector;
import dagger.internal.DoubleCheckLazy;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.BaseDialogFragment;
import org.solovyev.android.calculator.Calculator;
import org.solovyev.android.calculator.Engine;
import org.solovyev.android.calculator.Keyboard;
import org.solovyev.android.calculator.ToJsclTextProcessor;
import org.solovyev.android.calculator.VariablesRegistry;
import org.solovyev.android.calculator.functions.FunctionsRegistry;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class EditVariableFragment_MembersInjector implements MembersInjector<EditVariableFragment> {
  private final MembersInjector<BaseDialogFragment> supertypeInjector;
  private final Provider<Calculator> calculatorProvider;
  private final Provider<Keyboard> keyboardProvider;
  private final Provider<Typeface> typefaceProvider;
  private final Provider<FunctionsRegistry> functionsRegistryProvider;
  private final Provider<VariablesRegistry> variablesRegistryProvider;
  private final Provider<ToJsclTextProcessor> toJsclTextProcessorProvider;
  private final Provider<Engine> engineProvider;

  public EditVariableFragment_MembersInjector(MembersInjector<BaseDialogFragment> supertypeInjector, Provider<Calculator> calculatorProvider, Provider<Keyboard> keyboardProvider, Provider<Typeface> typefaceProvider, Provider<FunctionsRegistry> functionsRegistryProvider, Provider<VariablesRegistry> variablesRegistryProvider, Provider<ToJsclTextProcessor> toJsclTextProcessorProvider, Provider<Engine> engineProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert calculatorProvider != null;
    this.calculatorProvider = calculatorProvider;
    assert keyboardProvider != null;
    this.keyboardProvider = keyboardProvider;
    assert typefaceProvider != null;
    this.typefaceProvider = typefaceProvider;
    assert functionsRegistryProvider != null;
    this.functionsRegistryProvider = functionsRegistryProvider;
    assert variablesRegistryProvider != null;
    this.variablesRegistryProvider = variablesRegistryProvider;
    assert toJsclTextProcessorProvider != null;
    this.toJsclTextProcessorProvider = toJsclTextProcessorProvider;
    assert engineProvider != null;
    this.engineProvider = engineProvider;
  }

  @Override
  public void injectMembers(EditVariableFragment instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.calculator = calculatorProvider.get();
    instance.keyboard = keyboardProvider.get();
    instance.typeface = typefaceProvider.get();
    instance.functionsRegistry = functionsRegistryProvider.get();
    instance.variablesRegistry = variablesRegistryProvider.get();
    instance.toJsclTextProcessor = DoubleCheckLazy.create(toJsclTextProcessorProvider);
    instance.engine = engineProvider.get();
  }

  public static MembersInjector<EditVariableFragment> create(MembersInjector<BaseDialogFragment> supertypeInjector, Provider<Calculator> calculatorProvider, Provider<Keyboard> keyboardProvider, Provider<Typeface> typefaceProvider, Provider<FunctionsRegistry> functionsRegistryProvider, Provider<VariablesRegistry> variablesRegistryProvider, Provider<ToJsclTextProcessor> toJsclTextProcessorProvider, Provider<Engine> engineProvider) {  
      return new EditVariableFragment_MembersInjector(supertypeInjector, calculatorProvider, keyboardProvider, typefaceProvider, functionsRegistryProvider, variablesRegistryProvider, toJsclTextProcessorProvider, engineProvider);
  }
}

