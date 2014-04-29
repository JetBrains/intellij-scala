package org.jetbrains.plugins.scala.testingSupport.test;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configuration.BrowseModuleValueActionListener;
import com.intellij.execution.ui.ClassBrowser;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.RawCommandLineEditor;
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil;
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * @author Ksenia.Sautina
 * @since 5/15/12
 */
public class TestRunConfigurationForm{
  private JPanel myPanel;
  private TextFieldWithBrowseButton testClassTextField;
  private RawCommandLineEditor VMParamsTextField;
  private RawCommandLineEditor testOptionsTextField;
  private TextFieldWithBrowseButton testPackageTextField;
  private JLabel testClassLabel;
  private JLabel testPackageLabel;
  private JComboBox moduleComboBox;
  private TextFieldWithBrowseButton workingDirectoryField;
  private JPanel searchForTestsPanel;
  private ConfigurationModuleSelector myModuleSelector;
  private String suitePath;

  private JComboBox searchForTestsComboBox;
  public static enum SearchForTest {
    IN_WHOLE_PROJECT, IN_SINGLE_MODULE, ACCROSS_MODULE_DEPENDENCIES;

    @Override
    public String toString() {
      switch (this) {
        case ACCROSS_MODULE_DEPENDENCIES: return "Across module dependencies";
        case IN_WHOLE_PROJECT: return "In whole project";
        case IN_SINGLE_MODULE: return "In single module";
        default: return "";
      }
    }
  }

  private JComboBox kindComboBox;
  private JTextField testNameTextField;
  private JLabel testNameLabel;
  private JCheckBox myShowProgressMessagesCheckBox;

  public static enum TestKind {
    ALL_IN_PACKAGE, CLASS, TEST_NAME;

    @Override
    public String toString() {
      switch (this) {
        case ALL_IN_PACKAGE: return "All in package";
        case CLASS: return "Class";
        case TEST_NAME: return "Test name";
        default: return "";
      }
    }

    public static TestKind fromString(String s) {
      if (s.equals("All in package")) return ALL_IN_PACKAGE;
      else if (s.equals("Class")) return CLASS;
      else if (s.equals("Test name")) return TEST_NAME;
      else return null;
    }
  }


  public TestRunConfigurationForm(final Project project, final AbstractTestRunConfiguration configuration) {
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

    for (SearchForTest searchForTest : SearchForTest.values()) {
      searchForTestsComboBox.addItem(searchForTest);
    }

    searchForTestsComboBox.setSelectedItem(configuration.getSearchTest());

    searchForTestsComboBox.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        setupModuleComboBox();
      }
    });

    myShowProgressMessagesCheckBox.setSelected(configuration.getShowProgressMessages());

    for (TestKind testKind : TestKind.values()) {
      kindComboBox.addItem(testKind);
    }

    switch (configuration.getTestKind()) {
      case ALL_IN_PACKAGE:
        setPackageEnabled();
        break;
      case CLASS:
        setClassEnabled();
        break;
      case TEST_NAME:
        setTestNameEnabled();
        break;
    }

    kindComboBox.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        moduleComboBox.setEnabled(true);
        switch ((TestKind) e.getItem()) {
          case ALL_IN_PACKAGE:
            setPackageEnabled();
            setupModuleComboBox();
            break;
          case CLASS:
            setClassEnabled();
            break;
          case TEST_NAME:
            setTestNameEnabled();
            break;
        }
      }
    });

    suitePath = configuration.suitePath();
 }

  private void setupModuleComboBox() {
    switch ((SearchForTest) searchForTestsComboBox.getSelectedItem()) {
      case IN_WHOLE_PROJECT:
        moduleComboBox.setEnabled(false);
        break;
      case IN_SINGLE_MODULE:
        moduleComboBox.setEnabled(true);
        break;
      case ACCROSS_MODULE_DEPENDENCIES:
        moduleComboBox.setEnabled(true);
        break;
    }
  }

  private void setPackageVisible(boolean visible) {
    testPackageLabel.setVisible(visible);
    testPackageTextField.setVisible(visible);
    searchForTestsPanel.setVisible(visible);
  }

  private void setClassVisible(boolean visible) {
    testClassLabel.setVisible(visible);
    testClassTextField.setVisible(visible);
  }

  private void setTestNameVisible(boolean visible) {
    testNameLabel.setVisible(visible);
    testNameTextField.setVisible(visible);
    testClassLabel.setVisible(visible);
    testClassTextField.setVisible(visible);
  }

  private void disableAll() {
    setPackageVisible(false);
    setClassVisible(false);
    setTestNameVisible(false);
  }

  private void setPackageEnabled() {
    disableAll();
    setPackageVisible(true);
    kindComboBox.setSelectedItem(TestKind.ALL_IN_PACKAGE);
  }

  private void setClassEnabled() {
    disableAll();
    setClassVisible(true);
    kindComboBox.setSelectedItem(TestKind.CLASS);
  }

  private void setTestNameEnabled() {
    disableAll();
    setTestNameVisible(true);
    kindComboBox.setSelectedItem(TestKind.TEST_NAME);
  }

  public void apply(AbstractTestRunConfiguration configuration) {
    setTestClassPath(configuration.getTestClassPath());
    setJavaOptions(configuration.getJavaOptions());
    setTestArgs(configuration.getTestArgs());
    setTestPackagePath(configuration.getTestPackagePath());
    switch (configuration.getTestKind()) {
      case ALL_IN_PACKAGE:
        setPackageEnabled();
        break;
      case CLASS:
        setClassEnabled();
        break;
      case TEST_NAME:
        setTestNameEnabled();
        break;
    }
    setWorkingDirectory(configuration.getWorkingDirectory());
    myModuleSelector.applyTo(configuration);
    searchForTestsComboBox.setSelectedItem(configuration.getSearchTest());
    setTestName(configuration.getTestName());
    setShowProgressMessages(configuration.getShowProgressMessages());
  }

  public TestKind getSelectedKind() {
    return (TestKind) kindComboBox.getSelectedItem();
  }

  public SearchForTest getSearchForTest() {
    return (SearchForTest) searchForTestsComboBox.getSelectedItem();
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

  public String getTestName() {
    return testNameTextField.getText();
  }

  public void setTestName(String s) {
    testNameTextField.setText(s);
  }

  public boolean getShowProgressMessages() {
    return myShowProgressMessagesCheckBox.isSelected();
  }

  public void setShowProgressMessages(boolean b) {
    myShowProgressMessagesCheckBox.setSelected(b);
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
            Module module = getModule();
            if (module != null) return GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
            return GlobalSearchScope.allScope(project);
          }

          public boolean isAccepted(PsiClass aClass) {
            if (!getScope().accept(aClass.getContainingFile().getVirtualFile())) return false;
            PsiClass[] classes = ScalaPsiManager.instance(project).getCachedClasses(getScope(), suitePath);
            for (PsiClass psiClass : classes) {
              if (ScalaPsiUtil.cachedDeepIsInheritor(aClass, psiClass)) return true;
            }
            return false;
          }
        };
      }

      protected PsiClass findClass(String className) {
        return ScalaPsiManager.instance(project).getCachedClass(GlobalSearchScope.allScope(project), className);
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
