package org.solovyev.android.calculator.preferences;

import android.content.SharedPreferences;
import androidx.preference.PreferenceFragmentCompat;
import com.squareup.otto.Bus;
import dagger.MembersInjector;
import javax.annotation.Generated;
import javax.inject.Provider;
import jscl.JsclMathEngine;
import org.solovyev.android.calculator.ActivityLauncher;
import org.solovyev.android.calculator.feedback.FeedbackReporter;
import org.solovyev.android.calculator.language.Languages;
import org.solovyev.android.wizard.Wizards;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class PreferencesFragment_MembersInjector implements MembersInjector<PreferencesFragment> {
  private final MembersInjector<PreferenceFragmentCompat> supertypeInjector;
  private final Provider<SharedPreferences> preferencesProvider;
  private final Provider<Languages> languagesProvider;
  private final Provider<Wizards> wizardsProvider;
  private final Provider<JsclMathEngine> engineProvider;
  private final Provider<FeedbackReporter> feedbackReporterProvider;
  private final Provider<ActivityLauncher> launcherProvider;
  private final Provider<Bus> busProvider;

  public PreferencesFragment_MembersInjector(MembersInjector<PreferenceFragmentCompat> supertypeInjector, Provider<SharedPreferences> preferencesProvider, Provider<Languages> languagesProvider, Provider<Wizards> wizardsProvider, Provider<JsclMathEngine> engineProvider, Provider<FeedbackReporter> feedbackReporterProvider, Provider<ActivityLauncher> launcherProvider, Provider<Bus> busProvider) {  
    assert supertypeInjector != null;
    this.supertypeInjector = supertypeInjector;
    assert preferencesProvider != null;
    this.preferencesProvider = preferencesProvider;
    assert languagesProvider != null;
    this.languagesProvider = languagesProvider;
    assert wizardsProvider != null;
    this.wizardsProvider = wizardsProvider;
    assert engineProvider != null;
    this.engineProvider = engineProvider;
    assert feedbackReporterProvider != null;
    this.feedbackReporterProvider = feedbackReporterProvider;
    assert launcherProvider != null;
    this.launcherProvider = launcherProvider;
    assert busProvider != null;
    this.busProvider = busProvider;
  }

  @Override
  public void injectMembers(PreferencesFragment instance) {  
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    supertypeInjector.injectMembers(instance);
    instance.preferences = preferencesProvider.get();
    instance.languages = languagesProvider.get();
    instance.wizards = wizardsProvider.get();
    instance.engine = engineProvider.get();
    instance.feedbackReporter = feedbackReporterProvider.get();
    instance.launcher = launcherProvider.get();
    instance.bus = busProvider.get();
  }

  public static MembersInjector<PreferencesFragment> create(MembersInjector<PreferenceFragmentCompat> supertypeInjector, Provider<SharedPreferences> preferencesProvider, Provider<Languages> languagesProvider, Provider<Wizards> wizardsProvider, Provider<JsclMathEngine> engineProvider, Provider<FeedbackReporter> feedbackReporterProvider, Provider<ActivityLauncher> launcherProvider, Provider<Bus> busProvider) {  
      return new PreferencesFragment_MembersInjector(supertypeInjector, preferencesProvider, languagesProvider, wizardsProvider, engineProvider, feedbackReporterProvider, launcherProvider, busProvider);
  }
}

