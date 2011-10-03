package org.jetbrains.plugins.scala.testingSupport.scalaTest;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configuration.BrowseModuleValueActionListener;
import com.intellij.execution.ui.ClassBrowser;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.RawCommandLineEditor;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.02.2009
 */
public class ScalaTestRunConfigurationForm {
  private JPanel myPanel;
  private TextFieldWithBrowseButton testClassTextField;
  private RawCommandLineEditor VMParamsTextField;
  private RawCommandLineEditor testOptionsTextField;
  private TextFieldWithBrowseButton testPackageTextField;
  private JRadioButton testClassRadioButton;
  private JRadioButton testPackageRadioButton;
  private JLabel testClassLabel;
  private JLabel testPackageLabel;
  private JComboBox moduleComboBox;
  private TextFieldWithBrowseButton workingDirectoryField;

  private ConfigurationModuleSelector myModuleSelector;

  public ScalaTestRunConfigurationForm(final Project project, final ScalaTestRunConfiguration configuration) {
    myModuleSelector = new ConfigurationModuleSelector(project, moduleComboBox);
    myModuleSelector.reset(configuration);
    moduleComboBox.setEnabled(true);
    addClassChooser("Choose test class", testClassTextField, project);
    addFileChooser("Choose Working Directory", workingDirectoryField, project);
    VirtualFile baseDir = project.getBaseDir();
    String path = baseDir != null ? baseDir.getPath() : "";
    workingDirectoryField.setText(path);
    addPackageChooser(testPackageTextField, project);
    VMParamsTextField.setDialogCaption("VM parameters editor");
    testOptionsTextField.setDialogCaption("Additional options editor");
    if (configuration.getTestClassPath().equals("") && !configuration.getTestPackagePath().equals("")) {
      setClassEnabled();
    } else {
      setPackageEnabled();
    }
    testClassRadioButton.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        if (testClassRadioButton.isSelected()) {
          setClassEnabled();
        } else {
          setPackageEnabled();
        }
      }
    });
    testPackageRadioButton.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        if (testPackageRadioButton.isSelected()) {
          testClassRadioButton.setSelected(false);
        }
        else {
          testClassRadioButton.setSelected(true);
        }
      }
    });
  }

  private void setPackageEnabled() {
    testPackageLabel.setVisible(true);
    testPackageTextField.setVisible(true);
    testClassLabel.setVisible(false);
    testClassTextField.setVisible(false);
    testPackageRadioButton.setSelected(true);
    testClassRadioButton.setSelected(false);
  }

  private void setClassEnabled() {
    testPackageLabel.setVisible(false);
    testPackageTextField.setVisible(false);
    testClassLabel.setVisible(true);
    testClassTextField.setVisible(true);
    testPackageRadioButton.setSelected(false);
    testClassRadioButton.setSelected(true);
  }

  public void apply(ScalaTestRunConfiguration configuration) {
    setTestClassPath(configuration.getTestClassPath());
    setJavaOptions(configuration.getJavaOptions());
    setTestArgs(configuration.getTestArgs());
    setTestPackagePath(configuration.getTestPackagePath());
    if (getTestClassPath().equals("") && !getTestPackagePath().equals("")) {
      setPackageEnabled();
    }
    else {
      setClassEnabled();
    }
    setWorkingDirectory(configuration.getWorkingDirectory());
    myModuleSelector.applyTo(configuration);
  }

  public boolean isClassSelected() {
    return testClassRadioButton.isSelected();
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

  public String getTestPackagePath() {
    return testPackageTextField.getText();
  }

  public String getWorkingDirectory() {
    return workingDirectoryField.getText();
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

  public void setTestPackagePath(String s) {
    testPackageTextField.setText(s);
  }

  public void setWorkingDirectory(String s) {
    workingDirectoryField.setText(s);
  }

  public JPanel getPanel() {
    return myPanel;
  }

  private void addClassChooser(final String title,
                              final TextFieldWithBrowseButton textField,
                              final Project project) {
     ClassBrowser browser = new ClassBrowser(project, title) {
       protected ClassFilter.ClassFilterWithScope getFilter() throws ClassBrowser.NoFilterException {
         return new ClassFilter.ClassFilterWithScope() {
           public GlobalSearchScope getScope() {
             return GlobalSearchScope.allScope(project);
           }

           public boolean isAccepted(PsiClass aClass) {
             return true;
           }
         };
       }

       protected PsiClass findClass(String className) {
         return JavaPsiFacade.getInstance(project).findClass(className);
       }
     };

    browser.setField(textField);
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

  private void addPackageChooser(final TextFieldWithBrowseButton textField, final Project project) {
    PackageChooserActionListener browser = new PackageChooserActionListener(project);
    browser.setField(textField);
  }

  //todo: copied from JUnitConfigurable
  private static class PackageChooserActionListener extends BrowseModuleValueActionListener {
    public PackageChooserActionListener(final Project project) {super(project);}

    protected String showDialog() {
      final PackageChooserDialog dialog = new PackageChooserDialog(ExecutionBundle.message("choose.package.dialog.title"), getProject());
      dialog.show();
      final PsiPackage aPackage = dialog.getSelectedPackage();
      return aPackage != null ? aPackage.getQualifiedName() : null;
    }
  }

  public Module getModule() {
    return myModuleSelector.getModule();
  }
}
