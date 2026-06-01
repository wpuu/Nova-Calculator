package org.solovyev.android.calculator.entities;

import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.BaseFragment;
import org.solovyev.android.calculator.Keyboard;
import org.solovyev.common.math.MathEntity;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class BaseEntitiesFragment_MembersInjector<E extends MathEntity> implements MembersInjector<BaseEntitiesFragment<E>> {
  private final MembersInjector<BaseFragment> supertypeInjector;
  private final Provider<Keyboard> keyboardProvider;

  public BaseEntitiesFragment_MembersInjector(MembersInjector<BaseFragment> supertypeInjector, Provider<Keyboard> keyboardProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert keyboardProvider != null;
    this.keyboardProvider = keyboardProvider;
  }

  @Override
  public void injectMembers(BaseEntitiesFragment<E> instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.keyboard = keyboardProvider.get();
  }

  public static <E extends MathEntity> MembersInjector<BaseEntitiesFragment<E>> create(MembersInjector<BaseFragment> supertypeInjector, Provider<Keyboard> keyboardProvider) {  
      return new BaseEntitiesFragment_MembersInjector<E>(supertypeInjector, keyboardProvider);
  }
}

