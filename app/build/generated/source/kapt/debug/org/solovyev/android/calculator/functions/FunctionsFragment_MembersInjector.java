package org.solovyev.android.calculator.functions;

import com.squareup.otto.Bus;
import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;
import jscl.math.function.Function;
import org.solovyev.android.calculator.Calculator;
import org.solovyev.android.calculator.entities.BaseEntitiesFragment;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class FunctionsFragment_MembersInjector implements MembersInjector<FunctionsFragment> {
  private final MembersInjector<BaseEntitiesFragment<Function>> supertypeInjector;
  private final Provider<FunctionsRegistry> registryProvider;
  private final Provider<Calculator> calculatorProvider;
  private final Provider<Bus> busProvider;

  public FunctionsFragment_MembersInjector(MembersInjector<BaseEntitiesFragment<Function>> supertypeInjector, Provider<FunctionsRegistry> registryProvider, Provider<Calculator> calculatorProvider, Provider<Bus> busProvider) {  
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
  public void injectMembers(FunctionsFragment instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.registry = registryProvider.get();
    instance.calculator = calculatorProvider.get();
    instance.bus = busProvider.get();
  }

  public static MembersInjector<FunctionsFragment> create(MembersInjector<BaseEntitiesFragment<Function>> supertypeInjector, Provider<FunctionsRegistry> registryProvider, Provider<Calculator> calculatorProvider, Provider<Bus> busProvider) {  
      return new FunctionsFragment_MembersInjector(supertypeInjector, registryProvider, calculatorProvider, busProvider);
  }
}

