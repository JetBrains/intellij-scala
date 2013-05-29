package org.jetbrains.plugins.scala.codeInspection.parameters;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle;

import javax.swing.*;

/**
 * Nikolay.Tropin
 * 5/28/13
 */

//for consistency with java reflection-based system of persistent storage of settings
public class NameBooleanParametersInspection extends NameBooleanParametersInspectionStub{
  public boolean ignoreSetters = false;

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
