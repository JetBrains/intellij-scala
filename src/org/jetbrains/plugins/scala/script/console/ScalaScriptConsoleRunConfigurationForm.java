package org.jetbrains.plugins.scala.script.console;

import com.intellij.ui.RawCommandLineEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.scala.script.ScalaScriptRunConfiguration;

import javax.swing.*;

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.02.2009
 */
public class ScalaScriptConsoleRunConfigurationForm {

  private RawCommandLineEditor javaOptionsEditor;
  private JPanel myPanel;
  private Project myProject;
  private ScalaScriptConsoleRunConfiguration myConfiguration;

  public ScalaScriptConsoleRunConfigurationForm(final Project project, final ScalaScriptConsoleRunConfiguration configuration) {
    myProject = project;
    myConfiguration = configuration;
    javaOptionsEditor.setName("VM options");
    javaOptionsEditor.setDialogCaption("VM opotions editor");
  }

  public JPanel getPanel() {
    return myPanel;
  }

  public String getJavaOptions() {
    return javaOptionsEditor.getText();
  }

  public void setJavaOptions(String s) {
    javaOptionsEditor.setText(s);
  }

  public void apply(ScalaScriptConsoleRunConfiguration configuration) {
    setJavaOptions(configuration.getJavaOptions());
  }
}
