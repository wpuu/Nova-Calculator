package org.solovyev.android.io;

import dagger.MembersInjector;
import dagger.internal.Factory;
import javax.annotation.Generated;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class FileSystem_Factory implements Factory<FileSystem> {
  private final MembersInjector<FileSystem> membersInjector;

  public FileSystem_Factory(MembersInjector<FileSystem> membersInjector) {  
    assert membersInjector != null;
    this.membersInjector = membersInjector;
  }

  @Override
  public FileSystem get() {  
    FileSystem instance = new FileSystem();
    membersInjector.injectMembers(instance);
    return instance;
  }

  public static Factory<FileSystem> create(MembersInjector<FileSystem> membersInjector) {  
    return new FileSystem_Factory(membersInjector);
  }
}

