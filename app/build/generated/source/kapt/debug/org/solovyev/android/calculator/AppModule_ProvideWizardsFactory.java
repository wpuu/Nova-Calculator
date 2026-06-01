package org.solovyev.android.calculator;

import android.app.Application;
import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.wizard.Wizards;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class AppModule_ProvideWizardsFactory implements Factory<Wizards> {
  private final AppModule module;
  private final Provider<Application> applicationProvider;

  public AppModule_ProvideWizardsFactory(AppModule module, Provider<Application> applicationProvider) {  
    assert module != null;
    this.module = module;
    assert applicationProvider != null;
    this.applicationProvider = applicationProvider;
  }

  @Override
  public Wizards get() {  
    Wizards provided = module.provideWizards(applicationProvider.get());
    if (provided == null) {
      throw new NullPointerException("Cannot return null from a non-@Nullable @Provides method");
    }
    return provided;
  }

  public static Factory<Wizards> create(AppModule module, Provider<Application> applicationProvider) {  
    return new AppModule_ProvideWizardsFactory(module, applicationProvider);
  }
}

