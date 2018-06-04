package org.jetbrains.plugins.scala.codeInspection.parentheses;

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.codeInspection.ui.OptionAccessor;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle;

import javax.swing.*;

/**
 * Nikolay.Tropin
 * 6/26/13
 */
public class ScalaUnnecessaryParenthesesInspection extends ScalaUnnecessaryParenthesesInspectionBase {
  public boolean ignoreClarifying = true;
  public boolean ignoreAroundFunctionType = false;
  public boolean ignoreAroundFunctionTypeParam = false;
  public boolean ignoreAroundFunctionExprParam = false;

  public UnnecessaryParenthesesSettings currentSettings() {
    return new UnnecessaryParenthesesSettings(ignoreClarifying, ignoreAroundFunctionType, ignoreAroundFunctionTypeParam, ignoreAroundFunctionExprParam);
  }

  public void setSettings(UnnecessaryParenthesesSettings settings) {
    ignoreClarifying = settings.ignoreClarifying();
    ignoreAroundFunctionType = settings.ignoreAroundFunctionType();
    ignoreAroundFunctionTypeParam = settings.ignoreAroundFunctionTypeParam();
    ignoreAroundFunctionExprParam = settings.ignoreAroundFunctionExprParam();
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(new OptionAccessor.Default(this));
    panel.add(new JBLabel("Ignore"));
    panel.addCheckbox(InspectionBundle.message("clarifying.parentheses"), "ignoreClarifying");
    panel.addCheckbox(InspectionBundle.message("around.function.type"), "ignoreAroundFunctionType");
    panel.addCheckbox(InspectionBundle.message("around.function.type.parameter"), "ignoreAroundFunctionTypeParam");
    panel.addCheckbox(InspectionBundle.message("around.function.expr.parameter"), "ignoreAroundFunctionExprParam");
    return panel;
  }
}
