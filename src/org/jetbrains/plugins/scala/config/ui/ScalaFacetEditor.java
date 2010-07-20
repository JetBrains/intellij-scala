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

public class ScalaFacetEditor {
  private static final Color DEFAULT_COLOR = UIManager.getColor("TextField.foreground");

  private JPanel contentPane;
  private JRadioButton buttonExistingSDK;
  private JRadioButton buttonNewSDK;
  private JRadioButton buttonNoSDK;
  private JComboBox comboLibrary;
  private TextFieldWithBrowseButton fieldHome;
  private JTextField fieldName;
  private JComboBox comboLevel;
  private JLabel labelState;
  private JLabel labelHome;
  private JLabel labelLevel;
  private JLabel labelName;
  private JLabel labelVersion;

  private Project myProject;
  
  private boolean homeIsValid;

  
  public ScalaFacetEditor(Project project) {
    myProject = project;
    
    ButtonsListener buttonsListener = new ButtonsListener();
    buttonExistingSDK.addActionListener(buttonsListener);
    buttonNewSDK.addActionListener(buttonsListener);
    buttonNoSDK.addActionListener(buttonsListener);
    
    fieldHome.getTextField().getDocument().addDocumentListener(new HomeListener());    
    fieldHome.addBrowseFolderListener("Scala home", null, myProject, 
        new FileChooserDescriptor(false, true, false, false, false, false));

    fieldName.getDocument().addDocumentListener(new NameListener());

    comboLibrary.setModel(new DefaultComboBoxModel(ScalaLibrary.findAll(myProject)));
    comboLibrary.setRenderer(new LibraryRenderer());
    
    comboLevel.setModel(new DefaultComboBoxModel(LibrariesContainer.LibraryLevel.values()));
    comboLevel.setRenderer(new LevelRenderer());
    comboLevel.addActionListener(new LevelListener());
  }

  public void init() {
    chooseActiveSection();
    updateExistingButtonState();
    guessHome();
    updateSectionsState();
  }
  
  public void updateExistingButtonState() {
    boolean exists = hasExistingLibrary();
    buttonExistingSDK.setEnabled(exists);
    if(!exists) {
      comboLibrary.setSelectedItem(null);
    }
  }

  public Choice getChoice() {
    if(buttonExistingSDK.isSelected()) return Choice.UseExisting;
    if(buttonNewSDK.isSelected()) return Choice.AddNew;
    if(buttonNoSDK.isSelected()) return Choice.DoNothing;
    throw new RuntimeException("Unknown selected section");
  }
  
  private boolean hasExistingLibrary() {
    return getExistingLibrary() != null;
  }

  public ScalaLibrary getExistingLibrary() {
    return ((ScalaLibrary) comboLibrary.getModel().getSelectedItem());
  }

  public String getHome() {
    return fieldHome.getText().trim();
  }
  
  public LibrariesContainer.LibraryLevel getLevel() {
    return (LibrariesContainer.LibraryLevel) comboLevel.getSelectedItem();
  }

  public String getName() {
    return fieldName.getText().trim();
  }
  
  public void chooseActiveSection() {
    if(hasExistingLibrary()) {
      buttonExistingSDK.setSelected(true);
    } else {
      buttonNewSDK.setSelected(true);
    }
  }

  public void updateSectionsState() {
    comboLibrary.setEnabled(buttonExistingSDK.isSelected());
    setNewSectionEnabled(buttonNewSDK.isSelected());
  }

  private void setNewSectionEnabled(boolean b) {
    labelHome.setEnabled(b);
    fieldHome.setEnabled(b);
    
    labelVersion.setEnabled(b);
    labelState.setEnabled(b);
    
    labelLevel.setEnabled(b);
    comboLevel.setEnabled(b);
    
    labelName.setEnabled(b);
    fieldName.setEnabled(b);
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
      if(problem instanceof NotScalaSDK) labelState.setText("Not valid Scala SDK");
      if(problem instanceof ComplierMissing) labelState.setText("Compiler missing in Scala SDK");
      if(problem instanceof InvalidArchive) labelState.setText("Invalid archive file");
      if(problem instanceof InconsistentVersions) labelState.setText("Mismatch of SDK file versions");
      return;
    }
    
    String missing = distribution.missing();
    if(missing.length() > 0) {
      labelState.setText("<html><body>Missing SDK files:<br>" + missing + "</html></body>");
      return;
    }
    
    String version = distribution.version();
    
    if(!version.startsWith("2.8")) {
      labelState.setText(version + " (unsupported, 2.8+ required)");
      labelState.setIcon(Icons.WARNING);
      return;
    }
    
    homeIsValid = true;
    
    if(!distribution.hasDocs()) {
      labelState.setText(version + " (no /docs/api found)");
      labelState.setIcon(Icons.WARNING);
    } else {    
      labelState.setText(version);
      labelState.setIcon(null);
    }
    
    fieldName.setText(ScalaLibrary.uniqueName(distribution.name(), getLevel(), myProject));
  }

  private void makeNameUnique() {
    if(nameClashes()) {
      fieldName.setText(ScalaLibrary.uniqueName(getName(), getLevel(), myProject));
    }
  }

  private void updateNameState() {
    boolean clashes = nameClashes();
    fieldName.setForeground(clashes ? Color.RED : DEFAULT_COLOR);
    fieldName.setToolTipText(clashes ? "Name is already in use" : null);
  }

  private boolean nameClashes() {
    return ScalaLibrary.nameClashes(getName(), getLevel(), myProject);
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
  private class NameListener extends DocumentAdapter {
    @Override
    protected void textChanged(DocumentEvent e) {
      updateNameState();
    }

  }
  private class LevelListener implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      makeNameUnique();
      updateNameState();
    }
  }
}
