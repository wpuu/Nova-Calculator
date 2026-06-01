package org.solovyev.android.calculator.plot;

import android.graphics.Typeface;
import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.BaseDialogFragment;
import org.solovyev.android.plotter.Plotter;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class PlotFunctionsFragment_MembersInjector implements MembersInjector<PlotFunctionsFragment> {
  private final MembersInjector<BaseDialogFragment> supertypeInjector;
  private final Provider<Plotter> plotterProvider;
  private final Provider<Typeface> typefaceProvider;

  public PlotFunctionsFragment_MembersInjector(MembersInjector<BaseDialogFragment> supertypeInjector, Provider<Plotter> plotterProvider, Provider<Typeface> typefaceProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert plotterProvider != null;
    this.plotterProvider = plotterProvider;
    assert typefaceProvider != null;
    this.typefaceProvider = typefaceProvider;
  }

  @Override
  public void injectMembers(PlotFunctionsFragment instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.plotter = plotterProvider.get();
    instance.typeface = typefaceProvider.get();
  }

  public static MembersInjector<PlotFunctionsFragment> create(MembersInjector<BaseDialogFragment> supertypeInjector, Provider<Plotter> plotterProvider, Provider<Typeface> typefaceProvider) {  
      return new PlotFunctionsFragment_MembersInjector(supertypeInjector, plotterProvider, typefaceProvider);
  }
}

