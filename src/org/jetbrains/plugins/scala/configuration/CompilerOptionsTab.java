package org.jetbrains.plugins.scala.configuration;

import com.intellij.ui.RawCommandLineEditor;

import javax.swing.*;

/**
 * @author Pavel Fatin
 */
public class CompilerOptionsTab {
  private RawCommandLineEditor myCompilerOptions;
  private JComboBox myDebuggingInfoLevel;
  private JCheckBox myEnableWarnings;
  private JCheckBox myDeprecationWarnings;
  private JCheckBox myUncheckedWarnings;
  private JCheckBox myOptimiseBytecode;
  private JCheckBox myExplainTypeErrors;
  private JCheckBox myEnableContinuations;
  private JPanel myRootPanel;

  public JPanel getComponent() {
    return myRootPanel;
  }
}
