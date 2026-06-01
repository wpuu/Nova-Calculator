package org.solovyev.android.calculator.feedback;

import android.app.Application;
import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class FeedbackReporter_Factory implements Factory<FeedbackReporter> {
  private final Provider<Application> contextProvider;

  public FeedbackReporter_Factory(Provider<Application> contextProvider) {  
    assert contextProvider != null;
    this.contextProvider = contextProvider;
  }

  @Override
  public FeedbackReporter get() {  
    return new FeedbackReporter(contextProvider.get());
  }

  public static Factory<FeedbackReporter> create(Provider<Application> contextProvider) {  
    return new FeedbackReporter_Factory(contextProvider);
  }
}

