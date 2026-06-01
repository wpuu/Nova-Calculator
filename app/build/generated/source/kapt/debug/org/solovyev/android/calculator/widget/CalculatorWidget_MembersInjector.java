package org.solovyev.android.calculator.widget;

import android.appwidget.AppWidgetProvider;
import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.Display;
import org.solovyev.android.calculator.Editor;
import org.solovyev.android.calculator.Engine;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class CalculatorWidget_MembersInjector implements MembersInjector<CalculatorWidget> {
  private final MembersInjector<AppWidgetProvider> supertypeInjector;
  private final Provider<Editor> editorProvider;
  private final Provider<Display> displayProvider;
  private final Provider<Engine> engineProvider;

  public CalculatorWidget_MembersInjector(MembersInjector<AppWidgetProvider> supertypeInjector, Provider<Editor> editorProvider, Provider<Display> displayProvider, Provider<Engine> engineProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert editorProvider != null;
    this.editorProvider = editorProvider;
    assert displayProvider != null;
    this.displayProvider = displayProvider;
    assert engineProvider != null;
    this.engineProvider = engineProvider;
  }

  @Override
  public void injectMembers(CalculatorWidget instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.editor = editorProvider.get();
    instance.display = displayProvider.get();
    instance.engine = engineProvider.get();
  }

  public static MembersInjector<CalculatorWidget> create(MembersInjector<AppWidgetProvider> supertypeInjector, Provider<Editor> editorProvider, Provider<Display> displayProvider, Provider<Engine> engineProvider) {  
      return new CalculatorWidget_MembersInjector(supertypeInjector, editorProvider, displayProvider, engineProvider);
  }
}

