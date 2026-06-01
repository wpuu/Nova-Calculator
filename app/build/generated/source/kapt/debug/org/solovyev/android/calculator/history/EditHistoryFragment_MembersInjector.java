package org.solovyev.android.calculator.history;

import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.BaseDialogFragment;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class EditHistoryFragment_MembersInjector implements MembersInjector<EditHistoryFragment> {
  private final MembersInjector<BaseDialogFragment> supertypeInjector;
  private final Provider<History> historyProvider;

  public EditHistoryFragment_MembersInjector(MembersInjector<BaseDialogFragment> supertypeInjector, Provider<History> historyProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert historyProvider != null;
    this.historyProvider = historyProvider;
  }

  @Override
  public void injectMembers(EditHistoryFragment instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.history = historyProvider.get();
  }

  public static MembersInjector<EditHistoryFragment> create(MembersInjector<BaseDialogFragment> supertypeInjector, Provider<History> historyProvider) {  
      return new EditHistoryFragment_MembersInjector(supertypeInjector, historyProvider);
  }
}

