package org.solovyev.android.calculator.converter;

import android.content.SharedPreferences;
import android.graphics.Typeface;
import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.BaseDialogFragment;
import org.solovyev.android.calculator.Clipboard;
import org.solovyev.android.calculator.Editor;
import org.solovyev.android.calculator.Keyboard;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class ConverterFragment_MembersInjector implements MembersInjector<ConverterFragment> {
  private final MembersInjector<BaseDialogFragment> supertypeInjector;
  private final Provider<Typeface> typefaceProvider;
  private final Provider<Clipboard> clipboardProvider;
  private final Provider<Keyboard> keyboardProvider;
  private final Provider<SharedPreferences> uiPreferencesProvider;
  private final Provider<Editor> editorProvider;

  public ConverterFragment_MembersInjector(MembersInjector<BaseDialogFragment> supertypeInjector, Provider<Typeface> typefaceProvider, Provider<Clipboard> clipboardProvider, Provider<Keyboard> keyboardProvider, Provider<SharedPreferences> uiPreferencesProvider, Provider<Editor> editorProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert typefaceProvider != null;
    this.typefaceProvider = typefaceProvider;
    assert clipboardProvider != null;
    this.clipboardProvider = clipboardProvider;
    assert keyboardProvider != null;
    this.keyboardProvider = keyboardProvider;
    assert uiPreferencesProvider != null;
    this.uiPreferencesProvider = uiPreferencesProvider;
    assert editorProvider != null;
    this.editorProvider = editorProvider;
  }

  @Override
  public void injectMembers(ConverterFragment instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.typeface = typefaceProvider.get();
    instance.clipboard = clipboardProvider.get();
    instance.keyboard = keyboardProvider.get();
    instance.uiPreferences = uiPreferencesProvider.get();
    instance.editor = editorProvider.get();
  }

  public static MembersInjector<ConverterFragment> create(MembersInjector<BaseDialogFragment> supertypeInjector, Provider<Typeface> typefaceProvider, Provider<Clipboard> clipboardProvider, Provider<Keyboard> keyboardProvider, Provider<SharedPreferences> uiPreferencesProvider, Provider<Editor> editorProvider) {  
      return new ConverterFragment_MembersInjector(supertypeInjector, typefaceProvider, clipboardProvider, keyboardProvider, uiPreferencesProvider, editorProvider);
  }
}

