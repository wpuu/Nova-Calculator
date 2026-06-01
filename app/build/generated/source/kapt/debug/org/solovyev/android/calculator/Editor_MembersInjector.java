package org.solovyev.android.calculator;

import com.squareup.otto.Bus;
import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class Editor_MembersInjector implements MembersInjector<Editor> {
  private final Provider<Bus> busProvider;

  public Editor_MembersInjector(Provider<Bus> busProvider) {  
    assert busProvider != null;
    this.busProvider = busProvider;
  }

  @Override
  public void injectMembers(Editor instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    instance.bus = busProvider.get();
  }

  public static MembersInjector<Editor> create(Provider<Bus> busProvider) {  
      return new Editor_MembersInjector(busProvider);
  }
}

