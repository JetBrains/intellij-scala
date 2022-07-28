package org.jetbrains.plugins.scala.codeInspection.parameters;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle;

import javax.swing.*;

//for consistency with java reflection-based system of persistent storage of settings
public class NameBooleanParametersInspection extends NameBooleanParametersInspectionBase {

  public boolean ignoreSingleParameter = true;

  @Override
  public boolean isIgnoreSingleParameter() {
    return ignoreSingleParameter;
  }

  @Override
  public void setIgnoreSingleParameter(boolean ignoreSingleParameter) {
    this.ignoreSingleParameter = ignoreSingleParameter;
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(ScalaInspectionBundle.message("name.boolean.ignore.single.parameter.methods"), this, "ignoreSingleParameter");
  }
}
