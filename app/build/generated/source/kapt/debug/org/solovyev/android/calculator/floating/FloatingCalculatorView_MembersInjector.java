package org.solovyev.android.calculator.floating;

import android.content.SharedPreferences;
import android.graphics.Typeface;
import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.Editor;
import org.solovyev.android.calculator.Keyboard;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class FloatingCalculatorView_MembersInjector implements MembersInjector<FloatingCalculatorView> {
  private final Provider<Keyboard> keyboardProvider;
  private final Provider<Editor> editorProvider;
  private final Provider<SharedPreferences> preferencesProvider;
  private final Provider<Typeface> typefaceProvider;
  private final Provider<SharedPreferences> myPreferencesProvider;

  public FloatingCalculatorView_MembersInjector(Provider<Keyboard> keyboardProvider, Provider<Editor> editorProvider, Provider<SharedPreferences> preferencesProvider, Provider<Typeface> typefaceProvider, Provider<SharedPreferences> myPreferencesProvider) {  
    assert keyboardProvider != null;
    this.keyboardProvider = keyboardProvider;
    assert editorProvider != null;
    this.editorProvider = editorProvider;
    assert preferencesProvider != null;
    this.preferencesProvider = preferencesProvider;
    assert typefaceProvider != null;
    this.typefaceProvider = typefaceProvider;
    assert myPreferencesProvider != null;
    this.myPreferencesProvider = myPreferencesProvider;
  }

  @Override
  public void injectMembers(FloatingCalculatorView instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    instance.keyboard = keyboardProvider.get();
    instance.editor = editorProvider.get();
    instance.preferences = preferencesProvider.get();
    instance.typeface = typefaceProvider.get();
    instance.myPreferences = myPreferencesProvider.get();
  }

  public static MembersInjector<FloatingCalculatorView> create(Provider<Keyboard> keyboardProvider, Provider<Editor> editorProvider, Provider<SharedPreferences> preferencesProvider, Provider<Typeface> typefaceProvider, Provider<SharedPreferences> myPreferencesProvider) {  
      return new FloatingCalculatorView_MembersInjector(keyboardProvider, editorProvider, preferencesProvider, typefaceProvider, myPreferencesProvider);
  }
}

