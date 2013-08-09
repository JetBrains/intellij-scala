package org.jetbrains.plugins.scala.config.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.plugins.scala.config.Libraries;
import org.jetbrains.plugins.scala.config.LibraryLevel;
import org.jetbrains.plugins.scala.config.ScalaDistribution;
import org.jetbrains.plugins.scala.icons.Icons;
import scala.Option;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

/**
 * User: Dmitry Naydanov
 * Date: 11/10/12
 */
public class ScalaSupportUiBase {
  private static final Color DEFAULT_COLOR = UIManager.getColor("TextField.foreground");

  private TextFieldWithBrowseButton fieldHome;
  private JLabel scalaHomeComment;
  private Project myProject;
  private JTextField fieldCompilerName;
  private JTextField fieldLibraryName;
  private JCheckBox makeLibrariesGlobalCheckBox;
  private JRadioButton[] choiceButtons;

  public ScalaSupportUiBase(Project myProject, TextFieldWithBrowseButton fieldHome, JLabel scalaHomeComment,
                            JTextField fieldCompilerName, JTextField fieldLibraryName, 
                            JCheckBox makeLibrariesGlobalCheckBox, JRadioButton[] choiceButtons) {
    this.fieldHome = fieldHome;
    this.scalaHomeComment = scalaHomeComment;
    this.myProject = myProject;
    this.fieldCompilerName = fieldCompilerName;
    this.fieldLibraryName = fieldLibraryName;
    this.makeLibrariesGlobalCheckBox = makeLibrariesGlobalCheckBox;
    this.choiceButtons = choiceButtons;
  }
  
  public void initControls() {

    final ButtonsListener buttonsListener = new ButtonsListener();
    for (JRadioButton button : choiceButtons) {
      button.addActionListener(buttonsListener);
    }

    fieldHome.getTextField().getDocument().addDocumentListener(new HomeListener());
    fieldHome.addBrowseFolderListener("Scala home", null, myProject,
        new FileChooserDescriptor(false, true, false, false, false, false));

    FieldUpdater updateListener = new FieldUpdater();
    fieldCompilerName.getDocument().addDocumentListener(updateListener);
    fieldLibraryName.getDocument().addDocumentListener(updateListener);
    makeLibrariesGlobalCheckBox.addActionListener(updateListener);
    
    updateSectionsState();
  }

  public void guessHome() {
    Option<String> home = ScalaDistribution.findHome();

    if(home.isDefined()) {
      fieldHome.setText(home.get());
    }
  }
  
  protected void updateSectionsState() { }
  
  protected LibraryLevel getSelectedLibrariesLevel() {
    return makeLibrariesGlobalCheckBox.isSelected()? LibraryLevel.Global : LibraryLevel.Project;
  }
  
  public void updateHomeState() {
    updateHomeState(scalaHomeComment);
  }

  public void updateHomeState(JLabel stateLabel) {
    fieldHome.getTextField().setForeground(DEFAULT_COLOR);
    fieldHome.getTextField().setToolTipText(null);

    stateLabel.setIcon(null);
    stateLabel.setText("");

    String path = fieldHome.getText();
    if(path.length() == 0) {
      stateLabel.setText("Please, provide a path to Scala SDK");
      return;
    }

    File home = new File(path);
    if(!home.exists()) {
      fieldHome.getTextField().setForeground(Color.RED);
      fieldHome.getTextField().setToolTipText("Path not found");
      return;
    }

    stateLabel.setIcon(Icons.ERROR);

    ScalaDistribution distribution = ScalaDistribution.from(home);

    if(!distribution.valid()) {
      stateLabel.setText("(not valid Scala home)");
      return;
    }

    if(distribution.libraryPath().length() == 0) {
      stateLabel.setText("(compiler missing)");
      return;
    }

    if(distribution.compilerVersion().length() == 0) {
      stateLabel.setText("(invalid compiler file)");
      return;
    }

    if(distribution.libraryVersion().length() == 0) {
      stateLabel.setText("(invalid library file)");
      return;
    }

    if(!distribution.compilerVersion().equals(distribution.libraryVersion())) {
      stateLabel.setText("(mismatch of file versions)");
      return;
    }

    String missing = distribution.missing();
    if(missing.length() > 0) {
      stateLabel.setText("<html><body>Missing files:<br>" + missing + "</html></body>");
      return;
    }

    String version = distribution.version();

    boolean versionOkay = version.startsWith("2.8") || version.startsWith("2.9") || version.startsWith("2.10") || version.startsWith("2.11");
    if(!versionOkay) {
      stateLabel.setText("(version " + version + ", 2.8-2.11 required)");
      stateLabel.setIcon(Icons.WARNING);
      return;
    }

    if(!distribution.hasDocs()) {
      stateLabel.setText("(version " + version + ", no /doc/scala-devel-docs/api)");
      stateLabel.setIcon(Icons.WARNING);
    } else {
      stateLabel.setText("(version " + version + ")");
      stateLabel.setIcon(null);
    }

    LibraryLevel selectedLevel = getSelectedLibrariesLevel();
    fieldCompilerName.setText(Libraries.uniqueName("scala-compiler", selectedLevel, myProject));
    fieldLibraryName.setText(Libraries.uniqueName("scala-library", selectedLevel, myProject));
  }


  public void setEnabled(boolean enabled, JComponent... components) {
    for (JComponent component : components) {
      component.setEnabled(enabled);
    }
  }

  protected class ButtonsListener implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      updateSectionsState();
    }
  }

  protected class HomeListener extends DocumentAdapter {
    @Override
    protected void textChanged(DocumentEvent e) {
      updateHomeState();
    }
  }
  
  protected class FieldUpdater extends DocumentAdapter implements ActionListener {
    @Override
    protected void textChanged(DocumentEvent e) {
      updateFiledState();
    }

    public void actionPerformed(ActionEvent e) {
      if (hasFieldNameClashes(fieldCompilerName)) 
        fieldCompilerName.setText(Libraries.uniqueName(fieldCompilerName.getText().trim(), getLevel(), myProject));
      if (hasFieldNameClashes(fieldLibraryName))
        fieldLibraryName.setText(Libraries.uniqueName(fieldLibraryName.getText().trim(), getLevel(), myProject));

      updateFiledState();
    }

    protected void updateFiledState() {
      showNameClashError(fieldCompilerName, hasFieldNameClashes(fieldCompilerName));
      showNameClashError(fieldLibraryName, hasFieldNameClashes(fieldLibraryName));
    }
    
    private boolean hasFieldNameClashes(JTextField myNameField) {
      return Libraries.nameClashes(myNameField.getText().trim(), getLevel(), myProject);
    }
    
    private void showNameClashError(JTextField myNameField, boolean hasClashes) {
      myNameField.setForeground(hasClashes ? Color.RED : DEFAULT_COLOR);
      myNameField.setToolTipText(hasClashes ? "Name is already in use" : null);
    }

    protected LibraryLevel getLevel() {
      return ScalaSupportUiBase.this.getSelectedLibrariesLevel();
    }
  }
}
