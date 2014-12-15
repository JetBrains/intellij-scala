package org.jetbrains.sbt.runner;

import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.ui.RawCommandLineEditor;

import javax.swing.*;
import java.util.Map;

/**
 * Form with configuration of SBT runner.
 */
public class SbtRunConfigurationForm {
  private JPanel mainPanel;
  private RawCommandLineEditor tasksEditor;
  private RawCommandLineEditor javaOptionsEditor;
  private EnvironmentVariablesComponent environmentVariables;

  public SbtRunConfigurationForm(final Project project, final SbtRunConfiguration configuration) {
    tasksEditor.setName("Scala script program arguments");
    tasksEditor.setDialogCaption("Scala script program arguments editor");
    javaOptionsEditor.setDialogCaption("VM parameters editor");
    environmentVariables.setEnvs(configuration.getEnvironmentVariables());
  }

  public JPanel getMainPanel() {
    return mainPanel;
  }

  /**
   * @return tasks to execute.
   */
  public String getTasks() {
    return tasksEditor.getText();
  }

  /**
   * @return java options.
   */
  public String getJavaOptions() {
    return javaOptionsEditor.getText();
  }

  /**
   * @return envirnoment variables.
   */
  public Map<String, String> getEnvironmentVariables() {
    return environmentVariables.getEnvs();
  }

  public void apply(SbtRunConfiguration configuration) {
    tasksEditor.setText(configuration.getTasks());
    javaOptionsEditor.setText(configuration.getJavaOptions());
    environmentVariables.setEnvs(configuration.getEnvironmentVariables());
  }
}
