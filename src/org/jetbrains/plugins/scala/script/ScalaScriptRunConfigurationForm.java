package org.jetbrains.plugins.scala.script;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.RawCommandLineEditor;

import javax.swing.*;

/**
 * User: Alexander Podkhalyuzin
 * Date: 05.02.2009
 */
public class ScalaScriptRunConfigurationForm {
  private ScalaScriptRunConfiguration myConfiguration;
  private Project myProject;
  private JPanel myPanel;
  private TextFieldWithBrowseButton textFieldWithBrowseButton1;
  private RawCommandLineEditor scriptArgsEditor;
  private RawCommandLineEditor javaOptionsEditor;

  public ScalaScriptRunConfigurationForm(final Project project, final ScalaScriptRunConfiguration configuration) {
    myProject = project;
    myConfiguration = configuration;
    addFileChooser("Select scala script file", textFieldWithBrowseButton1, project);
    scriptArgsEditor.setName("Scala script program arguments");
    scriptArgsEditor.setDialogCaption("Scala script program arguments editor");
    javaOptionsEditor.setName("VM options");
    javaOptionsEditor.setDialogCaption("VM opotions editor");
  }

  public JPanel getPanel() {
    return myPanel;
  }

  public String getScriptPath() {
    return textFieldWithBrowseButton1.getText();
  }

  public void setScriptPath(String scriptPath) {
    textFieldWithBrowseButton1.setText(scriptPath);
  }

  public String getScriptArgs() {
    return scriptArgsEditor.getText();
  }

  public void setScriptArgs(String scriptArgs) {
    scriptArgsEditor.setText(scriptArgs);
  }

  public String getJavaOptions() {
    return javaOptionsEditor.getText();
  }

  public void setJavaOptions(String s) {
    javaOptionsEditor.setText(s);
  }

  public void apply(ScalaScriptRunConfiguration configuration) {
    setScriptArgs(configuration.getScriptArgs());
    setScriptPath(configuration.getScriptPath());
    setJavaOptions(configuration.getJavaOptions());
  }

  private FileChooserDescriptor addFileChooser(final String title,
                                               final TextFieldWithBrowseButton textField,
                                               final Project project) {
    final FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        return super.isFileVisible(file, showHiddenFiles) && (file.isDirectory() || "scala".equals(file.getExtension()));
      }
    };
    fileChooserDescriptor.setTitle(title);
    textField.addBrowseFolderListener(title, null, project, fileChooserDescriptor);
    return fileChooserDescriptor;
  }
}
