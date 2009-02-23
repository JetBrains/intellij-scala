package org.jetbrains.plugins.scala.testingSupport.scalaTest;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.module.Module;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.junit2.configuration.ClassBrowser;
import com.intellij.execution.configurations.ConfigurationUtil;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserDialog;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.PsiClass;
import com.intellij.psi.JavaPsiFacade;
import org.jetbrains.plugins.scala.script.ScalaScriptRunConfiguration;

import javax.swing.*;

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.02.2009
 */
public class ScalaTestRunConfigurationForm {
  private JPanel myPanel;
  private TextFieldWithBrowseButton testClassTextField;
  private RawCommandLineEditor VMParamsTextField;
  private RawCommandLineEditor testOptionsTextField;

  public ScalaTestRunConfigurationForm(final Project project, final ScalaTestRunConfiguration configuration) {
    addFileChooser("Choose test class", testClassTextField, project);
    VMParamsTextField.setDialogCaption("VM parameters editor");
    testOptionsTextField.setDialogCaption("Additional options editor");
  }

  public void apply(ScalaTestRunConfiguration configuration) {
    setTestClassPath(configuration.getTestClassPath());
    setJavaOptions(configuration.getJavaOptions());
    setTestArgs(configuration.getTestArgs());
  }

  public String getTestClassPath() {
    return testClassTextField.getText();
  }

  public String getTestArgs() {
    return testOptionsTextField.getText();
  }

  public String getJavaOptions() {
    return VMParamsTextField.getText();
  }

  public void setTestClassPath(String s) {
    testClassTextField.setText(s);
  }

  public void setTestArgs(String s) {
    testOptionsTextField.setText(s);
  }

  public void setJavaOptions(String s) {
    VMParamsTextField.setText(s);
  }

  public JPanel getPanel() {
    return myPanel;
  }

  private void addFileChooser(final String title,
                              final TextFieldWithBrowseButton textField,
                              final Project project) {
     ClassBrowser browser = new ClassBrowser(project, title) {
       protected TreeClassChooser.ClassFilterWithScope getFilter() throws NoFilterException {
         return null;
       }

       protected PsiClass findClass(String className) {
         return JavaPsiFacade.getInstance(project).findClass(className);
       }
     };

    browser.setField(textField);
  }
}
