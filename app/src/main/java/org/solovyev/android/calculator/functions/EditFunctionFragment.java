package org.solovyev.android.calculator.functions;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import org.solovyev.android.Check;
import org.solovyev.android.calculator.App;
import org.solovyev.android.calculator.AppComponent;
import org.solovyev.android.calculator.Engine;
import org.solovyev.android.calculator.PreparedExpression;
import org.solovyev.android.calculator.R;
import org.solovyev.android.calculator.RemovalConfirmationDialog;
import org.solovyev.android.calculator.Utils;
import org.solovyev.android.calculator.ai.AiFormulaCandidate;
import org.solovyev.android.calculator.ai.AiGatewayClient;
import org.solovyev.android.calculator.ai.AiGatewayFeatureConfig;
import org.solovyev.android.calculator.ai.AiGatewayRequest;
import org.solovyev.android.calculator.ai.AiGatewayResponse;
import org.solovyev.android.calculator.ai.AiGatewayStatus;
import org.solovyev.android.calculator.ai.AiRequests;

import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;

import jscl.math.function.Function;
import jscl.math.function.IConstant;

public class EditFunctionFragment extends BaseFunctionFragment {

    @Inject AiGatewayFeatureConfig aiGatewayFeatureConfig;
    @Inject AiGatewayClient aiGatewayClient;
    @Nullable private AlertDialog formulaLoadingDialog;
    private long formulaGeneration;

    public EditFunctionFragment() {
        super(R.layout.fragment_function_edit);
    }

    public static void show(@Nonnull FragmentActivity activity) {
        show(null, activity.getSupportFragmentManager());
    }

    public static void show(@Nullable CppFunction function, @Nonnull Context context) {
        if (!(context instanceof FunctionsActivity)) {
            final Intent intent = new Intent(context, FunctionsActivity.getClass(context));
            App.addIntentFlags(intent, false, context);
            intent.putExtra(FunctionsActivity.EXTRA_FUNCTION, function);
            context.startActivity(intent);
        } else {
            show(function, ((FunctionsActivity) context).getSupportFragmentManager());
        }
    }

    public static void show(@Nullable CppFunction function, @Nonnull FragmentManager fm) {
        App.showDialog(create(function), "function-editor", fm);
    }

    @Nonnull
    private static BaseFunctionFragment create(@Nullable CppFunction function) {
        final BaseFunctionFragment fragment = new EditFunctionFragment();
        if (function != null) {
            final Bundle args = new Bundle();
            args.putParcelable(ARG_FUNCTION, function);
            fragment.setArguments(args);
        }
        return fragment;
    }

    @Override
    protected void inject(@NonNull AppComponent component) {
        super.inject(component);
        component.inject(this);
    }

    @Override
    protected void onPrepareDialog(@NonNull AlertDialog.Builder builder) {
        super.onPrepareDialog(builder);
        if (isNewFunction() && aiGatewayFeatureConfig != null && aiGatewayFeatureConfig.isEnabled()) {
            builder.setNeutralButton(R.string.nova_ai_formula_action, null);
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_NEUTRAL
                && isNewFunction()
                && aiGatewayFeatureConfig != null
                && aiGatewayFeatureConfig.isEnabled()) {
            showAiFormulaPrompt();
            return;
        }
        super.onClick(dialog, which);
    }

    @Override
    public void onDestroyView() {
        formulaGeneration++;
        dismissFormulaLoading();
        super.onDestroyView();
    }

    private void showAiFormulaPrompt() {
        if (getActivity() == null) return;
        final EditText input = new EditText(getActivity());
        input.setHint(R.string.nova_ai_formula_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMinLines(2);
        input.setMaxLines(5);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)});

        new AlertDialog.Builder(getActivity(), App.getTheme().alertDialogTheme)
                .setTitle(R.string.nova_ai_formula_title)
                .setMessage(R.string.nova_ai_formula_description)
                .setView(input)
                .setNegativeButton(R.string.cpp_cancel, null)
                .setPositiveButton(R.string.nova_ai_formula_submit, (promptDialog, which) -> {
                    final String goal = input.getText() == null ? "" : input.getText().toString().trim();
                    if (goal.isEmpty()) {
                        Toast.makeText(getActivity(), R.string.nova_ai_formula_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    requestAiFormula(goal);
                })
                .show();
    }

    private void requestAiFormula(@Nonnull String goal) {
        if (getActivity() == null || aiGatewayClient == null) return;
        final AiGatewayRequest request = AiRequests.buildFormula(
                goal,
                Locale.getDefault().toLanguageTag());
        final long generation = ++formulaGeneration;
        dismissFormulaLoading();

        final AlertDialog loading = new AlertDialog.Builder(
                getActivity(), App.getTheme().alertDialogTheme)
                .setTitle(R.string.nova_ai_formula_title)
                .setMessage(R.string.nova_ai_formula_loading)
                .setNegativeButton(R.string.cpp_cancel, (dialog, which) -> formulaGeneration++)
                .create();
        formulaLoadingDialog = loading;
        loading.setOnDismissListener(dialog -> {
            if (formulaLoadingDialog == loading) formulaLoadingDialog = null;
        });
        loading.show();

        try {
            aiGatewayClient.execute(request, response -> {
                if (generation != formulaGeneration || !isAdded()) return;
                if (formulaLoadingDialog == loading && loading.isShowing()) loading.dismiss();
                formulaLoadingDialog = null;
                handleFormulaResponse(response);
            });
        } catch (RuntimeException e) {
            if (generation == formulaGeneration) {
                dismissFormulaLoading();
                showFormulaMessage(R.string.nova_ai_unavailable);
            }
        }
    }

    private void handleFormulaResponse(@Nullable AiGatewayResponse response) {
        if (response == null) {
            showFormulaMessage(R.string.nova_ai_unavailable);
            return;
        }
        if (response.getStatus() != AiGatewayStatus.SUCCESS) {
            showFormulaMessage(messageForStatus(response.getStatus()));
            return;
        }

        final AiFormulaCandidate candidate;
        try {
            candidate = AiFormulaCandidate.parse(response.getAnswer());
        } catch (RuntimeException e) {
            showFormulaMessage(R.string.nova_ai_formula_invalid);
            return;
        }
        if (!validateCandidateLocally(candidate)) {
            showFormulaMessage(R.string.nova_ai_formula_local_invalid);
            return;
        }

        nameView.setText(candidate.getName());
        while (!paramsView.getParams().isEmpty()) {
            paramsView.removeViewAt(0);
        }
        paramsView.addParams(candidate.getParameters());
        bodyView.setText(candidate.getExpression());
        descriptionView.setText(candidate.getDescription());
        showFormulaMessage(R.string.nova_ai_formula_applied);
    }

    private boolean validateCandidateLocally(@Nonnull AiFormulaCandidate candidate) {
        try {
            final PreparedExpression prepared = calculator.prepare(candidate.getExpression());
            if (prepared.hasUndefinedVariables()) {
                for (IConstant variable : prepared.getUndefinedVariables()) {
                    if (!candidate.getParameters().contains(variable.getName())) return false;
                }
            }
            CppFunction.builder(candidate.getName(), prepared.getValue())
                    .withParameters(candidate.getParameters())
                    .withDescription(candidate.getDescription())
                    .build()
                    .toJsclBuilder()
                    .create();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private int messageForStatus(@Nonnull AiGatewayStatus status) {
        switch (status) {
            case AUTH_REQUIRED:
                return R.string.nova_ai_auth_required;
            case QUOTA_EXHAUSTED:
                return R.string.nova_ai_quota_exhausted;
            case RATE_LIMITED:
                return R.string.nova_ai_rate_limited;
            case INVALID_REQUEST:
                return R.string.nova_ai_formula_invalid;
            case TEMPORARILY_UNAVAILABLE:
            default:
                return R.string.nova_ai_unavailable;
        }
    }

    private void showFormulaMessage(int message) {
        if (getActivity() != null) Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
    }

    private void dismissFormulaLoading() {
        final AlertDialog dialog = formulaLoadingDialog;
        formulaLoadingDialog = null;
        if (dialog != null) {
            dialog.setOnDismissListener(null);
            if (dialog.isShowing()) dialog.dismiss();
        }
    }

    @Override
    protected boolean applyData(@Nonnull @NonNull CppFunction function) {
        try {
            final Function oldFunction = isNewFunction() ? null : functionsRegistry.getById(function.id);
            functionsRegistry.addOrUpdate(function.toJsclBuilder().create(), oldFunction);
            return true;
        } catch (RuntimeException e) {
            setError(bodyLabel, Utils.getErrorMessage(e));
        }
        return false;
    }

    @Override
    protected boolean validateName() {
        final String name = nameView.getText().toString();
        if (TextUtils.isEmpty(name)) {
            setError(nameLabel, getString(R.string.cpp_field_cannot_be_empty));
            return false;
        }
        if (!Engine.isValidName(name)) {
            setError(nameLabel, getString(R.string.cpp_name_contains_invalid_characters));
            return false;
        }
        final Function existingFunction = functionsRegistry.get(name);
        if (existingFunction != null) {
            if (!existingFunction.isIdDefined()) {
                Check.shouldNotHappen();
                setError(nameLabel, getString(R.string.function_already_exists));
                return false;
            }
            if (isNewFunction()) {
                setError(nameLabel, getString(R.string.function_already_exists));
                return false;
            }
            Check.isNotNull(function);
            if (!existingFunction.getId().equals(function.getId())) {
                setError(nameLabel, getString(R.string.function_already_exists));
                return false;
            }
        }
        clearError(nameLabel);
        return true;
    }

    protected void showRemovalDialog(@NonNull final CppFunction function) {
        RemovalConfirmationDialog.showForFunction(getActivity(), function.name,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Check.isTrue(which == DialogInterface.BUTTON_POSITIVE);
                        functionsRegistry.remove(function.toJsclBuilder().create());
                        dismiss();
                    }
                });
    }
}
