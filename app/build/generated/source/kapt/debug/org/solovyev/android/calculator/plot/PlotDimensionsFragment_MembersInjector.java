package org.solovyev.android.calculator.plot;

import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.BaseDialogFragment;
import org.solovyev.android.plotter.Plotter;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class PlotDimensionsFragment_MembersInjector implements MembersInjector<PlotDimensionsFragment> {
  private final MembersInjector<BaseDialogFragment> supertypeInjector;
  private final Provider<Plotter> plotterProvider;

  public PlotDimensionsFragment_MembersInjector(MembersInjector<BaseDialogFragment> supertypeInjector, Provider<Plotter> plotterProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert plotterProvider != null;
    this.plotterProvider = plotterProvider;
  }

  @Override
  public void injectMembers(PlotDimensionsFragment instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.plotter = plotterProvider.get();
  }

  public static MembersInjector<PlotDimensionsFragment> create(MembersInjector<BaseDialogFragment> supertypeInjector, Provider<Plotter> plotterProvider) {  
      return new PlotDimensionsFragment_MembersInjector(supertypeInjector, plotterProvider);
  }
}

