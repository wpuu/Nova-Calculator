package org.solovyev.android.calculator.variables;

import com.squareup.otto.Bus;
import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;
import jscl.math.function.IConstant;
import org.solovyev.android.calculator.Calculator;
import org.solovyev.android.calculator.VariablesRegistry;
import org.solovyev.android.calculator.entities.BaseEntitiesFragment;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class VariablesFragment_MembersInjector implements MembersInjector<VariablesFragment> {
  private final MembersInjector<BaseEntitiesFragment<IConstant>> supertypeInjector;
  private final Provider<VariablesRegistry> registryProvider;
  private final Provider<Calculator> calculatorProvider;
  private final Provider<Bus> busProvider;

  public VariablesFragment_MembersInjector(MembersInjector<BaseEntitiesFragment<IConstant>> supertypeInjector, Provider<VariablesRegistry> registryProvider, Provider<Calculator> calculatorProvider, Provider<Bus> busProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert registryProvider != null;
    this.registryProvider = registryProvider;
    assert calculatorProvider != null;
    this.calculatorProvider = calculatorProvider;
    assert busProvider != null;
    this.busProvider = busProvider;
  }

  @Override
  public void injectMembers(VariablesFragment instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.registry = registryProvider.get();
    instance.calculator = calculatorProvider.get();
    instance.bus = busProvider.get();
  }

  public static MembersInjector<VariablesFragment> create(MembersInjector<BaseEntitiesFragment<IConstant>> supertypeInjector, Provider<VariablesRegistry> registryProvider, Provider<Calculator> calculatorProvider, Provider<Bus> busProvider) {  
      return new VariablesFragment_MembersInjector(supertypeInjector, registryProvider, calculatorProvider, busProvider);
  }
}

