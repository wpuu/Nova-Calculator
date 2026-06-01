package org.solovyev.android.calculator.functions;

import android.graphics.Typeface;
import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.BaseDialogFragment;
import org.solovyev.android.calculator.Calculator;
import org.solovyev.android.calculator.Keyboard;
import org.solovyev.android.calculator.VariablesRegistry;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class BaseFunctionFragment_MembersInjector implements MembersInjector<BaseFunctionFragment> {
  private final MembersInjector<BaseDialogFragment> supertypeInjector;
  private final Provider<Calculator> calculatorProvider;
  private final Provider<Keyboard> keyboardProvider;
  private final Provider<Typeface> typefaceProvider;
  private final Provider<FunctionsRegistry> functionsRegistryProvider;
  private final Provider<VariablesRegistry> variablesRegistryProvider;

  public BaseFunctionFragment_MembersInjector(MembersInjector<BaseDialogFragment> supertypeInjector, Provider<Calculator> calculatorProvider, Provider<Keyboard> keyboardProvider, Provider<Typeface> typefaceProvider, Provider<FunctionsRegistry> functionsRegistryProvider, Provider<VariablesRegistry> variablesRegistryProvider) {  
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
  }

  @Override
  public void injectMembers(BaseFunctionFragment instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.calculator = calculatorProvider.get();
    instance.keyboard = keyboardProvider.get();
    instance.typeface = typefaceProvider.get();
    instance.functionsRegistry = functionsRegistryProvider.get();
    instance.variablesRegistry = variablesRegistryProvider.get();
  }

  public static MembersInjector<BaseFunctionFragment> create(MembersInjector<BaseDialogFragment> supertypeInjector, Provider<Calculator> calculatorProvider, Provider<Keyboard> keyboardProvider, Provider<Typeface> typefaceProvider, Provider<FunctionsRegistry> functionsRegistryProvider, Provider<VariablesRegistry> variablesRegistryProvider) {  
      return new BaseFunctionFragment_MembersInjector(supertypeInjector, calculatorProvider, keyboardProvider, typefaceProvider, functionsRegistryProvider, variablesRegistryProvider);
  }
}

