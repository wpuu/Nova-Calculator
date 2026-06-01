package org.solovyev.android.calculator;

import dagger.MembersInjector;
import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;
import jscl.JsclMathEngine;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class VariablesRegistry_Factory implements Factory<VariablesRegistry> {
  private final MembersInjector<VariablesRegistry> membersInjector;
  private final Provider<JsclMathEngine> mathEngineProvider;

  public VariablesRegistry_Factory(MembersInjector<VariablesRegistry> membersInjector, Provider<JsclMathEngine> mathEngineProvider) {  
    assert membersInjector != null;
    this.membersInjector = membersInjector;
    assert mathEngineProvider != null;
    this.mathEngineProvider = mathEngineProvider;
  }

  @Override
  public VariablesRegistry get() {  
    VariablesRegistry instance = new VariablesRegistry(mathEngineProvider.get());
    membersInjector.injectMembers(instance);
    return instance;
  }

  public static Factory<VariablesRegistry> create(MembersInjector<VariablesRegistry> membersInjector, Provider<JsclMathEngine> mathEngineProvider) {  
    return new VariablesRegistry_Factory(membersInjector, mathEngineProvider);
  }
}

