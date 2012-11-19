package org.jetbrains.plugins.scala.config.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.jetbrains.plugins.scala.config.LibraryId;
import org.jetbrains.plugins.scala.config.LibraryLevel;

import javax.swing.*;

public class ScalaSupportWizard {
  private JPanel contentPane;
  private JRadioButton choiceNew;
  private JRadioButton choiceManual;
  private TextFieldWithBrowseButton fieldHome;
  private JTextField fieldCompilerName;
  private JLabel labelState;
  private JTextField fieldLibraryName;
  private JLabel labelCreateCompilerLibrary;
  private JLabel labelCreateStandardLibrary;
  private JCheckBox makeGlobalLibrariesCheckBox;

  private ScalaSupportUiBase uiUtil;
  
  public ScalaSupportWizard(Project project) {
    uiUtil = new ScalaSupportUiBase(project, fieldHome, labelState, fieldCompilerName,
        fieldLibraryName, makeGlobalLibrariesCheckBox, new JRadioButton[] {choiceNew, choiceManual}) {
      @Override
      protected void updateSectionsState() {
        uiUtil.setEnabled(choiceNew.isSelected(),
            fieldHome, labelState, labelCreateCompilerLibrary, fieldCompilerName, makeGlobalLibrariesCheckBox, 
            labelCreateStandardLibrary, fieldLibraryName);
      }
    };
    
    uiUtil.initControls();
  }

  public void init() {
    uiUtil.guessHome();
    uiUtil.updateSectionsState();
  }

  public Choice getChoice() {
    if(choiceNew.isSelected()) return Choice.AddNew;
    if(choiceManual.isSelected()) return Choice.DoNothing;
    throw new RuntimeException("Unknown selected section");
  }

  public String getHome() {
    return fieldHome.getText().trim();
  }

  public LibraryId getCompilerLibraryId() {
    return new LibraryId(fieldCompilerName.getText().trim(), getLibrariesLevel());
  }
  
  public LibraryId getStandardLibraryId() {
    return new LibraryId(fieldLibraryName.getText().trim(), getLibrariesLevel());
  } 

  public JComponent getComponent() {
    return contentPane;
  }
  
  private LibraryLevel getLibrariesLevel() {
    return makeGlobalLibrariesCheckBox.isSelected()? LibraryLevel.Global : LibraryLevel.Project;
  }
}
