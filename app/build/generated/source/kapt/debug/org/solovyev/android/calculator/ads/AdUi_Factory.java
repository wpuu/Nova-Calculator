package org.solovyev.android.calculator.ads;

import android.os.Handler;
import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.checkout.CppCheckout;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class AdUi_Factory implements Factory<AdUi> {
  private final Provider<CppCheckout> checkoutProvider;
  private final Provider<Handler> handlerProvider;

  public AdUi_Factory(Provider<CppCheckout> checkoutProvider, Provider<Handler> handlerProvider) {  
    assert checkoutProvider != null;
    this.checkoutProvider = checkoutProvider;
    assert handlerProvider != null;
    this.handlerProvider = handlerProvider;
  }

  @Override
  public AdUi get() {  
    return new AdUi(checkoutProvider.get(), handlerProvider.get());
  }

  public static Factory<AdUi> create(Provider<CppCheckout> checkoutProvider, Provider<Handler> handlerProvider) {  
    return new AdUi_Factory(checkoutProvider, handlerProvider);
  }
}

