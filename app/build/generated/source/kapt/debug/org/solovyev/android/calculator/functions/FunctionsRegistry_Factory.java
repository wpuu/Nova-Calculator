package org.solovyev.android.calculator.functions;

import dagger.MembersInjector;
import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;
import jscl.JsclMathEngine;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class FunctionsRegistry_Factory implements Factory<FunctionsRegistry> {
  private final MembersInjector<FunctionsRegistry> membersInjector;
  private final Provider<JsclMathEngine> mathEngineProvider;

  public FunctionsRegistry_Factory(MembersInjector<FunctionsRegistry> membersInjector, Provider<JsclMathEngine> mathEngineProvider) {  
    assert membersInjector != null;
    this.membersInjector = membersInjector;
    assert mathEngineProvider != null;
    this.mathEngineProvider = mathEngineProvider;
  }

  @Override
  public FunctionsRegistry get() {  
    FunctionsRegistry instance = new FunctionsRegistry(mathEngineProvider.get());
    membersInjector.injectMembers(instance);
    return instance;
  }

  public static Factory<FunctionsRegistry> create(MembersInjector<FunctionsRegistry> membersInjector, Provider<JsclMathEngine> mathEngineProvider) {  
    return new FunctionsRegistry_Factory(membersInjector, mathEngineProvider);
  }
}

