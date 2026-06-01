package org.solovyev.android.calculator;

import dagger.MembersInjector;
import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;
import jscl.JsclMathEngine;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class Engine_Factory implements Factory<Engine> {
  private final MembersInjector<Engine> membersInjector;
  private final Provider<JsclMathEngine> mathEngineProvider;

  public Engine_Factory(MembersInjector<Engine> membersInjector, Provider<JsclMathEngine> mathEngineProvider) {  
    assert membersInjector != null;
    this.membersInjector = membersInjector;
    assert mathEngineProvider != null;
    this.mathEngineProvider = mathEngineProvider;
  }

  @Override
  public Engine get() {  
    Engine instance = new Engine(mathEngineProvider.get());
    membersInjector.injectMembers(instance);
    return instance;
  }

  public static Factory<Engine> create(MembersInjector<Engine> membersInjector, Provider<JsclMathEngine> mathEngineProvider) {  
    return new Engine_Factory(membersInjector, mathEngineProvider);
  }
}

