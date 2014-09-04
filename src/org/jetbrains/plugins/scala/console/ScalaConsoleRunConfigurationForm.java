package org.jetbrains.plugins.scala.console;

import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.RawCommandLineEditor;

import javax.swing.*;

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.02.2009
 */
public class ScalaConsoleRunConfigurationForm {

  private RawCommandLineEditor javaOptionsEditor;
  private JPanel myPanel;
  private RawCommandLineEditor consoleArgsEditor;
  private TextFieldWithBrowseButton workingDirectoryField;
  private JComboBox moduleComboBox;
  private Project myProject;
  private ScalaConsoleRunConfiguration myConfiguration;

  private ConfigurationModuleSelector myModuleSelector;

  public ScalaConsoleRunConfigurationForm(final Project project,
                                          final ScalaConsoleRunConfiguration configuration) {
    myModuleSelector = new ConfigurationModuleSelector(project, moduleComboBox);
    myModuleSelector.reset(configuration);
    moduleComboBox.setEnabled(true);
    myProject = project;
    myConfiguration = configuration;
    javaOptionsEditor.setName("VM options");
    javaOptionsEditor.setDialogCaption("VM options editor");
    javaOptionsEditor.setText("-Djline.terminal=NONE");
    consoleArgsEditor.setName("Console arguments");
    consoleArgsEditor.setDialogCaption("Console arguments editor");
    consoleArgsEditor.setText("-usejavacp");
    addFileChooser("Choose Working Directory", workingDirectoryField, project);
    VirtualFile baseDir = project.getBaseDir();
    String path = baseDir != null ? baseDir.getPath() : "";
    workingDirectoryField.setText(path);
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

  public void apply(ScalaConsoleRunConfiguration configuration) {
    setJavaOptions(configuration.javaOptions());
    setConsoleArgs(configuration.consoleArgs());
    setWorkingDirectory(configuration.workingDirectory());

    myModuleSelector.applyTo(configuration);
  }

  public String getConsoleArgs() {
    return consoleArgsEditor.getText();
  }

  public void setConsoleArgs(String consoleArgs) {
    this.consoleArgsEditor.setText(consoleArgs);
  }

  public String getWorkingDirectory() {
    return workingDirectoryField.getText();
  }

  public void setWorkingDirectory(String s) {
    workingDirectoryField.setText(s);
  }

  public Module getModule() {
    return myModuleSelector.getModule();
  }

  private FileChooserDescriptor addFileChooser(final String title,
                                               final TextFieldWithBrowseButton textField,
                                               final Project project) {
    final FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(false, true, false, false, false, false) {
      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        return super.isFileVisible(file, showHiddenFiles) && file.isDirectory();
      }
    };
    fileChooserDescriptor.setTitle(title);
    textField.addBrowseFolderListener(title, null, project, fileChooserDescriptor);
    return fileChooserDescriptor;
  }
}
