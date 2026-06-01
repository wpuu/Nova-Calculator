package org.solovyev.android.calculator.plot;

import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.BaseFragment;
import org.solovyev.android.calculator.plot.PlotActivity.MyFragment;
import org.solovyev.android.plotter.Plotter;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class PlotActivity$MyFragment_MembersInjector implements MembersInjector<MyFragment> {
  private final MembersInjector<BaseFragment> supertypeInjector;
  private final Provider<Plotter> plotterProvider;

  public PlotActivity$MyFragment_MembersInjector(MembersInjector<BaseFragment> supertypeInjector, Provider<Plotter> plotterProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert plotterProvider != null;
    this.plotterProvider = plotterProvider;
  }

  @Override
  public void injectMembers(MyFragment instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.plotter = plotterProvider.get();
  }

  public static MembersInjector<MyFragment> create(MembersInjector<BaseFragment> supertypeInjector, Provider<Plotter> plotterProvider) {  
      return new PlotActivity$MyFragment_MembersInjector(supertypeInjector, plotterProvider);
  }
}

