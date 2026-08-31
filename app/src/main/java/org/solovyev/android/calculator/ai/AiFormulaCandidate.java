package org.solovyev.android.calculator.ai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict client-side parser for the server-sanitized reusable formula candidate. */
public final class AiFormulaCandidate {

    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,31}$");
    private static final Pattern EXPRESSION = Pattern.compile("^[A-Za-z0-9_+\\-*/^().,\\s]+$");

    private final String name;
    private final List<String> parameters;
    private final String expression;
    private final String description;

    private AiFormulaCandidate(String name,
                               List<String> parameters,
                               String expression,
                               String description) {
        this.name = name;
        this.parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
        this.expression = expression;
        this.description = description;
    }

    public static AiFormulaCandidate parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("formula candidate must not be blank");
        }
        try {
            final JSONObject json = new JSONObject(value);
            if (json.length() != 4
                    || !json.has("name")
                    || !json.has("parameters")
                    || !json.has("expression")
                    || !json.has("description")) {
                throw new IllegalArgumentException("formula candidate schema is invalid");
            }

            final String name = json.getString("name").trim();
            final String expression = json.getString("expression").trim();
            final String description = json.getString("description").trim();
            final JSONArray array = json.getJSONArray("parameters");
            if (!IDENTIFIER.matcher(name).matches() || array.length() < 1 || array.length() > 8) {
                throw new IllegalArgumentException("formula candidate identifiers are invalid");
            }

            final List<String> parameters = new ArrayList<>();
            final Set<String> unique = new HashSet<>();
            for (int i = 0; i < array.length(); i++) {
                final String parameter = array.getString(i).trim();
                if (!IDENTIFIER.matcher(parameter).matches() || !unique.add(parameter)) {
                    throw new IllegalArgumentException("formula candidate parameters are invalid");
                }
                parameters.add(parameter);
            }

            if (expression.isEmpty()
                    || expression.length() > 1024
                    || !EXPRESSION.matcher(expression).matches()
                    || expression.indexOf('\n') >= 0
                    || expression.indexOf('\r') >= 0
                    || expression.indexOf('\t') >= 0
                    || !balancedParentheses(expression)) {
                throw new IllegalArgumentException("formula candidate expression is invalid");
            }
            if (description.length() > 500) {
                throw new IllegalArgumentException("formula candidate description is too long");
            }
            boolean usesParameter = false;
            for (String parameter : parameters) {
                if (Pattern.compile("\\b" + Pattern.quote(parameter) + "\\b").matcher(expression).find()) {
                    usesParameter = true;
                    break;
                }
            }
            if (!usesParameter) {
                throw new IllegalArgumentException("formula candidate does not use its parameters");
            }
            return new AiFormulaCandidate(name, parameters, expression.replaceAll(" {2,}", " "), description);
        } catch (JSONException e) {
            throw new IllegalArgumentException("formula candidate JSON is invalid", e);
        }
    }

    public String getName() { return name; }
    public List<String> getParameters() { return parameters; }
    public String getExpression() { return expression; }
    public String getDescription() { return description; }

    private static boolean balancedParentheses(String expression) {
        int depth = 0;
        for (int i = 0; i < expression.length(); i++) {
            final char character = expression.charAt(i);
            if (character == '(') depth++;
            if (character == ')') {
                depth--;
                if (depth < 0) return false;
            }
        }
        return depth == 0;
    }
}
