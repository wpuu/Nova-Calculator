package org.solovyev.android.calculator;

import android.content.BroadcastReceiver;
import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.history.History;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class WidgetReceiver_MembersInjector implements MembersInjector<WidgetReceiver> {
  private final MembersInjector<BroadcastReceiver> supertypeInjector;
  private final Provider<Keyboard> keyboardProvider;
  private final Provider<History> historyProvider;

  public WidgetReceiver_MembersInjector(MembersInjector<BroadcastReceiver> supertypeInjector, Provider<Keyboard> keyboardProvider, Provider<History> historyProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert keyboardProvider != null;
    this.keyboardProvider = keyboardProvider;
    assert historyProvider != null;
    this.historyProvider = historyProvider;
  }

  @Override
  public void injectMembers(WidgetReceiver instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.keyboard = keyboardProvider.get();
    instance.history = historyProvider.get();
  }

  public static MembersInjector<WidgetReceiver> create(MembersInjector<BroadcastReceiver> supertypeInjector, Provider<Keyboard> keyboardProvider, Provider<History> historyProvider) {  
      return new WidgetReceiver_MembersInjector(supertypeInjector, keyboardProvider, historyProvider);
  }
}

