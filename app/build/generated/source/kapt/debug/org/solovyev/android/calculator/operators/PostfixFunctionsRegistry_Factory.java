package org.solovyev.android.calculator.operators;

import dagger.MembersInjector;
import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;
import jscl.JsclMathEngine;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class PostfixFunctionsRegistry_Factory implements Factory<PostfixFunctionsRegistry> {
  private final MembersInjector<PostfixFunctionsRegistry> membersInjector;
  private final Provider<JsclMathEngine> mathEngineProvider;

  public PostfixFunctionsRegistry_Factory(MembersInjector<PostfixFunctionsRegistry> membersInjector, Provider<JsclMathEngine> mathEngineProvider) {  
    assert membersInjector != null;
    this.membersInjector = membersInjector;
    assert mathEngineProvider != null;
    this.mathEngineProvider = mathEngineProvider;
  }

  @Override
  public PostfixFunctionsRegistry get() {  
    PostfixFunctionsRegistry instance = new PostfixFunctionsRegistry(mathEngineProvider.get());
    membersInjector.injectMembers(instance);
    return instance;
  }

  public static Factory<PostfixFunctionsRegistry> create(MembersInjector<PostfixFunctionsRegistry> membersInjector, Provider<JsclMathEngine> mathEngineProvider) {  
    return new PostfixFunctionsRegistry_Factory(membersInjector, mathEngineProvider);
  }
}

