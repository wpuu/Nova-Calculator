package org.solovyev.android.calculator;

import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.keyboard.KeyboardUi;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class KeyboardFragment_MembersInjector implements MembersInjector<KeyboardFragment> {
  private final MembersInjector<BaseFragment> supertypeInjector;
  private final Provider<KeyboardUi> keyboardUiProvider;

  public KeyboardFragment_MembersInjector(MembersInjector<BaseFragment> supertypeInjector, Provider<KeyboardUi> keyboardUiProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert keyboardUiProvider != null;
    this.keyboardUiProvider = keyboardUiProvider;
  }

  @Override
  public void injectMembers(KeyboardFragment instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.keyboardUi = keyboardUiProvider.get();
  }

  public static MembersInjector<KeyboardFragment> create(MembersInjector<BaseFragment> supertypeInjector, Provider<KeyboardUi> keyboardUiProvider) {  
      return new KeyboardFragment_MembersInjector(supertypeInjector, keyboardUiProvider);
  }
}

