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
import java.util.HashSet;
import java.util.Arrays;

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.09.2008
 */
public class ScalacConfigurable implements Configurable {
  private RawCommandLineEditor additionalCommandLineParameters;
  private JPanel myPanel;
  private JTextField maximumHeapSizeTextField;
  private JCheckBox deprecationCheckBox;
  private JCheckBox uncheckedCheckBox;
  private JCheckBox noWarningsCheckBox;
  private JCheckBox optimizeCheckBox;
  private JCheckBox scalacBeforeCheckBox;
  private JCheckBox myNoGenerics;
  private ScalacSettings mySettings;
  private Project myProject;

  public ScalacConfigurable(ScalacSettings settings, Project project) {
    myProject = project;
    mySettings = settings;
    additionalCommandLineParameters.setDialogCaption(ScalaBundle.message("scala.compiler.option.additional.command.line.parameters"));
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
    try {
    if (Integer.parseInt(maximumHeapSizeTextField.getText()) != mySettings.MAXIMUM_HEAP_SIZE) return true;
    } catch (NumberFormatException ignored) {}
    if (!additionalCommandLineParameters.getText().equals(mySettings.ADDITIONAL_OPTIONS_STRING)) return true;
    if (mySettings.DEPRECATION != deprecationCheckBox.isSelected()) return true;
    if (mySettings.UNCHECKED != uncheckedCheckBox.isSelected()) return true;
    if (mySettings.NO_GENERICS != myNoGenerics.isSelected()) return true;
    if (mySettings.GENERATE_NO_WARNINGS != noWarningsCheckBox.isSelected()) return true;
    if (mySettings.OPTIMISE != optimizeCheckBox.isSelected()) return true;
    if (mySettings.SCALAC_BEFORE != scalacBeforeCheckBox.isSelected()) return true;
    return false;
  }

  public void apply() throws ConfigurationException {
    try {
      int maxHeapSize = Integer.parseInt(maximumHeapSizeTextField.getText());
      if (maxHeapSize < 1) mySettings.MAXIMUM_HEAP_SIZE = 128;
      else mySettings.MAXIMUM_HEAP_SIZE = maxHeapSize;
    } catch (NumberFormatException e) {
      mySettings.MAXIMUM_HEAP_SIZE = 128;
    }
    mySettings.ADDITIONAL_OPTIONS_STRING = additionalCommandLineParameters.getText();
    mySettings.GENERATE_NO_WARNINGS = noWarningsCheckBox.isSelected();
    mySettings.UNCHECKED = uncheckedCheckBox.isSelected();
    mySettings.NO_GENERICS = myNoGenerics.isSelected();
    mySettings.DEPRECATION = deprecationCheckBox.isSelected();
    mySettings.OPTIMISE = optimizeCheckBox.isSelected();
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
    maximumHeapSizeTextField.setText("" + mySettings.MAXIMUM_HEAP_SIZE);
    additionalCommandLineParameters.setText(mySettings.ADDITIONAL_OPTIONS_STRING);
    noWarningsCheckBox.setSelected(mySettings.GENERATE_NO_WARNINGS);
    uncheckedCheckBox.setSelected(mySettings.UNCHECKED);
    myNoGenerics.setSelected(mySettings.NO_GENERICS);
    deprecationCheckBox.setSelected(mySettings.DEPRECATION);
    optimizeCheckBox.setSelected(mySettings.OPTIMISE);
    scalacBeforeCheckBox.setSelected(mySettings.SCALAC_BEFORE);
  }

  public void disposeUIResources() {
  }
}
