package org.solovyev.android.calculator;

import dagger.internal.Factory;
import java.io.File;
import java.util.concurrent.Executor;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class AppModule_ProvideFilesDirFactory implements Factory<File> {
  private final AppModule module;
  private final Provider<Executor> initThreadProvider;

  public AppModule_ProvideFilesDirFactory(AppModule module, Provider<Executor> initThreadProvider) {  
    assert module != null;
    this.module = module;
    assert initThreadProvider != null;
    this.initThreadProvider = initThreadProvider;
  }

  @Override
  public File get() {  
    File provided = module.provideFilesDir(initThreadProvider.get());
    if (provided == null) {
      throw new NullPointerException("Cannot return null from a non-@Nullable @Provides method");
    }
    return provided;
  }

  public static Factory<File> create(AppModule module, Provider<Executor> initThreadProvider) {  
    return new AppModule_ProvideFilesDirFactory(module, initThreadProvider);
  }
}

