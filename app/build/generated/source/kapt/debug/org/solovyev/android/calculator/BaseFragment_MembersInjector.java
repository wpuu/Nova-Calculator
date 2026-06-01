package org.solovyev.android.calculator;

import android.graphics.Typeface;
import androidx.fragment.app.Fragment;
import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.ads.AdUi;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class BaseFragment_MembersInjector implements MembersInjector<BaseFragment> {
  private final MembersInjector<Fragment> supertypeInjector;
  private final Provider<AdUi> adUiProvider;
  private final Provider<Typeface> typefaceProvider;

  public BaseFragment_MembersInjector(MembersInjector<Fragment> supertypeInjector, Provider<AdUi> adUiProvider, Provider<Typeface> typefaceProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert adUiProvider != null;
    this.adUiProvider = adUiProvider;
    assert typefaceProvider != null;
    this.typefaceProvider = typefaceProvider;
  }

  @Override
  public void injectMembers(BaseFragment instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.adUi = adUiProvider.get();
    instance.typeface = typefaceProvider.get();
  }

  public static MembersInjector<BaseFragment> create(MembersInjector<Fragment> supertypeInjector, Provider<AdUi> adUiProvider, Provider<Typeface> typefaceProvider) {  
      return new BaseFragment_MembersInjector(supertypeInjector, adUiProvider, typefaceProvider);
  }
}

