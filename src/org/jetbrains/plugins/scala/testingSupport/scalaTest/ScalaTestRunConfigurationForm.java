package org.jetbrains.plugins.scala.testingSupport.scalaTest;

import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.scala.script.ScalaScriptRunConfiguration;

import javax.swing.*;

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.02.2009
 */
public class ScalaTestRunConfigurationForm {
  private JPanel myPanel;

  public ScalaTestRunConfigurationForm(final Project project, final ScalaTestRunConfiguration configuration) {

  }

  public void apply(ScalaTestRunConfiguration configuration) {
    //todo
  }

  public JPanel getPanel() {
    return myPanel;
  }
}
