package org.solovyev.android.calculator.operators;

import dagger.MembersInjector;
import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;
import jscl.JsclMathEngine;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class OperatorsRegistry_Factory implements Factory<OperatorsRegistry> {
  private final MembersInjector<OperatorsRegistry> membersInjector;
  private final Provider<JsclMathEngine> mathEngineProvider;

  public OperatorsRegistry_Factory(MembersInjector<OperatorsRegistry> membersInjector, Provider<JsclMathEngine> mathEngineProvider) {  
    assert membersInjector != null;
    this.membersInjector = membersInjector;
    assert mathEngineProvider != null;
    this.mathEngineProvider = mathEngineProvider;
  }

  @Override
  public OperatorsRegistry get() {  
    OperatorsRegistry instance = new OperatorsRegistry(mathEngineProvider.get());
    membersInjector.injectMembers(instance);
    return instance;
  }

  public static Factory<OperatorsRegistry> create(MembersInjector<OperatorsRegistry> membersInjector, Provider<JsclMathEngine> mathEngineProvider) {  
    return new OperatorsRegistry_Factory(membersInjector, mathEngineProvider);
  }
}

