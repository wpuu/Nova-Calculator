package org.solovyev.android.calculator.history;

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
import org.solovyev.android.calculator.Display;
import org.solovyev.android.calculator.Editor;
import org.solovyev.android.calculator.ErrorReporter;
import org.solovyev.android.io.FileSystem;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class History_MembersInjector implements MembersInjector<History> {
  private final Provider<Application> applicationProvider;
  private final Provider<Bus> busProvider;
  private final Provider<Handler> handlerProvider;
  private final Provider<SharedPreferences> preferencesProvider;
  private final Provider<Editor> editorProvider;
  private final Provider<Display> displayProvider;
  private final Provider<ErrorReporter> errorReporterProvider;
  private final Provider<FileSystem> fileSystemProvider;
  private final Provider<Executor> backgroundThreadProvider;
  private final Provider<File> filesDirProvider;

  public History_MembersInjector(Provider<Application> applicationProvider, Provider<Bus> busProvider, Provider<Handler> handlerProvider, Provider<SharedPreferences> preferencesProvider, Provider<Editor> editorProvider, Provider<Display> displayProvider, Provider<ErrorReporter> errorReporterProvider, Provider<FileSystem> fileSystemProvider, Provider<Executor> backgroundThreadProvider, Provider<File> filesDirProvider) {  
    assert applicationProvider != null;
    this.applicationProvider = applicationProvider;
    assert busProvider != null;
    this.busProvider = busProvider;
    assert handlerProvider != null;
    this.handlerProvider = handlerProvider;
    assert preferencesProvider != null;
    this.preferencesProvider = preferencesProvider;
    assert editorProvider != null;
    this.editorProvider = editorProvider;
    assert displayProvider != null;
    this.displayProvider = displayProvider;
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
  public void injectMembers(History instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    instance.application = applicationProvider.get();
    instance.bus = busProvider.get();
    instance.handler = handlerProvider.get();
    instance.preferences = preferencesProvider.get();
    instance.editor = editorProvider.get();
    instance.display = displayProvider.get();
    instance.errorReporter = errorReporterProvider.get();
    instance.fileSystem = fileSystemProvider.get();
    instance.backgroundThread = backgroundThreadProvider.get();
    instance.filesDir = DoubleCheckLazy.create(filesDirProvider);
  }

  public static MembersInjector<History> create(Provider<Application> applicationProvider, Provider<Bus> busProvider, Provider<Handler> handlerProvider, Provider<SharedPreferences> preferencesProvider, Provider<Editor> editorProvider, Provider<Display> displayProvider, Provider<ErrorReporter> errorReporterProvider, Provider<FileSystem> fileSystemProvider, Provider<Executor> backgroundThreadProvider, Provider<File> filesDirProvider) {  
      return new History_MembersInjector(applicationProvider, busProvider, handlerProvider, preferencesProvider, editorProvider, displayProvider, errorReporterProvider, fileSystemProvider, backgroundThreadProvider, filesDirProvider);
  }
}

