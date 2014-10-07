package org.jetbrains.plugins.scala.configuration;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.RawCommandLineEditor;

import javax.swing.*;
import java.awt.*;

/**
 * @author Pavel Fatin
 */
public class ScalaCompilerSettingsForm {
  private JPanel myContentPanel;
  private JPanel myPluginsPanel;
  private RawCommandLineEditor myAdditionalCompilerOptions;
  private JComboBox<DebuggingInfoLevel> myDebuggingInfoLevel;
  private JCheckBox myWarnings;
  private JCheckBox myDeprecationWarnings;
  private JCheckBox myUncheckedWarnings;
  private JCheckBox myOptimiseBytecode;
  private JCheckBox myExplainTypeErrors;
  private JCheckBox myContinuations;
  private JComboBox<CompileOrder> myCompileOrder;
  private JComboBox<IncrementalityType> myIncrementalityType;
  private JCheckBox myMacros;
  private JCheckBox myDynamics;
  private JCheckBox myPostfixOps;
  private JCheckBox myReflectiveCalls;
  private JCheckBox myImplicitConversions;
  private JCheckBox myHigherKinds;
  private JCheckBox myExistentials;
  private JCheckBox myFeatureWarnings;

  private MyPathEditor myPluginsEditor = new MyPathEditor(new FileChooserDescriptor(true, false, true, true, false, true));

  public ScalaCompilerSettingsForm() {
    myIncrementalityType.setRenderer(new NamedValueRenderer());
    myIncrementalityType.setModel(new DefaultComboBoxModel<IncrementalityType>(IncrementalityType.values()));

    myCompileOrder.setRenderer(new NamedValueRenderer());
    myCompileOrder.setModel(new DefaultComboBoxModel<CompileOrder>(CompileOrder.values()));

    myDebuggingInfoLevel.setRenderer(new NamedValueRenderer());
    myDebuggingInfoLevel.setModel(new DefaultComboBoxModel<DebuggingInfoLevel>(DebuggingInfoLevel.values()));

    myPluginsPanel.setBorder(IdeBorderFactory.createBorder());
    myPluginsPanel.add(myPluginsEditor.createComponent(), BorderLayout.CENTER);
  }

  public ScalaCompilerSettingsState getState() {
    ScalaCompilerSettingsState state = new ScalaCompilerSettingsState();

    state.macros = myMacros.isSelected();
    state.dynamics = myDynamics.isSelected();
    state.postfixOps = myPostfixOps.isSelected();
    state.reflectiveCalls = myReflectiveCalls.isSelected();
    state.implicitConversions = myImplicitConversions.isSelected();
    state.higherKinds = myHigherKinds.isSelected();
    state.existentials = myExistentials.isSelected();

    state.incrementalityType = (IncrementalityType) myIncrementalityType.getSelectedItem();
    state.compileOrder = (CompileOrder) myCompileOrder.getSelectedItem();
    state.warnings = myWarnings.isSelected();
    state.deprecationWarnings = myDeprecationWarnings.isSelected();
    state.uncheckedWarnings = myUncheckedWarnings.isSelected();
    state.featureWarnings = myFeatureWarnings.isSelected();
    state.optimiseBytecode = myOptimiseBytecode.isSelected();
    state.explainTypeErrors = myExplainTypeErrors.isSelected();
    state.continuations = myContinuations.isSelected();
    state.debuggingInfoLevel = (DebuggingInfoLevel) myDebuggingInfoLevel.getSelectedItem();
    state.additionalCompilerOptions = myAdditionalCompilerOptions.getText();
    state.plugins = myPluginsEditor.getPaths();

    return state;
  }

  public void setState(ScalaCompilerSettingsState state) {
    myMacros.setSelected(state.macros);
    myDynamics.setSelected(state.dynamics);
    myPostfixOps.setSelected(state.postfixOps);
    myReflectiveCalls.setSelected(state.reflectiveCalls);
    myImplicitConversions.setSelected(state.implicitConversions);
    myHigherKinds.setSelected(state.higherKinds);
    myExistentials.setSelected(state.existentials);

    myIncrementalityType.setSelectedItem(state.incrementalityType);
    myCompileOrder.setSelectedItem(state.compileOrder);
    myWarnings.setSelected(state.warnings);
    myDeprecationWarnings.setSelected(state.deprecationWarnings);
    myUncheckedWarnings.setSelected(state.uncheckedWarnings);
    myFeatureWarnings.setSelected(state.featureWarnings);
    myOptimiseBytecode.setSelected(state.optimiseBytecode);
    myExplainTypeErrors.setSelected(state.explainTypeErrors);
    myContinuations.setSelected(state.continuations);
    myDebuggingInfoLevel.setSelectedItem(state.debuggingInfoLevel);
    myAdditionalCompilerOptions.setText(state.additionalCompilerOptions);
    myPluginsEditor.setPaths(state.plugins);
  }

  public JPanel getComponent() {
    return myContentPanel;
  }
}
