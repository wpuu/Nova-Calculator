package org.solovyev.android.calculator;

import android.app.Application;
import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class Clipboard_Factory implements Factory<Clipboard> {
  private final Provider<Application> applicationProvider;

  public Clipboard_Factory(Provider<Application> applicationProvider) {  
    assert applicationProvider != null;
    this.applicationProvider = applicationProvider;
  }

  @Override
  public Clipboard get() {  
    return new Clipboard(applicationProvider.get());
  }

  public static Factory<Clipboard> create(Provider<Application> applicationProvider) {  
    return new Clipboard_Factory(applicationProvider);
  }
}

