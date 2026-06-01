package org.solovyev.android.calculator;

import android.app.Application;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Handler;
import com.squareup.otto.Bus;
import dagger.MembersInjector;
import dagger.internal.MembersInjectors;
import dagger.internal.ScopedProvider;
import java.io.File;
import java.util.concurrent.Executor;
import javax.annotation.Generated;
import javax.inject.Provider;
import jscl.JsclMathEngine;
import jscl.math.function.Function;
import jscl.math.function.IConstant;
import jscl.math.operator.Operator;
import org.solovyev.android.calculator.ads.AdUi;
import org.solovyev.android.calculator.ads.AdUi_Factory;
import org.solovyev.android.calculator.converter.ConverterFragment;
import org.solovyev.android.calculator.converter.ConverterFragment_MembersInjector;
import org.solovyev.android.calculator.entities.BaseEntitiesFragment;
import org.solovyev.android.calculator.entities.BaseEntitiesFragment_MembersInjector;
import org.solovyev.android.calculator.entities.BaseEntitiesRegistry;
import org.solovyev.android.calculator.entities.BaseEntitiesRegistry_MembersInjector;
import org.solovyev.android.calculator.errors.FixableErrorFragment;
import org.solovyev.android.calculator.errors.FixableErrorFragment_MembersInjector;
import org.solovyev.android.calculator.errors.FixableErrorsActivity;
import org.solovyev.android.calculator.errors.FixableErrorsActivity_MembersInjector;
import org.solovyev.android.calculator.feedback.FeedbackReporter;
import org.solovyev.android.calculator.feedback.FeedbackReporter_Factory;
import org.solovyev.android.calculator.floating.FloatingCalculatorBroadcastReceiver;
import org.solovyev.android.calculator.floating.FloatingCalculatorService;
import org.solovyev.android.calculator.floating.FloatingCalculatorService_MembersInjector;
import org.solovyev.android.calculator.floating.FloatingCalculatorView;
import org.solovyev.android.calculator.floating.FloatingCalculatorView_MembersInjector;
import org.solovyev.android.calculator.functions.BaseFunctionFragment;
import org.solovyev.android.calculator.functions.BaseFunctionFragment_MembersInjector;
import org.solovyev.android.calculator.functions.FunctionsFragment;
import org.solovyev.android.calculator.functions.FunctionsFragment_MembersInjector;
import org.solovyev.android.calculator.functions.FunctionsRegistry;
import org.solovyev.android.calculator.functions.FunctionsRegistry_Factory;
import org.solovyev.android.calculator.ga.Ga;
import org.solovyev.android.calculator.ga.Ga_Factory;
import org.solovyev.android.calculator.history.BaseHistoryFragment;
import org.solovyev.android.calculator.history.BaseHistoryFragment_MembersInjector;
import org.solovyev.android.calculator.history.EditHistoryFragment;
import org.solovyev.android.calculator.history.EditHistoryFragment_MembersInjector;
import org.solovyev.android.calculator.history.History;
import org.solovyev.android.calculator.history.HistoryActivity;
import org.solovyev.android.calculator.history.HistoryActivity_MembersInjector;
import org.solovyev.android.calculator.history.History_Factory;
import org.solovyev.android.calculator.history.History_MembersInjector;
import org.solovyev.android.calculator.keyboard.BaseKeyboardUi;
import org.solovyev.android.calculator.keyboard.BaseKeyboardUi_MembersInjector;
import org.solovyev.android.calculator.keyboard.KeyboardUi;
import org.solovyev.android.calculator.keyboard.KeyboardUi_Factory;
import org.solovyev.android.calculator.keyboard.KeyboardUi_MembersInjector;
import org.solovyev.android.calculator.keyboard.PartialKeyboardUi;
import org.solovyev.android.calculator.keyboard.PartialKeyboardUi_Factory;
import org.solovyev.android.calculator.language.Languages;
import org.solovyev.android.calculator.memory.Memory;
import org.solovyev.android.calculator.memory.Memory_Factory;
import org.solovyev.android.calculator.memory.Memory_MembersInjector;
import org.solovyev.android.calculator.operators.OperatorsFragment;
import org.solovyev.android.calculator.operators.OperatorsFragment_MembersInjector;
import org.solovyev.android.calculator.operators.OperatorsRegistry;
import org.solovyev.android.calculator.operators.OperatorsRegistry_Factory;
import org.solovyev.android.calculator.operators.PostfixFunctionsRegistry;
import org.solovyev.android.calculator.operators.PostfixFunctionsRegistry_Factory;
import org.solovyev.android.calculator.plot.PlotActivity$MyFragment_MembersInjector;
import org.solovyev.android.calculator.plot.PlotActivity.MyFragment;
import org.solovyev.android.calculator.plot.PlotDimensionsFragment;
import org.solovyev.android.calculator.plot.PlotDimensionsFragment_MembersInjector;
import org.solovyev.android.calculator.plot.PlotEditFunctionFragment;
import org.solovyev.android.calculator.plot.PlotEditFunctionFragment_MembersInjector;
import org.solovyev.android.calculator.plot.PlotFunctionsFragment;
import org.solovyev.android.calculator.plot.PlotFunctionsFragment_MembersInjector;
import org.solovyev.android.calculator.preferences.PreferencesActivity;
import org.solovyev.android.calculator.preferences.PreferencesActivity_MembersInjector;
import org.solovyev.android.calculator.preferences.PreferencesFragment;
import org.solovyev.android.calculator.preferences.PreferencesFragment_MembersInjector;
import org.solovyev.android.calculator.preferences.PurchaseDialogActivity;
import org.solovyev.android.calculator.preferences.PurchaseDialogActivity_MembersInjector;
import org.solovyev.android.calculator.variables.EditVariableFragment;
import org.solovyev.android.calculator.variables.EditVariableFragment_MembersInjector;
import org.solovyev.android.calculator.variables.VariablesFragment;
import org.solovyev.android.calculator.variables.VariablesFragment_MembersInjector;
import org.solovyev.android.calculator.view.Tabs;
import org.solovyev.android.calculator.view.Tabs_MembersInjector;
import org.solovyev.android.calculator.widget.CalculatorWidget;
import org.solovyev.android.calculator.widget.CalculatorWidget_MembersInjector;
import org.solovyev.android.calculator.wizard.DragButtonWizardStep;
import org.solovyev.android.calculator.wizard.DragButtonWizardStep_MembersInjector;
import org.solovyev.android.calculator.wizard.WizardActivity;
import org.solovyev.android.calculator.wizard.WizardActivity_MembersInjector;
import org.solovyev.android.calculator.wizard.WizardFragment;
import org.solovyev.android.calculator.wizard.WizardFragment_MembersInjector;
import org.solovyev.android.checkout.Billing;
import org.solovyev.android.checkout.CppCheckout;
import org.solovyev.android.checkout.CppCheckout_Factory;
import org.solovyev.android.io.FileSystem;
import org.solovyev.android.io.FileSystem_Factory;
import org.solovyev.android.io.FileSystem_MembersInjector;
import org.solovyev.android.plotter.Plotter;
import org.solovyev.android.wizard.Wizards;

@Generated("dagger.internal.codegen.ComponentProcessor")
public final class DaggerAppComponent implements AppComponent {
  private Provider<Executor> provideInitThreadProvider;
  private Provider<Handler> provideHandlerProvider;
  private Provider<Executor> provideUiThreadProvider;
  private Provider<Bus> provideBusProvider;
  private MembersInjector<Editor> editorMembersInjector;
  private Provider<Application> provideApplicationProvider;
  private Provider<SharedPreferences> providePreferencesProvider;
  private Provider<ErrorReporter> provideErrorReporterProvider;
  private MembersInjector<FileSystem> fileSystemMembersInjector;
  private Provider<FileSystem> fileSystemProvider;
  private Provider<Executor> provideBackgroundThreadProvider;
  private Provider<File> provideFilesDirProvider;
  private MembersInjector<BaseEntitiesRegistry<Function>> baseEntitiesRegistryMembersInjector;
  private MembersInjector<FunctionsRegistry> functionsRegistryMembersInjector;
  private Provider<JsclMathEngine> provideJsclMathEngineProvider;
  private Provider<FunctionsRegistry> functionsRegistryProvider;
  private MembersInjector<BaseEntitiesRegistry<IConstant>> baseEntitiesRegistryMembersInjector1;
  private MembersInjector<VariablesRegistry> variablesRegistryMembersInjector;
  private Provider<VariablesRegistry> variablesRegistryProvider;
  private MembersInjector<BaseEntitiesRegistry<Operator>> baseEntitiesRegistryMembersInjector2;
  private MembersInjector<OperatorsRegistry> operatorsRegistryMembersInjector;
  private Provider<OperatorsRegistry> operatorsRegistryProvider;
  private MembersInjector<PostfixFunctionsRegistry> postfixFunctionsRegistryMembersInjector;
  private Provider<PostfixFunctionsRegistry> postfixFunctionsRegistryProvider;
  private MembersInjector<Engine> engineMembersInjector;
  private Provider<Engine> engineProvider;
  private Provider<Editor> editorProvider;
  private Provider<Clipboard> clipboardProvider;
  private MembersInjector<Notifier> notifierMembersInjector;
  private Provider<Notifier> notifierProvider;
  private Provider<UiPreferences> uiPreferencesProvider;
  private MembersInjector<Display> displayMembersInjector;
  private Provider<Display> displayProvider;
  private MembersInjector<ToJsclTextProcessor> toJsclTextProcessorMembersInjector;
  private Provider<ToJsclTextProcessor> toJsclTextProcessorProvider;
  private MembersInjector<Calculator> calculatorMembersInjector;
  private Provider<Calculator> calculatorProvider;
  private MembersInjector<History> historyMembersInjector;
  private Provider<History> historyProvider;
  private MembersInjector<Memory> memoryMembersInjector;
  private Provider<Memory> memoryProvider;
  private Provider<Ga> gaProvider;
  private Provider<Plotter> providePlotterProvider;
  private MembersInjector<ActivityLauncher> activityLauncherMembersInjector;
  private Provider<ActivityLauncher> activityLauncherProvider;
  private MembersInjector<Keyboard> keyboardMembersInjector;
  private Provider<Keyboard> keyboardProvider;
  private Provider<Broadcaster> broadcasterProvider;
  private MembersInjector<CalculatorApplication> calculatorApplicationMembersInjector;
  private Provider<Billing> provideBillingProvider;
  private Provider<CppCheckout> cppCheckoutProvider;
  private Provider<AdUi> adUiProvider;
  private Provider<Typeface> provideTypefaceProvider;
  private MembersInjector<BaseFragment> baseFragmentMembersInjector;
  private MembersInjector<EditorFragment> editorFragmentMembersInjector;
  private MembersInjector<FloatingCalculatorService> floatingCalculatorServiceMembersInjector;
  private MembersInjector<BaseHistoryFragment> baseHistoryFragmentMembersInjector;
  private MembersInjector<BaseDialogFragment> baseDialogFragmentMembersInjector;
  private MembersInjector<PlotFunctionsFragment> plotFunctionsFragmentMembersInjector;
  private MembersInjector<FixableErrorFragment> fixableErrorFragmentMembersInjector;
  private MembersInjector<BaseFunctionFragment> baseFunctionFragmentMembersInjector;
  private MembersInjector<PlotEditFunctionFragment> plotEditFunctionFragmentMembersInjector;
  private MembersInjector<EditVariableFragment> editVariableFragmentMembersInjector;
  private MembersInjector<EditHistoryFragment> editHistoryFragmentMembersInjector;
  private MembersInjector<BaseEntitiesFragment<Function>> baseEntitiesFragmentMembersInjector;
  private MembersInjector<FunctionsFragment> functionsFragmentMembersInjector;
  private MembersInjector<BaseEntitiesFragment<IConstant>> baseEntitiesFragmentMembersInjector1;
  private MembersInjector<VariablesFragment> variablesFragmentMembersInjector;
  private MembersInjector<BaseEntitiesFragment<Operator>> baseEntitiesFragmentMembersInjector2;
  private MembersInjector<OperatorsFragment> operatorsFragmentMembersInjector;
  private Provider<SharedPreferences> provideUiPreferencesProvider;
  private MembersInjector<ConverterFragment> converterFragmentMembersInjector;
  private Provider<Languages> provideLanguagesProvider;
  private MembersInjector<BaseActivity> baseActivityMembersInjector;
  private MembersInjector<BaseKeyboardUi> baseKeyboardUiMembersInjector;
  private MembersInjector<PartialKeyboardUi> partialKeyboardUiMembersInjector;
  private Provider<PartialKeyboardUi> partialKeyboardUiProvider;
  private Provider<Wizards> provideWizardsProvider;
  private MembersInjector<StartupHelper> startupHelperMembersInjector;
  private Provider<StartupHelper> startupHelperProvider;
  private MembersInjector<CalculatorActivity> calculatorActivityMembersInjector;
  private MembersInjector<FixableErrorsActivity> fixableErrorsActivityMembersInjector;
  private MembersInjector<WidgetReceiver> widgetReceiverMembersInjector;
  private MembersInjector<DisplayFragment> displayFragmentMembersInjector;
  private MembersInjector<KeyboardUi> keyboardUiMembersInjector;
  private Provider<KeyboardUi> keyboardUiProvider;
  private MembersInjector<KeyboardFragment> keyboardFragmentMembersInjector;
  private MembersInjector<PurchaseDialogActivity> purchaseDialogActivityMembersInjector;
  private MembersInjector<PreferencesActivity> preferencesActivityMembersInjector;
  private Provider<SharedPreferences> provideFloatingPreferencesProvider;
  private MembersInjector<FloatingCalculatorView> floatingCalculatorViewMembersInjector;
  private MembersInjector<WizardFragment> wizardFragmentMembersInjector;
  private MembersInjector<DragButtonWizardStep> dragButtonWizardStepMembersInjector;
  private MembersInjector<MyFragment> myFragmentMembersInjector;
  private MembersInjector<PlotDimensionsFragment> plotDimensionsFragmentMembersInjector;
  private MembersInjector<HistoryActivity> historyActivityMembersInjector;
  private Provider<SharedPreferences> provideTabsPreferencesProvider;
  private MembersInjector<Tabs> tabsMembersInjector;
  private MembersInjector<CalculatorWidget> calculatorWidgetMembersInjector;
  private MembersInjector<WizardActivity> wizardActivityMembersInjector;
  private Provider<FeedbackReporter> feedbackReporterProvider;
  private MembersInjector<PreferencesFragment> preferencesFragmentMembersInjector;

  private DaggerAppComponent(Builder builder) {  
    assert builder != null;
    initialize(builder);
    initialize1(builder);
  }

  public static Builder builder() {  
    return new Builder();
  }

  private void initialize(final Builder builder) {  
    this.provideInitThreadProvider = ScopedProvider.create(AppModule_ProvideInitThreadFactory.create(builder.appModule));
    this.provideHandlerProvider = ScopedProvider.create(AppModule_ProvideHandlerFactory.create(builder.appModule));
    this.provideUiThreadProvider = ScopedProvider.create(AppModule_ProvideUiThreadFactory.create(builder.appModule, provideHandlerProvider));
    this.provideBusProvider = ScopedProvider.create(AppModule_ProvideBusFactory.create(builder.appModule, provideHandlerProvider));
    this.editorMembersInjector = Editor_MembersInjector.create(provideBusProvider);
    this.provideApplicationProvider = ScopedProvider.create(AppModule_ProvideApplicationFactory.create(builder.appModule));
    this.providePreferencesProvider = ScopedProvider.create(AppModule_ProvidePreferencesFactory.create(builder.appModule));
    this.provideErrorReporterProvider = ScopedProvider.create(AppModule_ProvideErrorReporterFactory.create(builder.appModule));
    this.fileSystemMembersInjector = FileSystem_MembersInjector.create(provideErrorReporterProvider);
    this.fileSystemProvider = ScopedProvider.create(FileSystem_Factory.create(fileSystemMembersInjector));
    this.provideBackgroundThreadProvider = ScopedProvider.create(AppModule_ProvideBackgroundThreadFactory.create(builder.appModule));
    this.provideFilesDirProvider = ScopedProvider.create(AppModule_ProvideFilesDirFactory.create(builder.appModule, provideInitThreadProvider));
    this.baseEntitiesRegistryMembersInjector = BaseEntitiesRegistry_MembersInjector.create(provideHandlerProvider, providePreferencesProvider, provideApplicationProvider, provideBusProvider, provideErrorReporterProvider, fileSystemProvider, provideBackgroundThreadProvider, provideFilesDirProvider);
    this.functionsRegistryMembersInjector = MembersInjectors.delegatingTo(baseEntitiesRegistryMembersInjector);
    this.provideJsclMathEngineProvider = ScopedProvider.create(AppModule_ProvideJsclMathEngineFactory.create(builder.appModule));
    this.functionsRegistryProvider = ScopedProvider.create(FunctionsRegistry_Factory.create(functionsRegistryMembersInjector, provideJsclMathEngineProvider));
    this.baseEntitiesRegistryMembersInjector1 = BaseEntitiesRegistry_MembersInjector.create(provideHandlerProvider, providePreferencesProvider, provideApplicationProvider, provideBusProvider, provideErrorReporterProvider, fileSystemProvider, provideBackgroundThreadProvider, provideFilesDirProvider);
    this.variablesRegistryMembersInjector = MembersInjectors.delegatingTo(baseEntitiesRegistryMembersInjector1);
    this.variablesRegistryProvider = ScopedProvider.create(VariablesRegistry_Factory.create(variablesRegistryMembersInjector, provideJsclMathEngineProvider));
    this.baseEntitiesRegistryMembersInjector2 = BaseEntitiesRegistry_MembersInjector.create(provideHandlerProvider, providePreferencesProvider, provideApplicationProvider, provideBusProvider, provideErrorReporterProvider, fileSystemProvider, provideBackgroundThreadProvider, provideFilesDirProvider);
    this.operatorsRegistryMembersInjector = MembersInjectors.delegatingTo(baseEntitiesRegistryMembersInjector2);
    this.operatorsRegistryProvider = ScopedProvider.create(OperatorsRegistry_Factory.create(operatorsRegistryMembersInjector, provideJsclMathEngineProvider));
    this.postfixFunctionsRegistryMembersInjector = MembersInjectors.delegatingTo(baseEntitiesRegistryMembersInjector2);
    this.postfixFunctionsRegistryProvider = ScopedProvider.create(PostfixFunctionsRegistry_Factory.create(postfixFunctionsRegistryMembersInjector, provideJsclMathEngineProvider));
    this.engineMembersInjector = Engine_MembersInjector.create(providePreferencesProvider, provideBusProvider, provideErrorReporterProvider, functionsRegistryProvider, variablesRegistryProvider, operatorsRegistryProvider, postfixFunctionsRegistryProvider);
    this.engineProvider = ScopedProvider.create(Engine_Factory.create(engineMembersInjector, provideJsclMathEngineProvider));
    this.editorProvider = ScopedProvider.create(Editor_Factory.create(editorMembersInjector, provideApplicationProvider, providePreferencesProvider, engineProvider));
    this.clipboardProvider = ScopedProvider.create(Clipboard_Factory.create(provideApplicationProvider));
    this.notifierMembersInjector = Notifier_MembersInjector.create(provideApplicationProvider, provideHandlerProvider);
    this.notifierProvider = ScopedProvider.create(Notifier_Factory.create(notifierMembersInjector));
    this.uiPreferencesProvider = ScopedProvider.create(UiPreferences_Factory.create());
    this.displayMembersInjector = Display_MembersInjector.create(provideApplicationProvider, engineProvider, clipboardProvider, notifierProvider, uiPreferencesProvider);
    this.displayProvider = ScopedProvider.create(Display_Factory.create(displayMembersInjector, provideBusProvider));
    this.toJsclTextProcessorMembersInjector = ToJsclTextProcessor_MembersInjector.create(engineProvider);
    this.toJsclTextProcessorProvider = ScopedProvider.create(ToJsclTextProcessor_Factory.create(toJsclTextProcessorMembersInjector));
    this.calculatorMembersInjector = Calculator_MembersInjector.create(editorProvider, engineProvider, toJsclTextProcessorProvider);
    this.calculatorProvider = ScopedProvider.create(Calculator_Factory.create(calculatorMembersInjector, providePreferencesProvider, provideBusProvider));
    this.historyMembersInjector = History_MembersInjector.create(provideApplicationProvider, provideBusProvider, provideHandlerProvider, providePreferencesProvider, editorProvider, displayProvider, provideErrorReporterProvider, fileSystemProvider, provideBackgroundThreadProvider, provideFilesDirProvider);
    this.historyProvider = ScopedProvider.create(History_Factory.create(historyMembersInjector));
    this.memoryMembersInjector = Memory_MembersInjector.create(notifierProvider, toJsclTextProcessorProvider, provideBackgroundThreadProvider, provideBusProvider);
    this.memoryProvider = ScopedProvider.create(Memory_Factory.create(memoryMembersInjector, provideInitThreadProvider, fileSystemProvider, provideFilesDirProvider, provideHandlerProvider));
    this.gaProvider = ScopedProvider.create(Ga_Factory.create(provideApplicationProvider, providePreferencesProvider));
    this.providePlotterProvider = ScopedProvider.create(AppModule_ProvidePlotterFactory.create(builder.appModule));
    this.activityLauncherMembersInjector = ActivityLauncher_MembersInjector.create(provideApplicationProvider, providePlotterProvider, provideErrorReporterProvider, displayProvider, variablesRegistryProvider, notifierProvider);
    this.activityLauncherProvider = ScopedProvider.create(ActivityLauncher_Factory.create(activityLauncherMembersInjector));
    this.keyboardMembersInjector = Keyboard_MembersInjector.create(editorProvider, displayProvider, historyProvider, memoryProvider, calculatorProvider, engineProvider, gaProvider, clipboardProvider, activityLauncherProvider);
    this.keyboardProvider = ScopedProvider.create(Keyboard_Factory.create(keyboardMembersInjector, providePreferencesProvider, provideBusProvider));
    this.broadcasterProvider = ScopedProvider.create(Broadcaster_Factory.create(provideApplicationProvider, providePreferencesProvider, provideBusProvider, provideHandlerProvider));
    this.calculatorApplicationMembersInjector = CalculatorApplication_MembersInjector.create((MembersInjector) MembersInjectors.noOp(), provideInitThreadProvider, provideUiThreadProvider, provideHandlerProvider, editorProvider, displayProvider, provideBusProvider, calculatorProvider, engineProvider, keyboardProvider, historyProvider, broadcasterProvider, provideErrorReporterProvider, activityLauncherProvider, gaProvider);
    this.provideBillingProvider = ScopedProvider.create(AppModule_ProvideBillingFactory.create(builder.appModule));
    this.cppCheckoutProvider = ScopedProvider.create(CppCheckout_Factory.create((MembersInjector) MembersInjectors.noOp(), provideBillingProvider));
    this.adUiProvider = AdUi_Factory.create(cppCheckoutProvider, provideHandlerProvider);
    this.provideTypefaceProvider = ScopedProvider.create(AppModule_ProvideTypefaceFactory.create(builder.appModule));
    this.baseFragmentMembersInjector = BaseFragment_MembersInjector.create((MembersInjector) MembersInjectors.noOp(), adUiProvider, provideTypefaceProvider);
    this.editorFragmentMembersInjector = EditorFragment_MembersInjector.create(baseFragmentMembersInjector, editorProvider);
    this.floatingCalculatorServiceMembersInjector = FloatingCalculatorService_MembersInjector.create((MembersInjector) MembersInjectors.noOp(), provideBusProvider, editorProvider, displayProvider, gaProvider, providePreferencesProvider);
    this.baseHistoryFragmentMembersInjector = BaseHistoryFragment_MembersInjector.create(baseFragmentMembersInjector, historyProvider, editorProvider, provideBusProvider, provideTypefaceProvider);
    this.baseDialogFragmentMembersInjector = BaseDialogFragment_MembersInjector.create((MembersInjector) MembersInjectors.noOp(), providePreferencesProvider, gaProvider, provideTypefaceProvider);
    this.plotFunctionsFragmentMembersInjector = PlotFunctionsFragment_MembersInjector.create(baseDialogFragmentMembersInjector, providePlotterProvider, provideTypefaceProvider);
    this.fixableErrorFragmentMembersInjector = FixableErrorFragment_MembersInjector.create(baseDialogFragmentMembersInjector, uiPreferencesProvider);
    this.baseFunctionFragmentMembersInjector = BaseFunctionFragment_MembersInjector.create(baseDialogFragmentMembersInjector, calculatorProvider, keyboardProvider, provideTypefaceProvider, functionsRegistryProvider, variablesRegistryProvider);
    this.plotEditFunctionFragmentMembersInjector = PlotEditFunctionFragment_MembersInjector.create(baseFunctionFragmentMembersInjector, providePlotterProvider);
    this.editVariableFragmentMembersInjector = EditVariableFragment_MembersInjector.create(baseDialogFragmentMembersInjector, calculatorProvider, keyboardProvider, provideTypefaceProvider, functionsRegistryProvider, variablesRegistryProvider, toJsclTextProcessorProvider, engineProvider);
    this.editHistoryFragmentMembersInjector = EditHistoryFragment_MembersInjector.create(baseDialogFragmentMembersInjector, historyProvider);
    this.baseEntitiesFragmentMembersInjector = BaseEntitiesFragment_MembersInjector.create(baseFragmentMembersInjector, keyboardProvider);
    this.functionsFragmentMembersInjector = FunctionsFragment_MembersInjector.create(baseEntitiesFragmentMembersInjector, functionsRegistryProvider, calculatorProvider, provideBusProvider);
    this.baseEntitiesFragmentMembersInjector1 = BaseEntitiesFragment_MembersInjector.create(baseFragmentMembersInjector, keyboardProvider);
    this.variablesFragmentMembersInjector = VariablesFragment_MembersInjector.create(baseEntitiesFragmentMembersInjector1, variablesRegistryProvider, calculatorProvider, provideBusProvider);
    this.baseEntitiesFragmentMembersInjector2 = BaseEntitiesFragment_MembersInjector.create(baseFragmentMembersInjector, keyboardProvider);
    this.operatorsFragmentMembersInjector = OperatorsFragment_MembersInjector.create(baseEntitiesFragmentMembersInjector2, operatorsRegistryProvider, postfixFunctionsRegistryProvider);
    this.provideUiPreferencesProvider = ScopedProvider.create(AppModule_ProvideUiPreferencesFactory.create(builder.appModule));
    this.converterFragmentMembersInjector = ConverterFragment_MembersInjector.create(baseDialogFragmentMembersInjector, provideTypefaceProvider, clipboardProvider, keyboardProvider, provideUiPreferencesProvider, editorProvider);
    this.provideLanguagesProvider = ScopedProvider.create(AppModule_ProvideLanguagesFactory.create(builder.appModule));
    this.baseActivityMembersInjector = BaseActivity_MembersInjector.create((MembersInjector) MembersInjectors.noOp(), providePreferencesProvider, provideLanguagesProvider, editorProvider, calculatorProvider, gaProvider, provideTypefaceProvider);
    this.baseKeyboardUiMembersInjector = BaseKeyboardUi_MembersInjector.create(providePreferencesProvider, keyboardProvider, editorProvider, calculatorProvider, activityLauncherProvider, memoryProvider);
    this.partialKeyboardUiMembersInjector = MembersInjectors.delegatingTo(baseKeyboardUiMembersInjector);
    this.partialKeyboardUiProvider = PartialKeyboardUi_Factory.create(partialKeyboardUiMembersInjector, provideApplicationProvider);
    this.provideWizardsProvider = ScopedProvider.create(AppModule_ProvideWizardsFactory.create(builder.appModule, provideApplicationProvider));
    this.startupHelperMembersInjector = StartupHelper_MembersInjector.create(provideUiPreferencesProvider, providePreferencesProvider, provideWizardsProvider);
    this.startupHelperProvider = ScopedProvider.create(StartupHelper_Factory.create(startupHelperMembersInjector));
    this.calculatorActivityMembersInjector = CalculatorActivity_MembersInjector.create(baseActivityMembersInjector, keyboardProvider, partialKeyboardUiProvider, historyProvider, activityLauncherProvider, startupHelperProvider, provideBusProvider);
    this.fixableErrorsActivityMembersInjector = FixableErrorsActivity_MembersInjector.create((MembersInjector) MembersInjectors.noOp(), providePreferencesProvider, uiPreferencesProvider);
    this.widgetReceiverMembersInjector = WidgetReceiver_MembersInjector.create((MembersInjector) MembersInjectors.noOp(), keyboardProvider, historyProvider);
    this.displayFragmentMembersInjector = DisplayFragment_MembersInjector.create(baseFragmentMembersInjector, providePreferencesProvider, provideErrorReporterProvider, displayProvider, activityLauncherProvider, provideBusProvider, calculatorProvider, engineProvider);
    this.keyboardUiMembersInjector = KeyboardUi_MembersInjector.create(baseKeyboardUiMembersInjector, engineProvider, displayProvider, provideBusProvider, partialKeyboardUiProvider);
  }

  private void initialize1(final Builder builder) {  
    this.keyboardUiProvider = KeyboardUi_Factory.create(keyboardUiMembersInjector, provideApplicationProvider);
    this.keyboardFragmentMembersInjector = KeyboardFragment_MembersInjector.create(baseFragmentMembersInjector, keyboardUiProvider);
    this.purchaseDialogActivityMembersInjector = PurchaseDialogActivity_MembersInjector.create((MembersInjector) MembersInjectors.noOp(), provideBillingProvider, gaProvider);
    this.preferencesActivityMembersInjector = PreferencesActivity_MembersInjector.create(baseActivityMembersInjector, provideBillingProvider, provideLanguagesProvider);
    this.provideFloatingPreferencesProvider = ScopedProvider.create(AppModule_ProvideFloatingPreferencesFactory.create(builder.appModule));
    this.floatingCalculatorViewMembersInjector = FloatingCalculatorView_MembersInjector.create(keyboardProvider, editorProvider, providePreferencesProvider, provideTypefaceProvider, provideFloatingPreferencesProvider);
    this.wizardFragmentMembersInjector = WizardFragment_MembersInjector.create((MembersInjector) MembersInjectors.noOp(), providePreferencesProvider, provideTypefaceProvider);
    this.dragButtonWizardStepMembersInjector = DragButtonWizardStep_MembersInjector.create(wizardFragmentMembersInjector, provideTypefaceProvider);
    this.myFragmentMembersInjector = PlotActivity$MyFragment_MembersInjector.create(baseFragmentMembersInjector, providePlotterProvider);
    this.plotDimensionsFragmentMembersInjector = PlotDimensionsFragment_MembersInjector.create(baseDialogFragmentMembersInjector, providePlotterProvider);
    this.historyActivityMembersInjector = HistoryActivity_MembersInjector.create(baseActivityMembersInjector, historyProvider);
    this.provideTabsPreferencesProvider = ScopedProvider.create(AppModule_ProvideTabsPreferencesFactory.create(builder.appModule));
    this.tabsMembersInjector = Tabs_MembersInjector.create(provideTabsPreferencesProvider);
    this.calculatorWidgetMembersInjector = CalculatorWidget_MembersInjector.create((MembersInjector) MembersInjectors.noOp(), editorProvider, displayProvider, engineProvider);
    this.wizardActivityMembersInjector = WizardActivity_MembersInjector.create(baseActivityMembersInjector, providePreferencesProvider, provideLanguagesProvider, provideWizardsProvider);
    this.feedbackReporterProvider = ScopedProvider.create(FeedbackReporter_Factory.create(provideApplicationProvider));
    this.preferencesFragmentMembersInjector = PreferencesFragment_MembersInjector.create((MembersInjector) MembersInjectors.noOp(), providePreferencesProvider, provideLanguagesProvider, provideWizardsProvider, provideJsclMathEngineProvider, feedbackReporterProvider, activityLauncherProvider, provideBusProvider);
  }

  @Override
  public void inject(CalculatorApplication application) {  
    calculatorApplicationMembersInjector.injectMembers(application);
  }

  @Override
  public void inject(EditorFragment fragment) {  
    editorFragmentMembersInjector.injectMembers(fragment);
  }

  @Override
  public void inject(FloatingCalculatorService service) {  
    floatingCalculatorServiceMembersInjector.injectMembers(service);
  }

  @Override
  public void inject(BaseHistoryFragment fragment) {  
    baseHistoryFragmentMembersInjector.injectMembers(fragment);
  }

  @Override
  public void inject(BaseDialogFragment fragment) {  
    baseDialogFragmentMembersInjector.injectMembers(fragment);
  }

  @Override
  public void inject(PlotFunctionsFragment fragment) {  
    plotFunctionsFragmentMembersInjector.injectMembers(fragment);
  }

  @Override
  public void inject(FixableErrorFragment fragment) {  
    fixableErrorFragmentMembersInjector.injectMembers(fragment);
  }

  @Override
  public void inject(BaseFunctionFragment fragment) {  
    baseFunctionFragmentMembersInjector.injectMembers(fragment);
  }

  @Override
  public void inject(PlotEditFunctionFragment fragment) {  
    plotEditFunctionFragmentMembersInjector.injectMembers(fragment);
  }

  @Override
  public void inject(EditVariableFragment fragment) {  
    editVariableFragmentMembersInjector.injectMembers(fragment);
  }

  @Override
  public void inject(EditHistoryFragment fragment) {  
    editHistoryFragmentMembersInjector.injectMembers(fragment);
  }

  @Override
  public void inject(FunctionsFragment fragment) {  
    functionsFragmentMembersInjector.injectMembers(fragment);
  }

  @Override
  public void inject(VariablesFragment fragment) {  
    variablesFragmentMembersInjector.injectMembers(fragment);
  }

  @Override
  public void inject(OperatorsFragment fragment) {  
    operatorsFragmentMembersInjector.injectMembers(fragment);
  }

  @Override
  public void inject(ConverterFragment fragment) {  
    converterFragmentMembersInjector.injectMembers(fragment);
  }

  @Override
  public void inject(CalculatorActivity activity) {  
    calculatorActivityMembersInjector.injectMembers(activity);
  }

  @Override
  public void inject(FixableErrorsActivity activity) {  
    fixableErrorsActivityMembersInjector.injectMembers(activity);
  }

  @Override
  public void inject(WidgetReceiver receiver) {  
    widgetReceiverMembersInjector.injectMembers(receiver);
  }

  @Override
  public void inject(DisplayFragment fragment) {  
    displayFragmentMembersInjector.injectMembers(fragment);
  }

  @Override
  public void inject(KeyboardFragment fragment) {  
    keyboardFragmentMembersInjector.injectMembers(fragment);
  }

  @Override
  public void inject(PurchaseDialogActivity activity) {  
    purchaseDialogActivityMembersInjector.injectMembers(activity);
  }

  @Override
  public void inject(PreferencesActivity activity) {  
    preferencesActivityMembersInjector.injectMembers(activity);
  }

  @Override
  public void inject(BaseKeyboardUi ui) {  
    baseKeyboardUiMembersInjector.injectMembers(ui);
  }

  @Override
  public void inject(FloatingCalculatorView view) {  
    floatingCalculatorViewMembersInjector.injectMembers(view);
  }

  @Override
  public void inject(DragButtonWizardStep fragment) {  
    dragButtonWizardStepMembersInjector.injectMembers(fragment);
  }

  @Override
  public void inject(BaseFragment fragment) {  
    baseFragmentMembersInjector.injectMembers(fragment);
  }

  @Override
  public void inject(MyFragment fragment) {  
    myFragmentMembersInjector.injectMembers(fragment);
  }

  @Override
  public void inject(PlotDimensionsFragment fragment) {  
    plotDimensionsFragmentMembersInjector.injectMembers(fragment);
  }

  @Override
  public void inject(HistoryActivity activity) {  
    historyActivityMembersInjector.injectMembers(activity);
  }

  @Override
  public void inject(Tabs tabs) {  
    tabsMembersInjector.injectMembers(tabs);
  }

  @Override
  public void inject(CalculatorWidget widget) {  
    calculatorWidgetMembersInjector.injectMembers(widget);
  }

  @Override
  public void inject(WizardActivity activity) {  
    wizardActivityMembersInjector.injectMembers(activity);
  }

  @Override
  public void inject(BaseActivity activity) {  
    baseActivityMembersInjector.injectMembers(activity);
  }

  @Override
  public void inject(PreferencesFragment fragment) {  
    preferencesFragmentMembersInjector.injectMembers(fragment);
  }

  @Override
  public void inject(WizardFragment fragment) {  
    wizardFragmentMembersInjector.injectMembers(fragment);
  }

  @Override
  public void inject(FloatingCalculatorBroadcastReceiver receiver) {  
    MembersInjectors.noOp().injectMembers(receiver);
  }

  public static final class Builder {
    private AppModule appModule;
  
    private Builder() {  
    }
  
    public AppComponent build() {  
      if (appModule == null) {
        throw new IllegalStateException("appModule must be set");
      }
      return new DaggerAppComponent(this);
    }
  
    public Builder appModule(AppModule appModule) {  
      if (appModule == null) {
        throw new NullPointerException("appModule");
      }
      this.appModule = appModule;
      return this;
    }
  }
}

