package org.jetbrains.plugins.scala.config.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
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
    
    comboCompilerLevel.setModel(new DefaultComboBoxModel(
        new LibraryLevel[] {LibraryLevel.Global, LibraryLevel.Project}));
    comboCompilerLevel.setRenderer(new LevelRenderer());
    comboCompilerLevel.addActionListener(compilerUpdater);
    
    comboLibraryLevel.setModel(new DefaultComboBoxModel(LibraryLevel.values()));
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

  public LibraryId getCompilerLibraryId() {
    return new LibraryId(fieldCompilerName.getText().trim(), (LibraryLevel) comboCompilerLevel.getSelectedItem());
  }
  
  public LibraryId getStandardLibraryId() {
    return new LibraryId(fieldLibraryName.getText().trim(), (LibraryLevel) comboLibraryLevel.getSelectedItem());
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

    if(!distribution.valid()) {
      labelState.setText("(not valid Scala home)");
      return;
    }
    
    if(distribution.libraryPath().length() == 0) {
      labelState.setText("(compiler missing)");
      return;
    }
    
    if(distribution.compilerVersion().length() == 0) {
      labelState.setText("(invalid compiler file)");
      return;
    }
    
    if(distribution.libraryVersion().length() == 0) {
      labelState.setText("(invalid library file)");
      return;
    }
    
    if(!distribution.compilerVersion().equals(distribution.libraryVersion())) {
      labelState.setText("(mismatch of file versions)");
      return;
    }
    
    String missing = distribution.missing();
    if(missing.length() > 0) {
      labelState.setText("<html><body>Missing files:<br>" + missing + "</html></body>");
      return;
    }
    
    String version = distribution.version();

    boolean versionOkay = version.startsWith("2.8") || version.startsWith("2.9");
    if(!versionOkay) {
      labelState.setText("(version " + version + ", 2.8.x or 2.9.x required)");
      labelState.setIcon(Icons.WARNING);
      return;
    }
    
    if(!distribution.hasDocs()) {
      labelState.setText("(version " + version + ", no /docs/scala-devel-docs/api)");
      labelState.setIcon(Icons.WARNING);
    } else {    
      labelState.setText("(version " + version + ")");
      labelState.setIcon(null);
    }

    fieldCompilerName.setText(Libraries.uniqueName("scala-compiler-" + distribution.compilerVersion(), 
        getCompilerLibraryId().level(), myProject));

    fieldLibraryName.setText(Libraries.uniqueName("scala-library-" + distribution.libraryVersion(),
        getStandardLibraryId().level(), myProject));
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
        myNameField.setText(Libraries.uniqueName(getName(), getLevel(), myProject));
      }
      updateFiledState();
    }
    
    private void updateFiledState() {
      boolean clashes = nameClashes();
      myNameField.setForeground(clashes ? Color.RED : DEFAULT_COLOR);
      myNameField.setToolTipText(clashes ? "Name is already in use" : null);
    }

    private boolean nameClashes() {
      return Libraries.nameClashes(getName(), getLevel(), myProject);
    }

    private String getName() {
      return myNameField.getText().trim();
    }

    private LibraryLevel getLevel() {
      return (LibraryLevel) myLevelField.getSelectedItem();
    }
  }
}
