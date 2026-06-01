package org.solovyev.android.calculator;

import android.content.SharedPreferences;
import com.squareup.otto.Bus;
import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.functions.FunctionsRegistry;
import org.solovyev.android.calculator.operators.OperatorsRegistry;
import org.solovyev.android.calculator.operators.PostfixFunctionsRegistry;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class Engine_MembersInjector implements MembersInjector<Engine> {
  private final Provider<SharedPreferences> preferencesProvider;
  private final Provider<Bus> busProvider;
  private final Provider<ErrorReporter> errorReporterProvider;
  private final Provider<FunctionsRegistry> functionsRegistryProvider;
  private final Provider<VariablesRegistry> variablesRegistryProvider;
  private final Provider<OperatorsRegistry> operatorsRegistryProvider;
  private final Provider<PostfixFunctionsRegistry> postfixFunctionsRegistryProvider;

  public Engine_MembersInjector(Provider<SharedPreferences> preferencesProvider, Provider<Bus> busProvider, Provider<ErrorReporter> errorReporterProvider, Provider<FunctionsRegistry> functionsRegistryProvider, Provider<VariablesRegistry> variablesRegistryProvider, Provider<OperatorsRegistry> operatorsRegistryProvider, Provider<PostfixFunctionsRegistry> postfixFunctionsRegistryProvider) {  
    assert preferencesProvider != null;
    this.preferencesProvider = preferencesProvider;
    assert busProvider != null;
    this.busProvider = busProvider;
    assert errorReporterProvider != null;
    this.errorReporterProvider = errorReporterProvider;
    assert functionsRegistryProvider != null;
    this.functionsRegistryProvider = functionsRegistryProvider;
    assert variablesRegistryProvider != null;
    this.variablesRegistryProvider = variablesRegistryProvider;
    assert operatorsRegistryProvider != null;
    this.operatorsRegistryProvider = operatorsRegistryProvider;
    assert postfixFunctionsRegistryProvider != null;
    this.postfixFunctionsRegistryProvider = postfixFunctionsRegistryProvider;
  }

  @Override
  public void injectMembers(Engine instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    instance.preferences = preferencesProvider.get();
    instance.bus = busProvider.get();
    instance.errorReporter = errorReporterProvider.get();
    instance.functionsRegistry = functionsRegistryProvider.get();
    instance.variablesRegistry = variablesRegistryProvider.get();
    instance.operatorsRegistry = operatorsRegistryProvider.get();
    instance.postfixFunctionsRegistry = postfixFunctionsRegistryProvider.get();
  }

  public static MembersInjector<Engine> create(Provider<SharedPreferences> preferencesProvider, Provider<Bus> busProvider, Provider<ErrorReporter> errorReporterProvider, Provider<FunctionsRegistry> functionsRegistryProvider, Provider<VariablesRegistry> variablesRegistryProvider, Provider<OperatorsRegistry> operatorsRegistryProvider, Provider<PostfixFunctionsRegistry> postfixFunctionsRegistryProvider) {  
      return new Engine_MembersInjector(preferencesProvider, busProvider, errorReporterProvider, functionsRegistryProvider, variablesRegistryProvider, operatorsRegistryProvider, postfixFunctionsRegistryProvider);
  }
}

