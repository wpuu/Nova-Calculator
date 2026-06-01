package org.solovyev.android.calculator;

import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class ToJsclTextProcessor_MembersInjector implements MembersInjector<ToJsclTextProcessor> {
  private final Provider<Engine> engineProvider;

  public ToJsclTextProcessor_MembersInjector(Provider<Engine> engineProvider) {  
    assert engineProvider != null;
    this.engineProvider = engineProvider;
  }

  @Override
  public void injectMembers(ToJsclTextProcessor instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    instance.engine = engineProvider.get();
  }

  public static MembersInjector<ToJsclTextProcessor> create(Provider<Engine> engineProvider) {  
      return new ToJsclTextProcessor_MembersInjector(engineProvider);
  }
}

