package org.jetbrains.plugins.scala.compiler.server;

import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.ui.RawCommandLineEditor;
import org.jetbrains.plugins.scala.script.ScalaScriptRunConfiguration;

import javax.swing.*;

/**
 * User: Alexander Podkhalyuzin
 * Date: 26.01.2010
 */
public class ScalaCompilationServerRunConfigurationForm {
  private Project myProject;
  private ScalaCompilationServerRunConfiguration myConfiguration;
  private RawCommandLineEditor javaOptionsEditor;
  private JPanel myPanel;
  private JComboBox moduleComboBox;

  private ConfigurationModuleSelector myModuleSelector;

  public ScalaCompilationServerRunConfigurationForm(final Project project, final ScalaCompilationServerRunConfiguration configuration) {
    myModuleSelector = new ConfigurationModuleSelector(project, moduleComboBox);
    myModuleSelector.reset(configuration);
    moduleComboBox.setEnabled(true);
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

  public void apply(ScalaCompilationServerRunConfiguration configuration) {
    setJavaOptions(configuration.getJavaOptions());

    myModuleSelector.applyTo(configuration);
  }

  public Module getModule() {
    return myModuleSelector.getModule();
  }
}
