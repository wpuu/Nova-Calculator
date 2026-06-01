package org.solovyev.android.calculator.memory;

import com.squareup.otto.Bus;
import dagger.MembersInjector;
import java.util.concurrent.Executor;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.Notifier;
import org.solovyev.android.calculator.ToJsclTextProcessor;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class Memory_MembersInjector implements MembersInjector<Memory> {
  private final Provider<Notifier> notifierProvider;
  private final Provider<ToJsclTextProcessor> jsclProcessorProvider;
  private final Provider<Executor> backgroundThreadProvider;
  private final Provider<Bus> busProvider;

  public Memory_MembersInjector(Provider<Notifier> notifierProvider, Provider<ToJsclTextProcessor> jsclProcessorProvider, Provider<Executor> backgroundThreadProvider, Provider<Bus> busProvider) {  
    assert notifierProvider != null;
    this.notifierProvider = notifierProvider;
    assert jsclProcessorProvider != null;
    this.jsclProcessorProvider = jsclProcessorProvider;
    assert backgroundThreadProvider != null;
    this.backgroundThreadProvider = backgroundThreadProvider;
    assert busProvider != null;
    this.busProvider = busProvider;
  }

  @Override
  public void injectMembers(Memory instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    instance.notifier = notifierProvider.get();
    instance.jsclProcessor = jsclProcessorProvider.get();
    instance.backgroundThread = backgroundThreadProvider.get();
    instance.bus = busProvider.get();
  }

  public static MembersInjector<Memory> create(Provider<Notifier> notifierProvider, Provider<ToJsclTextProcessor> jsclProcessorProvider, Provider<Executor> backgroundThreadProvider, Provider<Bus> busProvider) {  
      return new Memory_MembersInjector(notifierProvider, jsclProcessorProvider, backgroundThreadProvider, busProvider);
  }
}

