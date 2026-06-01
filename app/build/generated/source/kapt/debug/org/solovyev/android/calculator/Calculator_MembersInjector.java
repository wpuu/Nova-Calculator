package org.solovyev.android.calculator;

import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class Calculator_MembersInjector implements MembersInjector<Calculator> {
  private final Provider<Editor> editorProvider;
  private final Provider<Engine> engineProvider;
  private final Provider<ToJsclTextProcessor> preprocessorProvider;

  public Calculator_MembersInjector(Provider<Editor> editorProvider, Provider<Engine> engineProvider, Provider<ToJsclTextProcessor> preprocessorProvider) {  
    assert editorProvider != null;
    this.editorProvider = editorProvider;
    assert engineProvider != null;
    this.engineProvider = engineProvider;
    assert preprocessorProvider != null;
    this.preprocessorProvider = preprocessorProvider;
  }

  @Override
  public void injectMembers(Calculator instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    instance.editor = editorProvider.get();
    instance.engine = engineProvider.get();
    instance.preprocessor = preprocessorProvider.get();
  }

  public static MembersInjector<Calculator> create(Provider<Editor> editorProvider, Provider<Engine> engineProvider, Provider<ToJsclTextProcessor> preprocessorProvider) {  
      return new Calculator_MembersInjector(editorProvider, engineProvider, preprocessorProvider);
  }
}

