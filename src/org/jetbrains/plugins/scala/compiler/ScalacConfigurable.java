package org.jetbrains.plugins.scala.compiler;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.RawCommandLineEditor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.ScalaFileType;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashSet;
import java.util.Arrays;

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.09.2008
 */
public class ScalacConfigurable implements Configurable {
  private JPanel myPanel;
  private JCheckBox scalacBeforeCheckBox;
  private JCheckBox useFscFastScalacCheckBox;
  private JTextField serverPortTextField;
  private JCheckBox resetFscServerCheckBox;
  private JCheckBox shutdownFscServerCheckBox;
  private JLabel serverPortLabel;
  private ScalacSettings mySettings;
  private Project myProject;

  public ScalacConfigurable(ScalacSettings settings, Project project) {
    myProject = project;
    mySettings = settings;
    useFscFastScalacCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        boolean enabled = useFscFastScalacCheckBox.isSelected();
        serverPortTextField.setEnabled(enabled);
        resetFscServerCheckBox.setEnabled(enabled);
        shutdownFscServerCheckBox.setEnabled(enabled);
        serverPortLabel.setEnabled(enabled);
      }
    });
  }

  @Nls
  public String getDisplayName() {
    return "Scala Compiler";
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    if (mySettings.SCALAC_BEFORE != scalacBeforeCheckBox.isSelected()) return true;
    if (mySettings.SERVER_RESET != resetFscServerCheckBox.isSelected()) return true;
    if (mySettings.SERVER_SHUTDOWN != shutdownFscServerCheckBox.isSelected()) return true;
    if (mySettings.USE_FSC != useFscFastScalacCheckBox.isSelected()) return true;
    if (!mySettings.SERVER_PORT.equals(serverPortTextField.getText())) return true;

    return false;
  }

  public void apply() throws ConfigurationException {
    mySettings.USE_FSC = useFscFastScalacCheckBox.isSelected();
    mySettings.SERVER_PORT = serverPortTextField.getText();
    mySettings.SERVER_RESET = resetFscServerCheckBox.isSelected();
    mySettings.SERVER_SHUTDOWN = shutdownFscServerCheckBox.isSelected();
    if (scalacBeforeCheckBox.isSelected() && mySettings.SCALAC_BEFORE != scalacBeforeCheckBox.isSelected()) {
      for (ScalaCompiler compiler: CompilerManager.getInstance(myProject).getCompilers(ScalaCompiler.class)) {
        CompilerManager.getInstance(myProject).removeCompiler(compiler);
      }
      HashSet<FileType> inputSet = new HashSet<FileType>(Arrays.asList(ScalaFileType.SCALA_FILE_TYPE, StdFileTypes.JAVA));
      HashSet<FileType> outputSet = new HashSet<FileType>(Arrays.asList(StdFileTypes.JAVA, StdFileTypes.CLASS));
      CompilerManager.getInstance(myProject).addTranslatingCompiler(new ScalaCompiler(myProject), inputSet, outputSet);
    } else if (!scalacBeforeCheckBox.isSelected() && mySettings.SCALAC_BEFORE != scalacBeforeCheckBox.isSelected()){
      for (ScalaCompiler compiler: CompilerManager.getInstance(myProject).getCompilers(ScalaCompiler.class)) {
        CompilerManager.getInstance(myProject).removeCompiler(compiler);
      }
      CompilerManager.getInstance(myProject).addCompiler(new ScalaCompiler(myProject));
    }
    mySettings.SCALAC_BEFORE = scalacBeforeCheckBox.isSelected();
  }

  public void reset() {
    scalacBeforeCheckBox.setSelected(mySettings.SCALAC_BEFORE);
    shutdownFscServerCheckBox.setSelected(mySettings.SERVER_SHUTDOWN);
    resetFscServerCheckBox.setSelected(mySettings.SERVER_RESET);
    serverPortTextField.setText(mySettings.SERVER_PORT);
    useFscFastScalacCheckBox.setSelected(mySettings.USE_FSC);
  }

  public void disposeUIResources() {
  }
}
