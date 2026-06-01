package org.solovyev.android.calculator.keyboard;

import com.squareup.otto.Bus;
import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.Display;
import org.solovyev.android.calculator.Engine;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class KeyboardUi_MembersInjector implements MembersInjector<KeyboardUi> {
  private final MembersInjector<BaseKeyboardUi> supertypeInjector;
  private final Provider<Engine> engineProvider;
  private final Provider<Display> displayProvider;
  private final Provider<Bus> busProvider;
  private final Provider<PartialKeyboardUi> partialUiProvider;

  public KeyboardUi_MembersInjector(MembersInjector<BaseKeyboardUi> supertypeInjector, Provider<Engine> engineProvider, Provider<Display> displayProvider, Provider<Bus> busProvider, Provider<PartialKeyboardUi> partialUiProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert engineProvider != null;
    this.engineProvider = engineProvider;
    assert displayProvider != null;
    this.displayProvider = displayProvider;
    assert busProvider != null;
    this.busProvider = busProvider;
    assert partialUiProvider != null;
    this.partialUiProvider = partialUiProvider;
  }

  @Override
  public void injectMembers(KeyboardUi instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.engine = engineProvider.get();
    instance.display = displayProvider.get();
    instance.bus = busProvider.get();
    instance.partialUi = partialUiProvider.get();
  }

  public static MembersInjector<KeyboardUi> create(MembersInjector<BaseKeyboardUi> supertypeInjector, Provider<Engine> engineProvider, Provider<Display> displayProvider, Provider<Bus> busProvider, Provider<PartialKeyboardUi> partialUiProvider) {  
      return new KeyboardUi_MembersInjector(supertypeInjector, engineProvider, displayProvider, busProvider, partialUiProvider);
  }
}

