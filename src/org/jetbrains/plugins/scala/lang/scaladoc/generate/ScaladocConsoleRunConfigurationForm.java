package org.jetbrains.plugins.scala.lang.scaladoc.generate;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;

/**
 * User: DarthGamer
 * Date: 01.10.11
 */
public class ScaladocConsoleRunConfigurationForm {
  private JPanel myPanel1;
  private JTextField additionalFlagsField;
  private JLabel dirLabel;
  private TextFieldWithBrowseButton destDirChooser;
  private JCheckBox myOpenInBrowserCheckBox;
  private JCheckBox myVerboseCheckBox;
  private JTextField docTitle;
  private JTextField maxHeapSizeField;
  private Project project;

  public ScaladocConsoleRunConfigurationForm(Project project) {
    this.project = project;
    addFileChooser("Output dir", destDirChooser, project);

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

  public void saveSettings() {
    ScaladocSettings settings = ScaladocSettings.getInstance(project);
    settings.outputDir = getOutputDir();
    settings.additionalFlags = getAdditionalFlags();
    settings.openInBrowser = isShowInBrowser();
    settings.verbose = isVerbose();
    settings.docTitle = getDocTitle();
    settings.maxHeapSize = getMaxHeapSize();
  }

  public String getOutputDir() {
    return destDirChooser.getText();
  }
  
  public String getAdditionalFlags() {
    return additionalFlagsField.getText();
  }

  public boolean isShowInBrowser() {
    return myOpenInBrowserCheckBox.isSelected();
  }
  
  public boolean isVerbose() {
    return myVerboseCheckBox.isSelected();
  }

  public String getDocTitle() {
    return docTitle.getText();
  }

  public String getMaxHeapSize() {
    return maxHeapSizeField.getText();
  }

  public JComponent createCenterPanel() {
    return myPanel1;
  }

  public JTextField getOutputDirChooser() {
    return destDirChooser.getTextField();
  }

  private void addFileChooser(final String title,
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
  }
}
