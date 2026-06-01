package org.solovyev.android.calculator.memory;

import android.os.Handler;
import dagger.MembersInjector;
import dagger.internal.DoubleCheckLazy;
import dagger.internal.Factory;
import java.io.File;
import java.util.concurrent.Executor;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.io.FileSystem;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class Memory_Factory implements Factory<Memory> {
  private final MembersInjector<Memory> membersInjector;
  private final Provider<Executor> initThreadProvider;
  private final Provider<FileSystem> fileSystemProvider;
  private final Provider<File> filesDirProvider;
  private final Provider<Handler> handlerProvider;

  public Memory_Factory(MembersInjector<Memory> membersInjector, Provider<Executor> initThreadProvider, Provider<FileSystem> fileSystemProvider, Provider<File> filesDirProvider, Provider<Handler> handlerProvider) {  
    assert membersInjector != null;
    this.membersInjector = membersInjector;
    assert initThreadProvider != null;
    this.initThreadProvider = initThreadProvider;
    assert fileSystemProvider != null;
    this.fileSystemProvider = fileSystemProvider;
    assert filesDirProvider != null;
    this.filesDirProvider = filesDirProvider;
    assert handlerProvider != null;
    this.handlerProvider = handlerProvider;
  }

  @Override
  public Memory get() {  
    Memory instance = new Memory(initThreadProvider.get(), fileSystemProvider.get(), DoubleCheckLazy.create(filesDirProvider), handlerProvider.get());
    membersInjector.injectMembers(instance);
    return instance;
  }

  public static Factory<Memory> create(MembersInjector<Memory> membersInjector, Provider<Executor> initThreadProvider, Provider<FileSystem> fileSystemProvider, Provider<File> filesDirProvider, Provider<Handler> handlerProvider) {  
    return new Memory_Factory(membersInjector, initThreadProvider, fileSystemProvider, filesDirProvider, handlerProvider);
  }
}

