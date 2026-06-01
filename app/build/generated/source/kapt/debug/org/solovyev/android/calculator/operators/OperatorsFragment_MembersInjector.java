package org.solovyev.android.calculator.operators;

import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;
import jscl.math.operator.Operator;
import org.solovyev.android.calculator.entities.BaseEntitiesFragment;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class OperatorsFragment_MembersInjector implements MembersInjector<OperatorsFragment> {
  private final MembersInjector<BaseEntitiesFragment<Operator>> supertypeInjector;
  private final Provider<OperatorsRegistry> operatorsRegistryProvider;
  private final Provider<PostfixFunctionsRegistry> postfixFunctionsRegistryProvider;

  public OperatorsFragment_MembersInjector(MembersInjector<BaseEntitiesFragment<Operator>> supertypeInjector, Provider<OperatorsRegistry> operatorsRegistryProvider, Provider<PostfixFunctionsRegistry> postfixFunctionsRegistryProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert operatorsRegistryProvider != null;
    this.operatorsRegistryProvider = operatorsRegistryProvider;
    assert postfixFunctionsRegistryProvider != null;
    this.postfixFunctionsRegistryProvider = postfixFunctionsRegistryProvider;
  }

  @Override
  public void injectMembers(OperatorsFragment instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.operatorsRegistry = operatorsRegistryProvider.get();
    instance.postfixFunctionsRegistry = postfixFunctionsRegistryProvider.get();
  }

  public static MembersInjector<OperatorsFragment> create(MembersInjector<BaseEntitiesFragment<Operator>> supertypeInjector, Provider<OperatorsRegistry> operatorsRegistryProvider, Provider<PostfixFunctionsRegistry> postfixFunctionsRegistryProvider) {  
      return new OperatorsFragment_MembersInjector(supertypeInjector, operatorsRegistryProvider, postfixFunctionsRegistryProvider);
  }
}

