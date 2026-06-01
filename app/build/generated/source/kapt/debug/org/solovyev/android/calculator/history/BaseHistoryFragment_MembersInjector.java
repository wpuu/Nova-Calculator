package org.solovyev.android.calculator.history;

import android.graphics.Typeface;
import com.squareup.otto.Bus;
import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.BaseFragment;
import org.solovyev.android.calculator.Editor;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class BaseHistoryFragment_MembersInjector implements MembersInjector<BaseHistoryFragment> {
  private final MembersInjector<BaseFragment> supertypeInjector;
  private final Provider<History> historyProvider;
  private final Provider<Editor> editorProvider;
  private final Provider<Bus> busProvider;
  private final Provider<Typeface> typefaceProvider;

  public BaseHistoryFragment_MembersInjector(MembersInjector<BaseFragment> supertypeInjector, Provider<History> historyProvider, Provider<Editor> editorProvider, Provider<Bus> busProvider, Provider<Typeface> typefaceProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert historyProvider != null;
    this.historyProvider = historyProvider;
    assert editorProvider != null;
    this.editorProvider = editorProvider;
    assert busProvider != null;
    this.busProvider = busProvider;
    assert typefaceProvider != null;
    this.typefaceProvider = typefaceProvider;
  }

  @Override
  public void injectMembers(BaseHistoryFragment instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.history = historyProvider.get();
    instance.editor = editorProvider.get();
    instance.bus = busProvider.get();
    instance.typeface = typefaceProvider.get();
  }

  public static MembersInjector<BaseHistoryFragment> create(MembersInjector<BaseFragment> supertypeInjector, Provider<History> historyProvider, Provider<Editor> editorProvider, Provider<Bus> busProvider, Provider<Typeface> typefaceProvider) {  
      return new BaseHistoryFragment_MembersInjector(supertypeInjector, historyProvider, editorProvider, busProvider, typefaceProvider);
  }
}

