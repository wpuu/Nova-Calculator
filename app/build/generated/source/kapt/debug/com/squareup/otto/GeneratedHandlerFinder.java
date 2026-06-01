package com.squareup.otto;

import java.lang.Class;
import java.lang.IllegalArgumentException;
import java.lang.NoSuchMethodException;
import java.lang.Object;
import java.lang.String;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.solovyev.android.calculator.Broadcaster;
import org.solovyev.android.calculator.Calculator;
import org.solovyev.android.calculator.CalculatorActivity;
import org.solovyev.android.calculator.Display;
import org.solovyev.android.calculator.Editor;
import org.solovyev.android.calculator.Engine;
import org.solovyev.android.calculator.Keyboard;
import org.solovyev.android.calculator.SecretCodeEvent;
import org.solovyev.android.calculator.VariablesRegistry;
import org.solovyev.android.calculator.calculations.CalculationCancelledEvent;
import org.solovyev.android.calculator.calculations.CalculationFailedEvent;
import org.solovyev.android.calculator.calculations.CalculationFinishedEvent;
import org.solovyev.android.calculator.calculations.ConversionFailedEvent;
import org.solovyev.android.calculator.calculations.ConversionFinishedEvent;
import org.solovyev.android.calculator.floating.FloatingCalculatorService;
import org.solovyev.android.calculator.functions.FunctionsFragment;
import org.solovyev.android.calculator.functions.FunctionsRegistry;
import org.solovyev.android.calculator.history.BaseHistoryFragment;
import org.solovyev.android.calculator.history.History;
import org.solovyev.android.calculator.keyboard.KeyboardUi;
import org.solovyev.android.calculator.memory.Memory;
import org.solovyev.android.calculator.preferences.PreferencesFragment;
import org.solovyev.android.calculator.variables.VariablesFragment;

public final class GeneratedHandlerFinder implements HandlerFinder {
  public Map findAllProducers(final Object listener) {
    return Collections.emptyMap();
  }

  public Map findAllSubscribers(final Object listener) {
    if (listener.getClass().equals(PreferencesFragment.class)) {
      final Map<Class<?>, Set<EventHandler>> handlers = new HashMap<Class<?>, Set<EventHandler>>(1);
      handlers.put(Engine.ChangedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(PreferencesFragment.class, "onEngineChanged", Engine.ChangedEvent.class))));
      return handlers;
    }

    if (listener.getClass().equals(KeyboardUi.class)) {
      final Map<Class<?>, Set<EventHandler>> handlers = new HashMap<Class<?>, Set<EventHandler>>(1);
      handlers.put(Keyboard.NumberModeChangedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(KeyboardUi.class, "onNumberModeChanged", Keyboard.NumberModeChangedEvent.class))));
      return handlers;
    }

    if (listener.getClass().equals(BaseHistoryFragment.HistoryAdapter.class)) {
      final Map<Class<?>, Set<EventHandler>> handlers = new HashMap<Class<?>, Set<EventHandler>>(4);
      handlers.put(History.ClearedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(BaseHistoryFragment.HistoryAdapter.class, "onHistoryCleared", History.ClearedEvent.class))));
      handlers.put(History.AddedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(BaseHistoryFragment.HistoryAdapter.class, "onHistoryAdded", History.AddedEvent.class))));
      handlers.put(History.UpdatedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(BaseHistoryFragment.HistoryAdapter.class, "onHistoryUpdated", History.UpdatedEvent.class))));
      handlers.put(History.RemovedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(BaseHistoryFragment.HistoryAdapter.class, "onHistoryRemoved", History.RemovedEvent.class))));
      return handlers;
    }

    if (listener.getClass().equals(Calculator.class)) {
      final Map<Class<?>, Set<EventHandler>> handlers = new HashMap<Class<?>, Set<EventHandler>>(8);
      handlers.put(FunctionsRegistry.AddedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(Calculator.class, "onFunctionAdded", FunctionsRegistry.AddedEvent.class))));
      handlers.put(FunctionsRegistry.ChangedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(Calculator.class, "onFunctionsChanged", FunctionsRegistry.ChangedEvent.class))));
      handlers.put(VariablesRegistry.ChangedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(Calculator.class, "onVariableChanged", VariablesRegistry.ChangedEvent.class))));
      handlers.put(Display.ChangedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(Calculator.class, "onDisplayChanged", Display.ChangedEvent.class))));
      handlers.put(VariablesRegistry.AddedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(Calculator.class, "onVariableAdded", VariablesRegistry.AddedEvent.class))));
      handlers.put(FunctionsRegistry.RemovedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(Calculator.class, "onFunctionsRemoved", FunctionsRegistry.RemovedEvent.class))));
      handlers.put(Editor.ChangedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(Calculator.class, "onEditorChanged", Editor.ChangedEvent.class))));
      handlers.put(VariablesRegistry.RemovedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(Calculator.class, "onVariableRemoved", VariablesRegistry.RemovedEvent.class))));
      return handlers;
    }

    if (listener.getClass().equals(Display.class)) {
      final Map<Class<?>, Set<EventHandler>> handlers = new HashMap<Class<?>, Set<EventHandler>>(6);
      handlers.put(ConversionFailedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(Display.class, "onConversionFailed", ConversionFailedEvent.class))));
      handlers.put(CalculationCancelledEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(Display.class, "onCalculationCancelled", CalculationCancelledEvent.class))));
      handlers.put(Display.CopyOperation.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(Display.class, "onCopy", Display.CopyOperation.class))));
      handlers.put(ConversionFinishedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(Display.class, "onConversionFinished", ConversionFinishedEvent.class))));
      handlers.put(CalculationFinishedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(Display.class, "onCalculationFinished", CalculationFinishedEvent.class))));
      handlers.put(CalculationFailedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(Display.class, "onCalculationFailed", CalculationFailedEvent.class))));
      return handlers;
    }

    if (listener.getClass().equals(CalculatorActivity.class)) {
      final Map<Class<?>, Set<EventHandler>> handlers = new HashMap<Class<?>, Set<EventHandler>>(1);
      handlers.put(SecretCodeEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(CalculatorActivity.class, "onSecretCodeEvent", SecretCodeEvent.class))));
      return handlers;
    }

    if (listener.getClass().equals(History.class)) {
      final Map<Class<?>, Set<EventHandler>> handlers = new HashMap<Class<?>, Set<EventHandler>>(1);
      handlers.put(Display.ChangedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(History.class, "onDisplayChanged", Display.ChangedEvent.class))));
      return handlers;
    }

    if (listener.getClass().equals(Keyboard.class)) {
      final Map<Class<?>, Set<EventHandler>> handlers = new HashMap<Class<?>, Set<EventHandler>>(3);
      handlers.put(SecretCodeEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(Keyboard.class, "onSecretCodeEvent", SecretCodeEvent.class))));
      handlers.put(Editor.ChangedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(Keyboard.class, "onEditorChanged", Editor.ChangedEvent.class))));
      handlers.put(Editor.CursorMovedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(Keyboard.class, "onCursorMoved", Editor.CursorMovedEvent.class))));
      return handlers;
    }

    if (listener.getClass().equals(FunctionsFragment.class)) {
      final Map<Class<?>, Set<EventHandler>> handlers = new HashMap<Class<?>, Set<EventHandler>>(3);
      handlers.put(FunctionsRegistry.AddedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(FunctionsFragment.class, "onFunctionAdded", FunctionsRegistry.AddedEvent.class))));
      handlers.put(FunctionsRegistry.ChangedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(FunctionsFragment.class, "onFunctionChanged", FunctionsRegistry.ChangedEvent.class))));
      handlers.put(FunctionsRegistry.RemovedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(FunctionsFragment.class, "onFunctionRemoved", FunctionsRegistry.RemovedEvent.class))));
      return handlers;
    }

    if (listener.getClass().equals(Editor.class)) {
      final Map<Class<?>, Set<EventHandler>> handlers = new HashMap<Class<?>, Set<EventHandler>>(2);
      handlers.put(Engine.ChangedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(Editor.class, "onEngineChanged", Engine.ChangedEvent.class))));
      handlers.put(Memory.ValueReadyEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(Editor.class, "onMemoryValueReady", Memory.ValueReadyEvent.class))));
      return handlers;
    }

    if (listener.getClass().equals(Broadcaster.class)) {
      final Map<Class<?>, Set<EventHandler>> handlers = new HashMap<Class<?>, Set<EventHandler>>(3);
      handlers.put(Display.ChangedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(Broadcaster.class, "onDisplayChanged", Display.ChangedEvent.class))));
      handlers.put(Editor.ChangedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(Broadcaster.class, "onEditorChanged", Editor.ChangedEvent.class))));
      handlers.put(Editor.CursorMovedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(Broadcaster.class, "onCursorMoved", Editor.CursorMovedEvent.class))));
      return handlers;
    }

    if (listener.getClass().equals(FloatingCalculatorService.class)) {
      final Map<Class<?>, Set<EventHandler>> handlers = new HashMap<Class<?>, Set<EventHandler>>(3);
      handlers.put(Display.ChangedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(FloatingCalculatorService.class, "onDisplayChanged", Display.ChangedEvent.class))));
      handlers.put(Editor.ChangedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(FloatingCalculatorService.class, "onEditorChanged", Editor.ChangedEvent.class))));
      handlers.put(Editor.CursorMovedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(FloatingCalculatorService.class, "onCursorMoved", Editor.CursorMovedEvent.class))));
      return handlers;
    }

    if (listener.getClass().equals(VariablesFragment.class)) {
      final Map<Class<?>, Set<EventHandler>> handlers = new HashMap<Class<?>, Set<EventHandler>>(3);
      handlers.put(VariablesRegistry.ChangedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(VariablesFragment.class, "onVariableChanged", VariablesRegistry.ChangedEvent.class))));
      handlers.put(VariablesRegistry.AddedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(VariablesFragment.class, "onVariableAdded", VariablesRegistry.AddedEvent.class))));
      handlers.put(VariablesRegistry.RemovedEvent.class, Collections.<EventHandler>singleton(
          new ReflectiveEventHandler(listener, lookupMethod(VariablesFragment.class, "onVariableRemoved", VariablesRegistry.RemovedEvent.class))));
      return handlers;
    }

    throw new IllegalArgumentException("Object with class name " + listener.getClass() + " is not supported");
  }

  public static Method lookupMethod(Class type, String methodName, Class eventType) {
    try {
        return type.getDeclaredMethod(methodName, eventType);
    } catch (NoSuchMethodException e) {
        throw new IllegalArgumentException(e);
    }
  }
}
