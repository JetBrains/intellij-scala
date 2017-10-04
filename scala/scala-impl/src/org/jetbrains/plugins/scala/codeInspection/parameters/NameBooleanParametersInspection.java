package org.jetbrains.plugins.scala.codeInspection.parameters;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle;

import javax.swing.*;

/**
 * Nikolay.Tropin
 * 5/28/13
 */

//for consistency with java reflection-based system of persistent storage of settings
public class NameBooleanParametersInspection extends NameBooleanParametersInspectionBase {

  public boolean ignoreSingleParameter = true;

  public boolean isIgnoreSingleParameter() {
    return ignoreSingleParameter;
  }

  public void setIgnoreSingleParameter(boolean ignoreSingleParameter) {
    this.ignoreSingleParameter = ignoreSingleParameter;
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionBundle.message("name.boolean.ignore.single.parameter.methods"), this, "ignoreSingleParameter");
  }
}
