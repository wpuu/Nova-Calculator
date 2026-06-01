package org.solovyev.android.io;

import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.ErrorReporter;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class FileSystem_MembersInjector implements MembersInjector<FileSystem> {
  private final Provider<ErrorReporter> errorReporterProvider;

  public FileSystem_MembersInjector(Provider<ErrorReporter> errorReporterProvider) {  
    assert errorReporterProvider != null;
    this.errorReporterProvider = errorReporterProvider;
  }

  @Override
  public void injectMembers(FileSystem instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    instance.errorReporter = errorReporterProvider.get();
  }

  public static MembersInjector<FileSystem> create(Provider<ErrorReporter> errorReporterProvider) {  
      return new FileSystem_MembersInjector(errorReporterProvider);
  }
}

