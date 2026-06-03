package org.jetbrains.plugins.scala.codeInspection.parameters;

import com.intellij.codeInspection.options.OptPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

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

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(checkbox("ignoreSingleParameter", ScalaInspectionBundle.message("name.boolean.ignore.single.parameter.methods")));
  }
}
