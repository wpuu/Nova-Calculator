package org.solovyev.android.calculator.entities;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Handler;
import com.squareup.otto.Bus;
import dagger.MembersInjector;
import dagger.internal.DoubleCheckLazy;
import java.io.File;
import java.util.concurrent.Executor;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.ErrorReporter;
import org.solovyev.android.io.FileSystem;
import org.solovyev.common.math.MathEntity;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class BaseEntitiesRegistry_MembersInjector<T extends MathEntity> implements MembersInjector<BaseEntitiesRegistry<T>> {
  private final Provider<Handler> handlerProvider;
  private final Provider<SharedPreferences> preferencesProvider;
  private final Provider<Application> applicationProvider;
  private final Provider<Bus> busProvider;
  private final Provider<ErrorReporter> errorReporterProvider;
  private final Provider<FileSystem> fileSystemProvider;
  private final Provider<Executor> backgroundThreadProvider;
  private final Provider<File> filesDirProvider;

  public BaseEntitiesRegistry_MembersInjector(Provider<Handler> handlerProvider, Provider<SharedPreferences> preferencesProvider, Provider<Application> applicationProvider, Provider<Bus> busProvider, Provider<ErrorReporter> errorReporterProvider, Provider<FileSystem> fileSystemProvider, Provider<Executor> backgroundThreadProvider, Provider<File> filesDirProvider) {  
    assert handlerProvider != null;
    this.handlerProvider = handlerProvider;
    assert preferencesProvider != null;
    this.preferencesProvider = preferencesProvider;
    assert applicationProvider != null;
    this.applicationProvider = applicationProvider;
    assert busProvider != null;
    this.busProvider = busProvider;
    assert errorReporterProvider != null;
    this.errorReporterProvider = errorReporterProvider;
    assert fileSystemProvider != null;
    this.fileSystemProvider = fileSystemProvider;
    assert backgroundThreadProvider != null;
    this.backgroundThreadProvider = backgroundThreadProvider;
    assert filesDirProvider != null;
    this.filesDirProvider = filesDirProvider;
  }

  @Override
  public void injectMembers(BaseEntitiesRegistry<T> instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    instance.handler = handlerProvider.get();
    instance.preferences = preferencesProvider.get();
    instance.application = applicationProvider.get();
    instance.bus = busProvider.get();
    instance.errorReporter = errorReporterProvider.get();
    instance.fileSystem = fileSystemProvider.get();
    instance.backgroundThread = backgroundThreadProvider.get();
    instance.filesDir = DoubleCheckLazy.create(filesDirProvider);
  }

  public static <T extends MathEntity> MembersInjector<BaseEntitiesRegistry<T>> create(Provider<Handler> handlerProvider, Provider<SharedPreferences> preferencesProvider, Provider<Application> applicationProvider, Provider<Bus> busProvider, Provider<ErrorReporter> errorReporterProvider, Provider<FileSystem> fileSystemProvider, Provider<Executor> backgroundThreadProvider, Provider<File> filesDirProvider) {  
      return new BaseEntitiesRegistry_MembersInjector<T>(handlerProvider, preferencesProvider, applicationProvider, busProvider, errorReporterProvider, fileSystemProvider, backgroundThreadProvider, filesDirProvider);
  }
}

