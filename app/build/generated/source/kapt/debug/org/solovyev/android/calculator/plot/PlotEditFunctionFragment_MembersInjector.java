package org.solovyev.android.calculator.plot;

import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.functions.BaseFunctionFragment;
import org.solovyev.android.plotter.Plotter;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class PlotEditFunctionFragment_MembersInjector implements MembersInjector<PlotEditFunctionFragment> {
  private final MembersInjector<BaseFunctionFragment> supertypeInjector;
  private final Provider<Plotter> plotterProvider;

  public PlotEditFunctionFragment_MembersInjector(MembersInjector<BaseFunctionFragment> supertypeInjector, Provider<Plotter> plotterProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert plotterProvider != null;
    this.plotterProvider = plotterProvider;
  }

  @Override
  public void injectMembers(PlotEditFunctionFragment instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.plotter = plotterProvider.get();
  }

  public static MembersInjector<PlotEditFunctionFragment> create(MembersInjector<BaseFunctionFragment> supertypeInjector, Provider<Plotter> plotterProvider) {  
      return new PlotEditFunctionFragment_MembersInjector(supertypeInjector, plotterProvider);
  }
}

