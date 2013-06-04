package org.jetbrains.plugins.scala.config.scalaProjectTemplate.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.plugins.scala.config.Libraries;
import org.jetbrains.plugins.scala.config.LibraryId;
import org.jetbrains.plugins.scala.config.LibraryLevel;
import org.jetbrains.plugins.scala.config.scalaProjectTemplate.scalaDownloader.ScalaDownloader;
import org.jetbrains.plugins.scala.config.ui.ScalaSupportUiBase;
import scala.Tuple2;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * User: Dmitry Naydanov
 * Date: 11/10/12
 */
public class ScalaModuleSettingsUi {
  private JRadioButton existentLibraryRadioButton;
  private JRadioButton setScalaHomeRadioButton;
  private JRadioButton configLaterRadioButton;
  private JComboBox existentLibraryComboBox;
  private JTextField compilerNameField;
  private JTextField libraryNameField;
  private TextFieldWithBrowseButton scalaHome;
  private JLabel scalaHomeComment;
  private JPanel mainPanel;
  private JComboBox existentCompilerComboBox;
  private JButton downloadScalaButton;
  private JCheckBox makeGlobalLibrariesCheckBox;
  private ScalaSupportUiBase uiUtil;
  private Project myProject;

  private ButtonGroup configTypeGroup;
  
  public ScalaModuleSettingsUi(Project project) {
    myProject = project;
    uiUtil = new ScalaSupportUiBase(project, scalaHome, scalaHomeComment, compilerNameField, libraryNameField,
        makeGlobalLibrariesCheckBox, new JRadioButton[] {existentLibraryRadioButton, setScalaHomeRadioButton, configLaterRadioButton}) {
      @Override
      protected void updateSectionsState() {
        setEnabled(setScalaHomeRadioButton.isSelected(), libraryNameField, makeGlobalLibrariesCheckBox, compilerNameField, 
            scalaHome, scalaHomeComment, downloadScalaButton);
        setEnabled(existentLibraryRadioButton.isSelected(), existentLibraryComboBox, existentCompilerComboBox);
      }
    };

    configTypeGroup = new ButtonGroup();
    configTypeGroup.add(existentLibraryRadioButton);
    configTypeGroup.add(setScalaHomeRadioButton);
    configTypeGroup.add(configLaterRadioButton);

    Tuple2<Library[], Library[]> filteredLibraries = Libraries.filterScalaLikeLibraries( getExistentLibraries());
    
    existentCompilerComboBox.setModel(new DefaultComboBoxModel(filteredLibraries._1()));
    existentLibraryComboBox.setModel(new DefaultComboBoxModel(filteredLibraries._2()));
    existentCompilerComboBox.setRenderer(new ExistentLibraryRenderer("compiler"));
    existentLibraryComboBox.setRenderer(new ExistentLibraryRenderer("library"));

    if (filteredLibraries._1().length > 0 && filteredLibraries._2().length > 0) 
      existentLibraryRadioButton.setSelected(true); else setScalaHomeRadioButton.setSelected(true);
    
    downloadScalaButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        String path = ScalaDownloader.download(myProject, getMainPanel());
        if (path != null) {
          scalaHome.setText(path);
        }
      }
    });
    
    uiUtil.initControls();
    setHomeDefault();
  }
  
  private void setHomeDefault() {
    if (scalaHome.getText() != null && scalaHome.getText().trim().length() > 0) return;
    uiUtil.guessHome();
  }
  
  private Library[] getExistentLibraries() {
    return LibraryTablesRegistrar.getInstance().getLibraryTable().getLibraries();
  }
  
  private LibraryId extractLibraryId(JComboBox existentComboBox, JTextField nameField) {
    if (existentLibraryRadioButton.isSelected()) {
      return new LibraryId(((Library) existentComboBox.getSelectedItem()).getName(), LibraryLevel.Global);
    } else if (setScalaHomeRadioButton.isSelected()) {
      return new LibraryId(nameField.getText().trim(), makeGlobalLibrariesCheckBox.isSelected()? LibraryLevel.Global : LibraryLevel.Project);
    } else {
      return null;
    }
  }
  
  public JComponent getMainPanel() {
    return mainPanel;
  }
  
  public String getScalaHome() {
    return setScalaHomeRadioButton.isSelected()? scalaHome.getText() : null;
  }

  public LibraryId getCompilerLibraryId() {
    return extractLibraryId(existentCompilerComboBox, compilerNameField);
  }

  public LibraryId getStandardLibraryId() {
    return extractLibraryId(existentLibraryComboBox, libraryNameField);
  }
  
  private class ExistentLibraryRenderer extends ColoredListCellRenderer {
    private final String libraryNameBase;

    private ExistentLibraryRenderer(String libraryNameBase) {
      this.libraryNameBase = libraryNameBase;
    }

    @Override
    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      if (value == null) return;
      Library library = (Library) value;

      append(library.getName() + "  ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
      append(
          Libraries.extractLibraryVersion(library, "scala-" + libraryNameBase + ".jar", libraryNameBase + ".properties"), 
          SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }
}
