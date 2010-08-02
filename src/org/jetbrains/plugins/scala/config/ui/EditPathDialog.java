package org.jetbrains.plugins.scala.config.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;

import javax.swing.*;
import java.awt.*;

/**
* Pavel.Fatin, 02.08.2010
*/
class EditPathDialog extends DialogWrapper {
  private TextFieldWithBrowseButton myField = new TextFieldWithBrowseButton();

  EditPathDialog(Project project, FileChooserDescriptor descriptor) {
    super(project);
    setTitle("Edit path");
    myField.addBrowseFolderListener("Plugin jar", null, project, descriptor);
    myField.setMinimumSize(new Dimension(250, myField.getMinimumSize().height));
    init();
  }
  
  String getPath() {
    return myField.getText();
  }
  
  void setPath(String path) {
    myField.setText(path);
  }

  @Override
  protected JComponent createCenterPanel() {
    return myField; 
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myField.getTextField();
  }
}
