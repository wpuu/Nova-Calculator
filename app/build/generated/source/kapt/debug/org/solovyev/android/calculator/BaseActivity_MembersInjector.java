package org.solovyev.android.calculator;

import android.content.SharedPreferences;
import android.graphics.Typeface;
import androidx.appcompat.app.AppCompatActivity;
import dagger.MembersInjector;
import dagger.internal.DoubleCheckLazy;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.solovyev.android.calculator.ga.Ga;
import org.solovyev.android.calculator.language.Languages;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class BaseActivity_MembersInjector implements MembersInjector<BaseActivity> {
  private final MembersInjector<AppCompatActivity> supertypeInjector;
  private final Provider<SharedPreferences> preferencesProvider;
  private final Provider<Languages> languagesProvider;
  private final Provider<Editor> editorProvider;
  private final Provider<Calculator> calculatorProvider;
  private final Provider<Ga> gaProvider;
  private final Provider<Typeface> typefaceProvider;

  public BaseActivity_MembersInjector(MembersInjector<AppCompatActivity> supertypeInjector, Provider<SharedPreferences> preferencesProvider, Provider<Languages> languagesProvider, Provider<Editor> editorProvider, Provider<Calculator> calculatorProvider, Provider<Ga> gaProvider, Provider<Typeface> typefaceProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert preferencesProvider != null;
    this.preferencesProvider = preferencesProvider;
    assert languagesProvider != null;
    this.languagesProvider = languagesProvider;
    assert editorProvider != null;
    this.editorProvider = editorProvider;
    assert calculatorProvider != null;
    this.calculatorProvider = calculatorProvider;
    assert gaProvider != null;
    this.gaProvider = gaProvider;
    assert typefaceProvider != null;
    this.typefaceProvider = typefaceProvider;
  }

  @Override
  public void injectMembers(BaseActivity instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.preferences = preferencesProvider.get();
    instance.languages = languagesProvider.get();
    instance.editor = editorProvider.get();
    instance.calculator = calculatorProvider.get();
    instance.ga = DoubleCheckLazy.create(gaProvider);
    instance.typeface = typefaceProvider.get();
  }

  public static MembersInjector<BaseActivity> create(MembersInjector<AppCompatActivity> supertypeInjector, Provider<SharedPreferences> preferencesProvider, Provider<Languages> languagesProvider, Provider<Editor> editorProvider, Provider<Calculator> calculatorProvider, Provider<Ga> gaProvider, Provider<Typeface> typefaceProvider) {  
      return new BaseActivity_MembersInjector(supertypeInjector, preferencesProvider, languagesProvider, editorProvider, calculatorProvider, gaProvider, typefaceProvider);
  }
}

