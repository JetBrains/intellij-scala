package org.jetbrains.plugins.scala.config.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.plugins.scala.config.*;
import org.jetbrains.plugins.scala.icons.Icons;
import scala.Option;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.*;
import java.io.File;

public class ScalaSupportWizard {
  private static final Color DEFAULT_COLOR = UIManager.getColor("TextField.foreground");

  private JPanel contentPane;
  private JRadioButton choiceNew;
  private JRadioButton choiceManual;
  private TextFieldWithBrowseButton fieldHome;
  private JTextField fieldCompilerName;
  private JComboBox comboCompilerLevel;
  private JLabel labelState;
  private JLabel labelCompilerName;
  private JLabel labelLibraryName;
  private JTextField fieldLibraryName;
  private JComboBox comboLibraryLevel;
  private JLabel labelCreateCompilerLibrary;
  private JLabel labelCreateStandardLibary;
  private JLabel labelCompilerLevel;
  private JLabel labelLibraryLevel;

  private Project myProject;
  
  private boolean homeIsValid;

  
  public ScalaSupportWizard(Project project) {
    myProject = project;
    
    ButtonsListener buttonsListener = new ButtonsListener();
    choiceNew.addActionListener(buttonsListener);
    choiceManual.addActionListener(buttonsListener);
    
    fieldHome.getTextField().getDocument().addDocumentListener(new HomeListener());    
    fieldHome.addBrowseFolderListener("Scala home", null, myProject, 
        new FileChooserDescriptor(false, true, false, false, false, false));
    
    FieldUpdater compilerUpdater = new FieldUpdater(fieldCompilerName, comboCompilerLevel);
    FieldUpdater libraryUpdater = new FieldUpdater(fieldLibraryName, comboLibraryLevel);

    fieldCompilerName.getDocument().addDocumentListener(compilerUpdater);
    fieldLibraryName.getDocument().addDocumentListener(libraryUpdater);
    
    comboCompilerLevel.setModel(new DefaultComboBoxModel(new LibrariesContainer.LibraryLevel[] {
        LibrariesContainer.LibraryLevel.GLOBAL, LibrariesContainer.LibraryLevel.PROJECT}));
    comboCompilerLevel.setRenderer(new LevelRenderer());
    comboCompilerLevel.addActionListener(compilerUpdater);
    
    comboLibraryLevel.setModel(new DefaultComboBoxModel(LibrariesContainer.LibraryLevel.values()));
    comboLibraryLevel.setRenderer(new LevelRenderer());
    comboLibraryLevel.addActionListener(libraryUpdater);
  }

  public void init() {
    guessHome();
    updateSectionsState();
  }
 
  public Choice getChoice() {
    if(choiceNew.isSelected()) return Choice.AddNew;
    if(choiceManual.isSelected()) return Choice.DoNothing;
    throw new RuntimeException("Unknown selected section");
  }

  public String getHome() {
    return fieldHome.getText().trim();
  }
  
  public LibrariesContainer.LibraryLevel getCompilerLevel() {
    return (LibrariesContainer.LibraryLevel) comboCompilerLevel.getSelectedItem();
  }
  
  public LibrariesContainer.LibraryLevel getLibraryLevel() {
    return (LibrariesContainer.LibraryLevel) comboLibraryLevel.getSelectedItem();
  }

  public String getCompilerName() {
    return fieldCompilerName.getText().trim();
  }
  
  public String getLibraryName() {
    return fieldLibraryName.getText().trim();
  }

  public void updateSectionsState() {
    setEnabled(choiceNew.isSelected(),
        fieldHome, 
        labelState, 
        labelCreateCompilerLibrary, 
        labelCompilerName, fieldCompilerName,
        labelCompilerLevel, comboCompilerLevel,
        labelCreateStandardLibary,
        labelLibraryName, fieldLibraryName,
        labelLibraryLevel, comboLibraryLevel);
  }

  private void setEnabled(boolean enabled, JComponent... components) {
    for (JComponent component : components) {
      component.setEnabled(enabled);
    }
  }

  private void guessHome() {
      Option<String> home = ScalaDistribution.findHome();
      if(home.isDefined()) {
        fieldHome.setText(home.get());
      }
  }

  private void updateHomeState() {
    homeIsValid = false;
    
    fieldHome.getTextField().setForeground(DEFAULT_COLOR);
    fieldHome.getTextField().setToolTipText(null);
    
    labelState.setIcon(null);    
    labelState.setText("");
    
    String path = fieldHome.getText();
    if(path.length() == 0) {
      labelState.setText("Please, provide a path to Scala SDK");
      return;
    }
    
    File home = new File(path);
    if(!home.exists()) {
      fieldHome.getTextField().setForeground(Color.RED);
      fieldHome.getTextField().setToolTipText("Path not found");
      return;
    }

    labelState.setIcon(Icons.ERROR);
    
    ScalaDistribution distribution = new ScalaDistribution(home);

    for(Problem problem : distribution.problems()) {
      if(problem instanceof NotScalaSDK) labelState.setText("(not valid Scala home)");
      if(problem instanceof ComplierMissing) labelState.setText("(compiler missing)");
      if(problem instanceof InvalidArchive) labelState.setText("(invalid archive file)");
      if(problem instanceof InconsistentVersions) labelState.setText("(mismatch of file versions)");
      return;
    }
    
    String missing = distribution.missing();
    if(missing.length() > 0) {
      labelState.setText("<html><body>Missing files:<br>" + missing + "</html></body>");
      return;
    }
    
    String version = distribution.version();
    
    if(!version.startsWith("2.8")) {
      labelState.setText("(version " + version + ", 2.8+ required)");
      labelState.setIcon(Icons.WARNING);
      return;
    }
    
    homeIsValid = true;
    
    if(!distribution.hasDocs()) {
      labelState.setText("(version " + version + ", no /docs/scala-devel-docs/api)");
      labelState.setIcon(Icons.WARNING);
    } else {    
      labelState.setText("(version " + version + ")");
      labelState.setIcon(null);
    }

    fieldCompilerName.setText(
        LibraryEntry.uniqueName("scala-compiler-" + distribution.version(), getCompilerLevel(), myProject));

    fieldLibraryName.setText(
        LibraryEntry.uniqueName("scala-library-" + distribution.version(), getLibraryLevel(), myProject));
  }

  public JComponent getComponent() {
    return contentPane;
  }

  private class ButtonsListener implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      updateSectionsState();
    }

  }
  
  private class HomeListener extends DocumentAdapter {
    @Override
    protected void textChanged(DocumentEvent e) {
      updateHomeState();
    }

  }
  
  private class FieldUpdater extends DocumentAdapter implements ActionListener {
    private JTextField myNameField;
    private JComboBox myLevelField;

    private FieldUpdater(JTextField nameField, JComboBox levelField) {
      myNameField = nameField;
      myLevelField = levelField;
    }

    @Override
    protected void textChanged(DocumentEvent e) {
      updateFiledState();
    }

    public void actionPerformed(ActionEvent e) {
      if(nameClashes()) {
        myNameField.setText(LibraryEntry.uniqueName(getName(), getLevel(), myProject));
      }
      updateFiledState();
    }
    
    private void updateFiledState() {
      boolean clashes = nameClashes();
      myNameField.setForeground(clashes ? Color.RED : DEFAULT_COLOR);
      myNameField.setToolTipText(clashes ? "Name is already in use" : null);
    }

    private boolean nameClashes() {
      return LibraryEntry.nameClashes(getName(), getLevel(), myProject);
    }

    private String getName() {
      return myNameField.getText().trim();
    }

    private LibrariesContainer.LibraryLevel getLevel() {
      return (LibrariesContainer.LibraryLevel) myLevelField.getSelectedItem();
    }
  }
}
