/*
 * Copyright 2013 serso aka se.solovyev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.solovyev.android.calculator;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.squareup.otto.Bus;

import org.solovyev.android.calculator.ai.AiExplainCoordinator;
import org.solovyev.android.calculator.ai.AiGatewayFeatureConfig;
import org.solovyev.android.calculator.ai.AiGatewayRequest;
import org.solovyev.android.calculator.ai.AiGatewayResponse;
import org.solovyev.android.calculator.converter.ConverterFragment;
import org.solovyev.android.calculator.jscl.JsclOperation;

import java.util.Locale;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import jscl.NumeralBase;
import jscl.math.Generic;
import jscl.math.NotDoubleException;

public class DisplayFragment extends BaseFragment implements View.OnClickListener,
        MenuItem.OnMenuItemClickListener {

    private enum ConversionMenuItem {
        to_bin(NumeralBase.bin, R.string.convert_to_bin),
        to_dec(NumeralBase.dec, R.string.convert_to_dec),
        to_hex(NumeralBase.hex, R.string.convert_to_hex);

        @Nonnull
        public final NumeralBase toNumeralBase;
        public final int title;

        ConversionMenuItem(@Nonnull NumeralBase toNumeralBase, @StringRes int title) {
            this.toNumeralBase = toNumeralBase;
            this.title = title;
        }

        @Nullable
        public static ConversionMenuItem getByTitle(int title) {
            if (title == R.string.convert_to_bin) return to_bin;
            if (title == R.string.convert_to_dec) return to_dec;
            if (title == R.string.convert_to_hex) return to_hex;
            return null;
        }
    }

    DisplayView displayView;
    @Inject SharedPreferences preferences;
    @Inject ErrorReporter errorReporter;
    @Inject Display display;
    @Inject ActivityLauncher launcher;
    @Inject Bus bus;
    @Inject Calculator calculator;
    @Inject Engine engine;
    @Inject Editor editor;
    @Inject AiGatewayFeatureConfig aiGatewayFeatureConfig;
    @Inject AiExplainCoordinator aiExplainCoordinator;
    @Nullable private AlertDialog aiExplanationDialog;

    public DisplayFragment() {
        super(R.layout.cpp_app_display);
    }

    @Override
    protected void inject(@Nonnull AppComponent component) {
        super.inject(component);
        component.inject(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = super.onCreateView(inflater, container, savedInstanceState);
        displayView = view.findViewById(R.id.calculator_display);
        display.setView(displayView);
        displayView.setOnClickListener(this);
        return view;
    }

    @Override
    public void onDestroyView() {
        aiExplainCoordinator.cancelCurrent();
        dismissAiDialog();
        display.clearView(displayView);
        super.onDestroyView();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        final DisplayState state = display.getState();
        if (!state.valid) return;
        addMenu(menu, R.string.cpp_copy, this);
        if (canExplain(state)) {
            addMenu(menu, R.string.nova_ai_explain_action, this);
            addMenu(menu, R.string.nova_ai_follow_up_action, this);
        }

        final Generic result = state.getResult();
        final JsclOperation operation = state.getOperation();
        if (result != null) {
            if (operation == JsclOperation.numeric && result.getConstants().isEmpty()) {
                for (ConversionMenuItem item : ConversionMenuItem.values()) {
                    if (isMenuItemVisible(item, result)) addMenu(menu, item.title, this);
                }
                try {
                    result.doubleValue();
                    addMenu(menu, R.string.c_convert, this);
                } catch (NotDoubleException ignored) {
                }
            }
            if (launcher.canPlot(result)) addMenu(menu, R.string.c_plot, this);
        }
    }

    private boolean canExplain(@Nonnull DisplayState state) {
        if (!aiGatewayFeatureConfig.isEnabled() || !state.valid) return false;
        final String expression = editor.getState().getTextString();
        return expression != null
                && !expression.trim().isEmpty()
                && state.text != null
                && !state.text.trim().isEmpty();
    }

    protected boolean isMenuItemVisible(@NonNull ConversionMenuItem menuItem,
                                        @Nonnull Generic generic) {
        final NumeralBase fromNumeralBase = engine.getMathEngine().getNumeralBase();
        return fromNumeralBase != menuItem.toNumeralBase
                && calculator.canConvert(generic, fromNumeralBase, menuItem.toNumeralBase);
    }

    @Override
    public void onClick(View v) {
        final DisplayState state = display.getState();
        if (state.valid) {
            v.setOnCreateContextMenuListener(this);
            v.showContextMenu();
            v.setOnCreateContextMenuListener(null);
        } else {
            showEvaluationError(v.getContext(), state.text);
        }
    }

    public static void showEvaluationError(@Nonnull Context context,
                                           @Nonnull final String errorMessage) {
        new AlertDialog.Builder(context, App.getTheme().alertDialogTheme)
                .setPositiveButton(R.string.cpp_cancel, null)
                .setMessage(errorMessage).create().show();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        final DisplayState state = display.getState();
        final Generic result = state.getResult();
        int itemId = item.getItemId();
        if (itemId == R.string.cpp_copy) {
            display.copy();
            return true;
        } else if (itemId == R.string.nova_ai_explain_action) {
            explainCurrentCalculation(state);
            return true;
        } else if (itemId == R.string.nova_ai_follow_up_action) {
            askAboutCurrentCalculation(state);
            return true;
        } else if (itemId == R.string.convert_to_bin || itemId == R.string.convert_to_dec || itemId == R.string.convert_to_hex) {
            final ConversionMenuItem menuItem = ConversionMenuItem.getByTitle(item.getItemId());
            if (menuItem == null) return false;
            if (result != null) calculator.convert(state, menuItem.toNumeralBase);
            return true;
        } else if (itemId == R.string.c_convert) {
            ConverterFragment.show(getActivity(), getValue(result));
            return true;
        } else if (itemId == R.string.c_plot) {
            launcher.plot(result);
            return true;
        }
        return false;
    }

    private void explainCurrentCalculation(@Nonnull DisplayState state) {
        if (!canExplain(state) || getActivity() == null) return;
        final String expression = editor.getState().getTextString().trim();
        final String deterministicResult = state.text.trim();
        final String localeTag = Locale.getDefault().toLanguageTag();

        showAiAnswerDialog(
                R.string.nova_ai_explain_title,
                R.string.nova_ai_explain_loading,
                listener -> aiExplainCoordinator.explain(
                        expression,
                        deterministicResult,
                        localeTag,
                        listener),
                R.string.nova_ai_invalid_request);
    }

    private void askAboutCurrentCalculation(@Nonnull DisplayState state) {
        if (!canExplain(state) || getActivity() == null) return;
        final EditText input = new EditText(getActivity());
        input.setHint(R.string.nova_ai_follow_up_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMinLines(2);
        input.setMaxLines(5);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)});

        new AlertDialog.Builder(getActivity(), App.getTheme().alertDialogTheme)
                .setTitle(R.string.nova_ai_follow_up_title)
                .setMessage(R.string.nova_ai_follow_up_description)
                .setView(input)
                .setNegativeButton(R.string.cpp_cancel, null)
                .setPositiveButton(R.string.nova_ai_follow_up_submit, (dialog, which) -> {
                    final String question = input.getText() == null ? "" : input.getText().toString().trim();
                    if (question.isEmpty()) {
                        Toast.makeText(getActivity(), R.string.nova_ai_follow_up_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    followUpCurrentCalculation(state, question);
                })
                .show();
    }

    private void followUpCurrentCalculation(@Nonnull DisplayState state,
                                            @Nonnull String question) {
        if (!canExplain(state) || getActivity() == null) return;
        final String expression = editor.getState().getTextString().trim();
        final String deterministicResult = state.text.trim();
        final String localeTag = Locale.getDefault().toLanguageTag();

        showAiAnswerDialog(
                R.string.nova_ai_follow_up_title,
                R.string.nova_ai_follow_up_loading,
                listener -> aiExplainCoordinator.followUp(
                        expression,
                        deterministicResult,
                        question,
                        localeTag,
                        listener),
                R.string.nova_ai_follow_up_invalid);
    }

    private void showAiAnswerDialog(@StringRes int title,
                                    @StringRes int loadingMessage,
                                    @Nonnull AiRequestStarter starter,
                                    @StringRes int invalidMessage) {
        if (getActivity() == null) return;
        aiExplainCoordinator.cancelCurrent();
        dismissAiDialog();

        final AlertDialog dialog = new AlertDialog.Builder(
                getActivity(), App.getTheme().alertDialogTheme)
                .setTitle(title)
                .setMessage(loadingMessage)
                .setNegativeButton(R.string.cpp_cancel, (d, which) -> aiExplainCoordinator.cancelCurrent())
                .create();
        aiExplanationDialog = dialog;
        dialog.setOnDismissListener(d -> {
            if (aiExplanationDialog == dialog) {
                aiExplanationDialog = null;
                aiExplainCoordinator.cancelCurrent();
            }
        });
        dialog.show();

        starter.start(new AiExplainCoordinator.Listener() {
            @Override
            public void onStarted(AiGatewayRequest request) {
            }

            @Override
            public void onFinished(AiGatewayResponse response) {
                if (!isAdded() || aiExplanationDialog != dialog || !dialog.isShowing()) return;
                dialog.setMessage(messageFor(response, invalidMessage));
            }
        });
    }

    @Nonnull
    private CharSequence messageFor(@Nullable AiGatewayResponse response,
                                    @StringRes int invalidMessage) {
        if (response == null) return getString(R.string.nova_ai_unavailable);
        switch (response.getStatus()) {
            case SUCCESS:
                final String answer = response.getAnswer();
                return answer == null || answer.trim().isEmpty()
                        ? getString(R.string.nova_ai_unavailable)
                        : answer.trim();
            case AUTH_REQUIRED:
                return getString(R.string.nova_ai_auth_required);
            case QUOTA_EXHAUSTED:
                return getString(R.string.nova_ai_quota_exhausted);
            case RATE_LIMITED:
                return getString(R.string.nova_ai_rate_limited);
            case INVALID_REQUEST:
                return getString(invalidMessage);
            case TEMPORARILY_UNAVAILABLE:
            default:
                return getString(R.string.nova_ai_unavailable);
        }
    }

    private void dismissAiDialog() {
        final AlertDialog dialog = aiExplanationDialog;
        aiExplanationDialog = null;
        if (dialog != null) {
            dialog.setOnDismissListener(null);
            if (dialog.isShowing()) dialog.dismiss();
        }
    }

    private static double getValue(@Nullable Generic result) {
        if (result == null) return 1d;
        try {
            return result.doubleValue();
        } catch (NotDoubleException ignored) {
            return 1d;
        }
    }

    private interface AiRequestStarter {
        void start(AiExplainCoordinator.Listener listener);
    }
}
