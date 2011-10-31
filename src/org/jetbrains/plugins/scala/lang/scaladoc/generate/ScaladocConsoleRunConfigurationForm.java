package org.jetbrains.plugins.scala.lang.scaladoc.generate;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;

/**
 * User: DarthGamer
 * Date: 01.10.11
 */
public class ScaladocConsoleRunConfigurationForm extends DialogWrapper {
  private JPanel myPanel1;
  private JTextField additionalFlagsField;
  private JLabel dirLabel;
  private TextFieldWithBrowseButton destDirChooser;
  private JCheckBox myOpenInBrowserCheckBox;
  private JCheckBox myVerboseCheckBox;
  private JTextField docTitle;
  private JTextField maxHeapSizeField;
  private Project project;


  @Override
  protected void doOKAction() {
    super.doOKAction();
  }

  @Override
  public void doCancelAction() {
    super.doCancelAction();
  }

  public ScaladocConsoleRunConfigurationForm(Project project) {
    super(project);
    this.project = project;
    addFileChooser("Output dir", destDirChooser, project);
    init();

    ScaladocSettings settings = ScaladocSettings.getInstance(project);
    if (settings.docTitle != null) {
      docTitle.setText(settings.docTitle);
    } else {
      docTitle.setText(project.getName() +  " documentation");
    }

    if (settings.additionalFlags != null) {
      additionalFlagsField.setText(settings.additionalFlags);
    }

    if (settings.maxHeapSize != null) {
      maxHeapSizeField.setText(settings.maxHeapSize);
    }

    if (settings.outputDir != null) {
      destDirChooser.setText(settings.outputDir);
    }

    if (settings.verbose != null) {
      myVerboseCheckBox.setSelected(settings.verbose);
    }

    if (settings.openInBrowser != null) {
      myOpenInBrowserCheckBox.setSelected(settings.openInBrowser);
    }
  }

  //todo: remove side effects from getters

  public String getOutputDir() {
    ScaladocSettings.getInstance(project).outputDir = destDirChooser.getText();
    return destDirChooser.getText();
  }
  
  public String getAdditionalFlags() {
    ScaladocSettings.getInstance(project).additionalFlags = additionalFlagsField.getText();
    return additionalFlagsField.getText();
  }

  public boolean isShowInBrowser() {
    ScaladocSettings.getInstance(project).openInBrowser = myOpenInBrowserCheckBox.isSelected();
    return myOpenInBrowserCheckBox.isSelected();
  }
  
  public boolean isVerbose() {
    ScaladocSettings.getInstance(project).verbose = myVerboseCheckBox.isSelected();
    return myVerboseCheckBox.isSelected();
  }

  public String getDocTitle() {
    ScaladocSettings.getInstance(project).docTitle = docTitle.getText();
    return docTitle.getText();
  }

  public String getMaxHeapSize() {
    ScaladocSettings.getInstance(project).maxHeapSize = maxHeapSizeField.getText();
    return maxHeapSizeField.getText();
  }

  @Override
  public JComponent createCenterPanel() {
    return myPanel1;
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
