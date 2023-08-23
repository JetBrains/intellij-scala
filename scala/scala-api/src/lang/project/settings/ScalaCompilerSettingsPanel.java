package org.jetbrains.plugins.scala.project.settings;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.TitledSeparator;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.compiler.data.CompileOrder;
import org.jetbrains.plugins.scala.compiler.data.DebuggingInfoLevel;
import org.jetbrains.plugins.scala.compiler.data.ScalaCompilerSettingsState;
import org.jetbrains.plugins.scala.project.MyPathEditor;
import org.jetbrains.plugins.scala.settings.SimpleMappingListCellRenderer;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.ResourceBundle;

@SuppressWarnings({"unchecked", "deprecation"})
public class ScalaCompilerSettingsPanel {
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
    private JCheckBox myDynamics;
    private JCheckBox myPostfixOps;
    private JCheckBox myReflectiveCalls;
    private JCheckBox myImplicitConversions;
    private JCheckBox myHigherKinds;
    private JCheckBox myExistentials;
    private JCheckBox myFeatureWarnings;
    private JCheckBox myMacros;
    private JCheckBox mySpecialization;
    private JCheckBox myExperimental;

    private boolean myNameHashing;
    private boolean myRecompileOnMacroDef;
    private int myTransitiveStep;
    private double myRecompileAllFraction;

    private final MyPathEditor myPluginsEditor = new MyPathEditor(new FileChooserDescriptor(true, false, true, true, false, true));

    public ScalaCompilerSettingsPanel() {
        myCompileOrder.setRenderer(SimpleMappingListCellRenderer.create(
                Pair.create(CompileOrder.Mixed, ScalaBundle.message("compile.order.mixed")),
                Pair.create(CompileOrder.JavaThenScala, ScalaBundle.message("compile.order.java.then.scala")),
                Pair.create(CompileOrder.ScalaThenJava, ScalaBundle.message("compile.order.scala.then.java"))
        ));
        myCompileOrder.setModel(new DefaultComboBoxModel<>(CompileOrder.values()));

        myDebuggingInfoLevel.setRenderer(SimpleMappingListCellRenderer.create(
                Pair.create(DebuggingInfoLevel.None, ScalaBundle.message("debug.info.level.none")),
                Pair.create(DebuggingInfoLevel.Source, ScalaBundle.message("debug.info.level.source")),
                Pair.create(DebuggingInfoLevel.Line, ScalaBundle.message("debug.info.level.source.and.line.number")),
                Pair.create(DebuggingInfoLevel.Vars, ScalaBundle.message("debug.info.level.source.line.number.and.local.variable")),
                Pair.create(DebuggingInfoLevel.Notailcalls, ScalaBundle.message("debug.info.level.complete.no.tail.call.optimization"))
        ));
        myDebuggingInfoLevel.setModel(new DefaultComboBoxModel<>(DebuggingInfoLevel.values()));

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
        state.experimental = myExperimental.isSelected();

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

        state.nameHashing = myNameHashing;
        state.recompileOnMacroDef = myRecompileOnMacroDef;
        state.transitiveStep = myTransitiveStep;
        state.recompileAllFraction = myRecompileAllFraction;

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
        myExperimental.setSelected(state.experimental);

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

        myNameHashing = state.nameHashing;
        myRecompileOnMacroDef = state.recompileOnMacroDef;
        myTransitiveStep = state.transitiveStep;
        myRecompileAllFraction = state.recompileAllFraction;
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
        profile.setSettings(ScalaCompilerSettings.fromState(getState()));
    }

    public void setProfile(ScalaCompilerSettingsProfile profile) {
        setState(profile.getSettings().toState());
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
        this.$$$loadLabelText$$$(label1, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "additional.compiler.options"));
        panel1.add(label1, new GridConstraints(12, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myAdditionalCompilerOptions = new RawCommandLineEditor();
        myAdditionalCompilerOptions.setDialogCaption("");
        panel1.add(myAdditionalCompilerOptions, new GridConstraints(12, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, new Dimension(250, -1), null, null, 0, false));
        final JLabel label2 = new JLabel();
        this.$$$loadLabelText$$$(label2, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "debugging.info.level"));
        panel1.add(label2, new GridConstraints(11, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myDebuggingInfoLevel = new JComboBox();
        panel1.add(myDebuggingInfoLevel, new GridConstraints(11, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myWarnings = new JCheckBox();
        this.$$$loadButtonText$$$(myWarnings, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "enable.warnings"));
        myWarnings.setToolTipText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "enable.warnings.tooltip"));
        panel1.add(myWarnings, new GridConstraints(7, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myDeprecationWarnings = new JCheckBox();
        this.$$$loadButtonText$$$(myDeprecationWarnings, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "deprecation.warnings"));
        myDeprecationWarnings.setToolTipText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "deprecation.warnings.tooltip"));
        panel1.add(myDeprecationWarnings, new GridConstraints(8, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        myUncheckedWarnings = new JCheckBox();
        this.$$$loadButtonText$$$(myUncheckedWarnings, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "unchecked.warnings"));
        myUncheckedWarnings.setToolTipText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "unchecked.warnings.tooltip"));
        panel1.add(myUncheckedWarnings, new GridConstraints(9, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        myOptimiseBytecode = new JCheckBox();
        this.$$$loadButtonText$$$(myOptimiseBytecode, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "optimise.bytecode"));
        myOptimiseBytecode.setToolTipText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "optimise.bytecode.tooltip"));
        panel1.add(myOptimiseBytecode, new GridConstraints(7, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myExplainTypeErrors = new JCheckBox();
        this.$$$loadButtonText$$$(myExplainTypeErrors, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "explain.type.errors"));
        myExplainTypeErrors.setToolTipText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "explain.type.errors.tooltip"));
        panel1.add(myExplainTypeErrors, new GridConstraints(8, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(panel2, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        this.$$$loadLabelText$$$(label3, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "compile.order"));
        panel2.add(label3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myCompileOrder = new JComboBox();
        panel2.add(myCompileOrder, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final TitledSeparator titledSeparator1 = new TitledSeparator();
        titledSeparator1.setText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "options"));
        panel1.add(titledSeparator1, new GridConstraints(6, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        myDynamics = new JCheckBox();
        this.$$$loadButtonText$$$(myDynamics, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "feature.dynamics"));
        panel1.add(myDynamics, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myPostfixOps = new JCheckBox();
        this.$$$loadButtonText$$$(myPostfixOps, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "feature.postfix.notation"));
        panel1.add(myPostfixOps, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myHigherKinds = new JCheckBox();
        this.$$$loadButtonText$$$(myHigherKinds, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "feature.higher.kinded.types"));
        panel1.add(myHigherKinds, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myExistentials = new JCheckBox();
        this.$$$loadButtonText$$$(myExistentials, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "feature.existential.types"));
        panel1.add(myExistentials, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final TitledSeparator titledSeparator2 = new TitledSeparator();
        titledSeparator2.setText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "features"));
        panel1.add(titledSeparator2, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        myFeatureWarnings = new JCheckBox();
        this.$$$loadButtonText$$$(myFeatureWarnings, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "feature.warnings"));
        myFeatureWarnings.setToolTipText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "feature.warnings.tooltip"));
        panel1.add(myFeatureWarnings, new GridConstraints(10, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        myImplicitConversions = new JCheckBox();
        this.$$$loadButtonText$$$(myImplicitConversions, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "feature.implicit.conversions"));
        panel1.add(myImplicitConversions, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myMacros = new JCheckBox();
        this.$$$loadButtonText$$$(myMacros, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "feature.macros"));
        panel1.add(myMacros, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myReflectiveCalls = new JCheckBox();
        this.$$$loadButtonText$$$(myReflectiveCalls, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "feature.reflective.calls"));
        panel1.add(myReflectiveCalls, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myContinuations = new JCheckBox();
        this.$$$loadButtonText$$$(myContinuations, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "enable.continuations"));
        panel1.add(myContinuations, new GridConstraints(10, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        mySpecialization = new JCheckBox();
        this.$$$loadButtonText$$$(mySpecialization, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "enable.specialization"));
        mySpecialization.setToolTipText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "enable.specialization.tooltip"));
        panel1.add(mySpecialization, new GridConstraints(9, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myExperimental = new JCheckBox();
        this.$$$loadButtonText$$$(myExperimental, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "feature.experimental.features"));
        panel1.add(myExperimental, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final TitledSeparator titledSeparator3 = new TitledSeparator();
        titledSeparator3.setText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "compiler.plugins"));
        myContentPanel.add(titledSeparator3, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        label2.setLabelFor(myDebuggingInfoLevel);
        label3.setLabelFor(myCompileOrder);
    }

    private static Method $$$cachedGetBundleMethod$$$ = null;

    private String $$$getMessageFromBundle$$$(String path, String key) {
        ResourceBundle bundle;
        try {
            Class<?> thisClass = this.getClass();
            if ($$$cachedGetBundleMethod$$$ == null) {
                Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
                $$$cachedGetBundleMethod$$$ = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
            }
            bundle = (ResourceBundle) $$$cachedGetBundleMethod$$$.invoke(null, path, thisClass);
        } catch (Exception e) {
            bundle = ResourceBundle.getBundle(path);
        }
        return bundle.getString(key);
    }

    /**
     * @noinspection ALL
     */
    private void $$$loadLabelText$$$(JLabel component, String text) {
        StringBuffer result = new StringBuffer();
        boolean haveMnemonic = false;
        char mnemonic = '\0';
        int mnemonicIndex = -1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '&') {
                i++;
                if (i == text.length()) break;
                if (!haveMnemonic && text.charAt(i) != '&') {
                    haveMnemonic = true;
                    mnemonic = text.charAt(i);
                    mnemonicIndex = result.length();
                }
            }
            result.append(text.charAt(i));
        }
        component.setText(result.toString());
        if (haveMnemonic) {
            component.setDisplayedMnemonic(mnemonic);
            component.setDisplayedMnemonicIndex(mnemonicIndex);
        }
    }

    /**
     * @noinspection ALL
     */
    private void $$$loadButtonText$$$(AbstractButton component, String text) {
        StringBuffer result = new StringBuffer();
        boolean haveMnemonic = false;
        char mnemonic = '\0';
        int mnemonicIndex = -1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '&') {
                i++;
                if (i == text.length()) break;
                if (!haveMnemonic && text.charAt(i) != '&') {
                    haveMnemonic = true;
                    mnemonic = text.charAt(i);
                    mnemonicIndex = result.length();
                }
            }
            result.append(text.charAt(i));
        }
        component.setText(result.toString());
        if (haveMnemonic) {
            component.setMnemonic(mnemonic);
            component.setDisplayedMnemonicIndex(mnemonicIndex);
        }
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return myContentPanel;
    }

}
