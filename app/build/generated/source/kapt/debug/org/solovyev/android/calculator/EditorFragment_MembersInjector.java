package org.solovyev.android.calculator;

import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class EditorFragment_MembersInjector implements MembersInjector<EditorFragment> {
  private final MembersInjector<BaseFragment> supertypeInjector;
  private final Provider<Editor> editorProvider;

  public EditorFragment_MembersInjector(MembersInjector<BaseFragment> supertypeInjector, Provider<Editor> editorProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert editorProvider != null;
    this.editorProvider = editorProvider;
  }

  @Override
  public void injectMembers(EditorFragment instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.editor = editorProvider.get();
  }

  public static MembersInjector<EditorFragment> create(MembersInjector<BaseFragment> supertypeInjector, Provider<Editor> editorProvider) {  
      return new EditorFragment_MembersInjector(supertypeInjector, editorProvider);
  }
}

