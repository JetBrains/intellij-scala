package org.jetbrains.plugins.scala.codeInspection.parentheses;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle;

import javax.swing.*;

/**
 * Nikolay.Tropin
 * 6/26/13
 */
public class ScalaUnnecessaryParenthesesInspection extends ScalaUnnecessaryParenthesesInspectionBase {
  public boolean ignoreClarifying = true;

  @Override
  public boolean getIgnoreClarifying() {
    return ignoreClarifying;
  }

  @Override
  public void setIgnoreClarifying(boolean value) {
    ignoreClarifying = value;
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionBundle.message("ignore.clarifying.parentheses"), this, "ignoreClarifying");
  }
}
