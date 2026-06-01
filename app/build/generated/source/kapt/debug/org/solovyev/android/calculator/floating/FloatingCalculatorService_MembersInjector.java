package org.solovyev.android.calculator.floating;

import android.app.Service;
import android.content.SharedPreferences;
import com.squareup.otto.Bus;
import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.Display;
import org.solovyev.android.calculator.Editor;
import org.solovyev.android.calculator.ga.Ga;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class FloatingCalculatorService_MembersInjector implements MembersInjector<FloatingCalculatorService> {
  private final MembersInjector<Service> supertypeInjector;
  private final Provider<Bus> busProvider;
  private final Provider<Editor> editorProvider;
  private final Provider<Display> displayProvider;
  private final Provider<Ga> gaProvider;
  private final Provider<SharedPreferences> preferencesProvider;

  public FloatingCalculatorService_MembersInjector(MembersInjector<Service> supertypeInjector, Provider<Bus> busProvider, Provider<Editor> editorProvider, Provider<Display> displayProvider, Provider<Ga> gaProvider, Provider<SharedPreferences> preferencesProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert busProvider != null;
    this.busProvider = busProvider;
    assert editorProvider != null;
    this.editorProvider = editorProvider;
    assert displayProvider != null;
    this.displayProvider = displayProvider;
    assert gaProvider != null;
    this.gaProvider = gaProvider;
    assert preferencesProvider != null;
    this.preferencesProvider = preferencesProvider;
  }

  @Override
  public void injectMembers(FloatingCalculatorService instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.bus = busProvider.get();
    instance.editor = editorProvider.get();
    instance.display = displayProvider.get();
    instance.ga = gaProvider.get();
    instance.preferences = preferencesProvider.get();
  }

  public static MembersInjector<FloatingCalculatorService> create(MembersInjector<Service> supertypeInjector, Provider<Bus> busProvider, Provider<Editor> editorProvider, Provider<Display> displayProvider, Provider<Ga> gaProvider, Provider<SharedPreferences> preferencesProvider) {  
      return new FloatingCalculatorService_MembersInjector(supertypeInjector, busProvider, editorProvider, displayProvider, gaProvider, preferencesProvider);
  }
}

