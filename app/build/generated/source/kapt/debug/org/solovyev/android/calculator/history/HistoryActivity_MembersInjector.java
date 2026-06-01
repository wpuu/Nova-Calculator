package org.solovyev.android.calculator.history;

import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.BaseActivity;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class HistoryActivity_MembersInjector implements MembersInjector<HistoryActivity> {
  private final MembersInjector<BaseActivity> supertypeInjector;
  private final Provider<History> historyProvider;

  public HistoryActivity_MembersInjector(MembersInjector<BaseActivity> supertypeInjector, Provider<History> historyProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert historyProvider != null;
    this.historyProvider = historyProvider;
  }

  @Override
  public void injectMembers(HistoryActivity instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.history = historyProvider.get();
  }

  public static MembersInjector<HistoryActivity> create(MembersInjector<BaseActivity> supertypeInjector, Provider<History> historyProvider) {  
      return new HistoryActivity_MembersInjector(supertypeInjector, historyProvider);
  }
}

