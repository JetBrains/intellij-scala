package org.jetbrains.plugins.scala.console;

import com.intellij.ui.RawCommandLineEditor;
import com.intellij.openapi.project.Project;

import javax.swing.*;

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.02.2009
 */
public class ScalaScriptConsoleRunConfigurationForm {

  private RawCommandLineEditor javaOptionsEditor;
  private JPanel myPanel;
  private RawCommandLineEditor consoleArgsEditor;
  private Project myProject;
  private ScalaScriptConsoleRunConfiguration myConfiguration;

  public ScalaScriptConsoleRunConfigurationForm(final Project project, final ScalaScriptConsoleRunConfiguration configuration) {
    myProject = project;
    myConfiguration = configuration;
    javaOptionsEditor.setName("VM options");
    javaOptionsEditor.setDialogCaption("VM options editor");
    consoleArgsEditor.setName("Console arguments");
    consoleArgsEditor.setDialogCaption("Console arguments editor");
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
    setConsoleArgs(configuration.getConsoleArgs());
  }

  public String getConsoleArgs() {
    return consoleArgsEditor.getText();
  }

  public void setConsoleArgs(String consoleArgs) {
    this.consoleArgsEditor.setText(consoleArgs);
  }
}
