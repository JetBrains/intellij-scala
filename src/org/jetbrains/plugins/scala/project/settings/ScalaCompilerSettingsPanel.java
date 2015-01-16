package org.jetbrains.plugins.scala.project.settings;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.TitledSeparator;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.jetbrains.plugins.scala.project.*;

import javax.swing.*;
import java.awt.*;

/**
 * @author Pavel Fatin
 */
public class ScalaCompilerSettingsPanel {
  private JPanel myContentPanel;
  private JPanel myPluginsPanel;
  private RawCommandLineEditor myAdditionalCompilerOptions;
  private JComboBox myDebuggingInfoLevel;
  private JCheckBox myWarnings;
  private JCheckBox myDeprecationWarnings;
  private JCheckBox myUncheckedWarnings;
  private JCheckBox myOptimiseBytecode;
  private JCheckBox myExplainTypeErrors;
  private JCheckBox myContinuations;
  private JComboBox myCompileOrder;
  private JCheckBox myDynamics;
  private JCheckBox myPostfixOps;
  private JCheckBox myReflectiveCalls;
  private JCheckBox myImplicitConversions;
  private JCheckBox myHigherKinds;
  private JCheckBox myExistentials;
  private JCheckBox myFeatureWarnings;
  private JCheckBox myMacros;
  private JCheckBox mySpecialization;

  private MyPathEditor myPluginsEditor = new MyPathEditor(new FileChooserDescriptor(true, false, true, true, false, true));

  public ScalaCompilerSettingsPanel() {
    myCompileOrder.setRenderer(new NamedValueRenderer());
    myCompileOrder.setModel(new DefaultComboBoxModel(CompileOrder.values()));

    myDebuggingInfoLevel.setRenderer(new NamedValueRenderer());
    myDebuggingInfoLevel.setModel(new DefaultComboBoxModel(DebuggingInfoLevel.values()));

    myPluginsPanel.setBorder(IdeBorderFactory.createBorder());
    myPluginsPanel.add(myPluginsEditor.createComponent(), BorderLayout.CENTER);
  }

  public ScalaCompilerSettingsState getState() {
    ScalaCompilerSettingsState state = new ScalaCompilerSettingsState();

    state.dynamics = myDynamics.isSelected();
    state.postfixOps = myPostfixOps.isSelected();
    state.reflectiveCalls = myReflectiveCalls.isSelected();
    state.implicitConversions = myImplicitConversions.isSelected();
    state.higherKinds = myHigherKinds.isSelected();
    state.existentials = myExistentials.isSelected();
    state.macros = myMacros.isSelected();

    state.compileOrder = (CompileOrder) myCompileOrder.getSelectedItem();
    state.warnings = myWarnings.isSelected();
    state.deprecationWarnings = myDeprecationWarnings.isSelected();
    state.uncheckedWarnings = myUncheckedWarnings.isSelected();
    state.featureWarnings = myFeatureWarnings.isSelected();
    state.optimiseBytecode = myOptimiseBytecode.isSelected();
    state.explainTypeErrors = myExplainTypeErrors.isSelected();
    state.specialization = mySpecialization.isSelected();
    state.continuations = myContinuations.isSelected();
    state.debuggingInfoLevel = (DebuggingInfoLevel) myDebuggingInfoLevel.getSelectedItem();
    String options = myAdditionalCompilerOptions.getText().trim();
    state.additionalCompilerOptions = options.isEmpty() ? new String[0] : options.split("\\s+");
    state.plugins = urlsToPaths(myPluginsEditor.getPaths());

    return state;
  }

  private static String[] urlsToPaths(String[] urls) {
    String[] result = new String[urls.length];
    int i = 0;
    for (String url : urls) {
      result[i] = VfsUtil.urlToPath(url);
      i++;
    }
    return result;
  }

  public void setState(ScalaCompilerSettingsState state) {
    myDynamics.setSelected(state.dynamics);
    myPostfixOps.setSelected(state.postfixOps);
    myReflectiveCalls.setSelected(state.reflectiveCalls);
    myImplicitConversions.setSelected(state.implicitConversions);
    myHigherKinds.setSelected(state.higherKinds);
    myExistentials.setSelected(state.existentials);
    myMacros.setSelected(state.macros);

    myCompileOrder.setSelectedItem(state.compileOrder);
    myWarnings.setSelected(state.warnings);
    myDeprecationWarnings.setSelected(state.deprecationWarnings);
    myUncheckedWarnings.setSelected(state.uncheckedWarnings);
    myFeatureWarnings.setSelected(state.featureWarnings);
    myOptimiseBytecode.setSelected(state.optimiseBytecode);
    myExplainTypeErrors.setSelected(state.explainTypeErrors);
    mySpecialization.setSelected(state.specialization);
    myContinuations.setSelected(state.continuations);
    myDebuggingInfoLevel.setSelectedItem(state.debuggingInfoLevel);
    myAdditionalCompilerOptions.setText(StringUtil.join(state.additionalCompilerOptions, " "));
    myPluginsEditor.setPaths(pathsToUrls(state.plugins));
  }

  private static String[] pathsToUrls(String[] paths) {
    String[] result = new String[paths.length];
    int i = 0;
    for (String path : paths) {
      result[i] = VfsUtil.pathToUrl(path);
      i++;
    }
    return result;
  }

  public JPanel getComponent() {
    return myContentPanel;
  }

  public void saveTo(ScalaCompilerSettingsProfile profile) {
    profile.setSettings(new ScalaCompilerSettings(getState()));
  }

  public void setProfile(ScalaCompilerSettingsProfile profile) {
    setState(profile.getSettings().getState());
  }

  {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
    $$$setupUI$$$();
  }

  /**
   * Method generated by IntelliJ IDEA GUI Designer
   * >>> IMPORTANT!! <<<
   * DO NOT edit this method OR call it in your code!
   *
   * @noinspection ALL
   */
  private void $$$setupUI$$$() {
    myContentPanel = new JPanel();
    myContentPanel.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
    myPluginsPanel = new JPanel();
    myPluginsPanel.setLayout(new BorderLayout(0, 0));
    myContentPanel.add(myPluginsPanel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(13, 2, new Insets(0, 0, 10, 0), -1, -1));
    myContentPanel.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    final JLabel label1 = new JLabel();
    label1.setText("Additional compiler options:");
    label1.setDisplayedMnemonic('O');
    label1.setDisplayedMnemonicIndex(20);
    panel1.add(label1, new GridConstraints(12, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myAdditionalCompilerOptions = new RawCommandLineEditor();
    myAdditionalCompilerOptions.setDialogCaption("Additional command-line parameters for Scala compiler");
    panel1.add(myAdditionalCompilerOptions, new GridConstraints(12, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, new Dimension(250, -1), null, null, 0, false));
    final JLabel label2 = new JLabel();
    label2.setText("Debugging info level:");
    label2.setDisplayedMnemonic('L');
    label2.setDisplayedMnemonicIndex(15);
    panel1.add(label2, new GridConstraints(11, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myDebuggingInfoLevel = new JComboBox();
    panel1.add(myDebuggingInfoLevel, new GridConstraints(11, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myWarnings = new JCheckBox();
    myWarnings.setText("Enable warnings");
    myWarnings.setMnemonic('W');
    myWarnings.setDisplayedMnemonicIndex(7);
    myWarnings.setToolTipText("Generate warnings");
    panel1.add(myWarnings, new GridConstraints(7, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myDeprecationWarnings = new JCheckBox();
    myDeprecationWarnings.setText("Deprecation warnings");
    myDeprecationWarnings.setMnemonic('D');
    myDeprecationWarnings.setDisplayedMnemonicIndex(0);
    myDeprecationWarnings.setToolTipText("Emit warning and location for usages of deprecated APIs.");
    panel1.add(myDeprecationWarnings, new GridConstraints(8, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
    myUncheckedWarnings = new JCheckBox();
    myUncheckedWarnings.setText("Unchecked warnings");
    myUncheckedWarnings.setMnemonic('U');
    myUncheckedWarnings.setDisplayedMnemonicIndex(0);
    myUncheckedWarnings.setToolTipText(" Enable additional warnings where generated code depends on assumptions. ");
    panel1.add(myUncheckedWarnings, new GridConstraints(9, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
    myOptimiseBytecode = new JCheckBox();
    myOptimiseBytecode.setText("Optimise bytecode (use with care*)");
    myOptimiseBytecode.setMnemonic('O');
    myOptimiseBytecode.setDisplayedMnemonicIndex(0);
    myOptimiseBytecode.setToolTipText("Generates faster bytecode by applying optimisations to the program. May trigger various compilation problems. Use with care.");
    panel1.add(myOptimiseBytecode, new GridConstraints(7, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myExplainTypeErrors = new JCheckBox();
    myExplainTypeErrors.setText("Explain type errors");
    myExplainTypeErrors.setMnemonic('E');
    myExplainTypeErrors.setDisplayedMnemonicIndex(0);
    myExplainTypeErrors.setToolTipText("Explain type errors in more detail");
    panel1.add(myExplainTypeErrors, new GridConstraints(8, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JPanel panel2 = new JPanel();
    panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    panel1.add(panel2, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    final JLabel label3 = new JLabel();
    label3.setText("Compile order:");
    label3.setDisplayedMnemonic('O');
    label3.setDisplayedMnemonicIndex(8);
    panel2.add(label3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myCompileOrder = new JComboBox();
    panel2.add(myCompileOrder, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final TitledSeparator titledSeparator1 = new TitledSeparator();
    titledSeparator1.setText("Options");
    panel1.add(titledSeparator1, new GridConstraints(6, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    myDynamics = new JCheckBox();
    myDynamics.setText("Dynamics");
    myDynamics.setMnemonic('D');
    myDynamics.setDisplayedMnemonicIndex(0);
    panel1.add(myDynamics, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myPostfixOps = new JCheckBox();
    myPostfixOps.setText("Postfix operator notation");
    myPostfixOps.setMnemonic('P');
    myPostfixOps.setDisplayedMnemonicIndex(0);
    panel1.add(myPostfixOps, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myHigherKinds = new JCheckBox();
    myHigherKinds.setText("Higher-kinded types");
    myHigherKinds.setMnemonic('H');
    myHigherKinds.setDisplayedMnemonicIndex(0);
    panel1.add(myHigherKinds, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myExistentials = new JCheckBox();
    myExistentials.setText("Existential types");
    myExistentials.setMnemonic('E');
    myExistentials.setDisplayedMnemonicIndex(0);
    panel1.add(myExistentials, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final TitledSeparator titledSeparator2 = new TitledSeparator();
    titledSeparator2.setText("Features");
    panel1.add(titledSeparator2, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    myFeatureWarnings = new JCheckBox();
    myFeatureWarnings.setText("Feature warnings");
    myFeatureWarnings.setMnemonic('F');
    myFeatureWarnings.setDisplayedMnemonicIndex(0);
    myFeatureWarnings.setToolTipText("Emit language feature warnings. ");
    panel1.add(myFeatureWarnings, new GridConstraints(10, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
    myImplicitConversions = new JCheckBox();
    myImplicitConversions.setText("Implicit conversions");
    myImplicitConversions.setMnemonic('I');
    myImplicitConversions.setDisplayedMnemonicIndex(0);
    panel1.add(myImplicitConversions, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myMacros = new JCheckBox();
    myMacros.setText("Macros");
    myMacros.setMnemonic('M');
    myMacros.setDisplayedMnemonicIndex(0);
    panel1.add(myMacros, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myReflectiveCalls = new JCheckBox();
    myReflectiveCalls.setText("Reflective calls");
    myReflectiveCalls.setMnemonic('R');
    myReflectiveCalls.setDisplayedMnemonicIndex(0);
    panel1.add(myReflectiveCalls, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myContinuations = new JCheckBox();
    myContinuations.setText("Enable continuations");
    myContinuations.setMnemonic('C');
    myContinuations.setDisplayedMnemonicIndex(7);
    panel1.add(myContinuations, new GridConstraints(10, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    mySpecialization = new JCheckBox();
    mySpecialization.setText("Enable specialization");
    mySpecialization.setMnemonic('S');
    mySpecialization.setDisplayedMnemonicIndex(7);
    mySpecialization.setToolTipText("Respect @specialize annotations");
    panel1.add(mySpecialization, new GridConstraints(9, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final TitledSeparator titledSeparator3 = new TitledSeparator();
    titledSeparator3.setText("Compiler plugins");
    myContentPanel.add(titledSeparator3, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
  }

  /**
   * @noinspection ALL
   */
  public JComponent $$$getRootComponent$$$() {
    return myContentPanel;
  }
}
