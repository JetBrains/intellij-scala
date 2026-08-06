package org.jetbrains.plugins.scala.codeInspection.parentheses;

import com.intellij.codeInspection.options.OptPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle;

import static com.intellij.codeInspection.options.OptPane.*;

public class ScalaUnnecessaryParenthesesInspection extends ScalaUnnecessaryParenthesesInspectionBase {
    public boolean ignoreClarifying = true;
    public boolean ignoreAroundFunctionType = false;
    public boolean ignoreAroundFunctionTypeParam = false;
    public boolean ignoreAroundFunctionExprParam = false;

    @Override
    public UnnecessaryParenthesesSettings currentSettings() {
        return new UnnecessaryParenthesesSettings(ignoreClarifying, ignoreAroundFunctionType, ignoreAroundFunctionTypeParam, ignoreAroundFunctionExprParam);
    }

    @Override
    public void setSettings(UnnecessaryParenthesesSettings settings) {
        ignoreClarifying = settings.ignoreClarifying();
        ignoreAroundFunctionType = settings.ignoreAroundFunctionType();
        ignoreAroundFunctionTypeParam = settings.ignoreAroundFunctionTypeParam();
        ignoreAroundFunctionExprParam = settings.ignoreAroundFunctionExprParam();
    }

    @Override
    public @NotNull OptPane getOptionsPane() {
        return pane(
                group(ScalaInspectionBundle.message("unnecessary.parentheses.ignore"),
                        checkbox("ignoreClarifying", ScalaInspectionBundle.message("clarifying.parentheses")),
                        checkbox("ignoreAroundFunctionType", ScalaInspectionBundle.message("around.function.type")),
                        checkbox("ignoreAroundFunctionTypeParam", ScalaInspectionBundle.message("around.function.type.parameter")),
                        checkbox("ignoreAroundFunctionExprParam", ScalaInspectionBundle.message("around.function.expr.parameter"))
                )
        );
    }
}
