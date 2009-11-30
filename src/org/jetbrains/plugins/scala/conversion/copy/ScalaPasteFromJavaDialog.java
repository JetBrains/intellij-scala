package org.jetbrains.plugins.scala.conversion.copy;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings;

import javax.swing.*;
import java.awt.*;

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.11.2009
 */
public class ScalaPasteFromJavaDialog extends DialogWrapper {
  private JPanel myPanel;
  private JCheckBox donTShowThisCheckBox;
  private JButton buttonOK;
  private Project myProject;

  public ScalaPasteFromJavaDialog(Project project) {
    super(project, true);
    myProject = project;
    setModal(true);
    getRootPane().setDefaultButton(buttonOK);
    setTitle("Convert code from Java");
    init();
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public Container getContentPane() {
    return myPanel;
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction()};
  }

  protected void doOKAction() {
    if (donTShowThisCheckBox.isSelected()) {
      CodeStyleSettingsManager.getSettings(myProject).getCustomSettings(ScalaCodeStyleSettings.class).DONT_SHOW_CONVERSION_DIALOG = true;
    }
    super.doOKAction();
  }
}
