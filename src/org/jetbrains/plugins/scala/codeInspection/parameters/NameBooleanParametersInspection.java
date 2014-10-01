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
  public boolean ignoreSetters = true;

  @Override
  public boolean getIgnoreSetters() {
    return ignoreSetters;
  }

  @Override
  public void setIgnoreSetters(boolean value) {
    ignoreSetters = value;
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionBundle.message("name.boolean.ignore.setters"), this, "ignoreSetters");
  }
}
