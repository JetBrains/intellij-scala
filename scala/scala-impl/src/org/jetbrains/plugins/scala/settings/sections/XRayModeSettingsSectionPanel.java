package org.jetbrains.plugins.scala.settings.sections;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings;
import org.jetbrains.plugins.scala.settings.SimpleMappingListCellRenderer;
import org.jetbrains.plugins.scala.settings.XRayWidgetMode;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

public class XRayModeSettingsSectionPanel extends SettingsSectionPanel {
    private JCheckBox myShowMethodChainHintsCheckbox;
    private JCheckBox myShowIndentGuidesCheckbox;
    private JCheckBox myShowMethodSeparatorsCheckbox;
    private JCheckBox myShowImplicitHintsCheckbox;
    private JCheckBox myShowTypeHintsCheckbox;
    private JCheckBox myShowMethodResultsCheckbox;
    private JCheckBox myShowMemberVariablesCheckbox;
    private JCheckBox myShowLocalVariablesCheckbox;
    private JCheckBox myShowLambdaParametersCheckbox;
    private JCheckBox myShowLambdaPlaceholdersCheckbox;
    private JCheckBox myShowVariablePatternsCheckbox;
    private JCheckBox myShowParameterHintsCheckbox;
    private JCheckBox myShowArgumentHintsCheckbox;
    private JCheckBox myDoublePressAndHoldCheckbox;
    private JCheckBox myPressAndHoldCheckbox;
    private JComboBox<XRayWidgetMode> myWidgetModeCombobox;
    private JPanel rootPanel;

    protected XRayModeSettingsSectionPanel(Project project) {
        super(project);

        if (SystemInfo.isMac) {
            myDoublePressAndHoldCheckbox.setText(myDoublePressAndHoldCheckbox.getText().replace("Ctrl", "Cmd"));
            myPressAndHoldCheckbox.setText(myPressAndHoldCheckbox.getText().replace("Ctrl", "Cmd"));
        }

        Map<XRayWidgetMode, String> modes = new HashMap<>();
        modes.put(XRayWidgetMode.ALWAYS, "Always");
        modes.put(XRayWidgetMode.WHEN_ACTIVE, "When active");
        modes.put(XRayWidgetMode.NEVER, "Never");
        myWidgetModeCombobox.setModel(new DefaultComboBoxModel<>(XRayWidgetMode.values()));
        myWidgetModeCombobox.setRenderer(new SimpleMappingListCellRenderer<>(modes));
        myShowTypeHintsCheckbox.addItemListener(e -> updateTypeHintCheckboxes());

        reset();
        updateTypeHintCheckboxes();
    }


    @Override
    JComponent getRootPanel() {
        return rootPanel;
    }

    @Override
    boolean isModified() {
        ScalaApplicationSettings scalaApplicationSettings = ScalaApplicationSettings.getInstance();
        return scalaApplicationSettings.XRAY_DOUBLE_PRESS_AND_HOLD != myDoublePressAndHoldCheckbox.isSelected() ||
                scalaApplicationSettings.XRAY_PRESS_AND_HOLD != myPressAndHoldCheckbox.isSelected() ||
                scalaApplicationSettings.XRAY_SHOW_PARAMETER_HINTS != myShowParameterHintsCheckbox.isSelected() ||
                scalaApplicationSettings.XRAY_SHOW_ARGUMENT_HINTS != myShowArgumentHintsCheckbox.isSelected() ||
                scalaApplicationSettings.XRAY_SHOW_TYPE_HINTS != myShowTypeHintsCheckbox.isSelected() ||
                scalaApplicationSettings.XRAY_SHOW_MEMBER_VARIABLE_HINTS != myShowMemberVariablesCheckbox.isSelected() ||
                scalaApplicationSettings.XRAY_SHOW_LOCAL_VARIABLE_HINTS != myShowLocalVariablesCheckbox.isSelected() ||
                scalaApplicationSettings.XRAY_SHOW_METHOD_RESULT_HINTS != myShowMethodResultsCheckbox.isSelected() ||
                scalaApplicationSettings.XRAY_SHOW_LAMBDA_PARAMETER_HINTS != myShowLambdaParametersCheckbox.isSelected() ||
                scalaApplicationSettings.XRAY_SHOW_LAMBDA_PLACEHOLDER_HINTS != myShowLambdaPlaceholdersCheckbox.isSelected() ||
                scalaApplicationSettings.XRAY_SHOW_VARIABLE_PATTERN_HINTS != myShowVariablePatternsCheckbox.isSelected() ||
                scalaApplicationSettings.XRAY_SHOW_METHOD_CHAIN_HINTS != myShowMethodChainHintsCheckbox.isSelected() ||
                scalaApplicationSettings.XRAY_SHOW_IMPLICIT_HINTS != myShowImplicitHintsCheckbox.isSelected() ||
                scalaApplicationSettings.XRAY_SHOW_INDENT_GUIDES != myShowIndentGuidesCheckbox.isSelected() ||
                scalaApplicationSettings.XRAY_SHOW_METHOD_SEPARATORS != myShowMethodSeparatorsCheckbox.isSelected() ||
                scalaApplicationSettings.XRAY_WIDGET_MODE != myWidgetModeCombobox.getSelectedItem();

    }

    @Override
    void apply() {
        ScalaApplicationSettings scalaApplicationSettings = ScalaApplicationSettings.getInstance();
        scalaApplicationSettings.XRAY_DOUBLE_PRESS_AND_HOLD = myDoublePressAndHoldCheckbox.isSelected();
        scalaApplicationSettings.XRAY_PRESS_AND_HOLD = myPressAndHoldCheckbox.isSelected();
        scalaApplicationSettings.XRAY_SHOW_PARAMETER_HINTS = myShowParameterHintsCheckbox.isSelected();
        scalaApplicationSettings.XRAY_SHOW_ARGUMENT_HINTS = myShowArgumentHintsCheckbox.isSelected();
        scalaApplicationSettings.XRAY_SHOW_TYPE_HINTS = myShowTypeHintsCheckbox.isSelected();
        scalaApplicationSettings.XRAY_SHOW_MEMBER_VARIABLE_HINTS = myShowMemberVariablesCheckbox.isSelected();
        scalaApplicationSettings.XRAY_SHOW_LOCAL_VARIABLE_HINTS = myShowLocalVariablesCheckbox.isSelected();
        scalaApplicationSettings.XRAY_SHOW_METHOD_RESULT_HINTS = myShowMethodResultsCheckbox.isSelected();
        scalaApplicationSettings.XRAY_SHOW_LAMBDA_PARAMETER_HINTS = myShowLambdaParametersCheckbox.isSelected();
        scalaApplicationSettings.XRAY_SHOW_LAMBDA_PLACEHOLDER_HINTS = myShowLambdaPlaceholdersCheckbox.isSelected();
        scalaApplicationSettings.XRAY_SHOW_VARIABLE_PATTERN_HINTS = myShowVariablePatternsCheckbox.isSelected();
        scalaApplicationSettings.XRAY_SHOW_METHOD_CHAIN_HINTS = myShowMethodChainHintsCheckbox.isSelected();
        scalaApplicationSettings.XRAY_SHOW_IMPLICIT_HINTS = myShowImplicitHintsCheckbox.isSelected();
        scalaApplicationSettings.XRAY_SHOW_INDENT_GUIDES = myShowIndentGuidesCheckbox.isSelected();
        scalaApplicationSettings.XRAY_SHOW_METHOD_SEPARATORS = myShowMethodSeparatorsCheckbox.isSelected();
        scalaApplicationSettings.XRAY_WIDGET_MODE = (XRayWidgetMode) myWidgetModeCombobox.getSelectedItem();
    }

    @Override
    void reset() {
        ScalaApplicationSettings scalaApplicationSettings = ScalaApplicationSettings.getInstance();
        myDoublePressAndHoldCheckbox.setSelected(scalaApplicationSettings.XRAY_DOUBLE_PRESS_AND_HOLD);
        myPressAndHoldCheckbox.setSelected(scalaApplicationSettings.XRAY_PRESS_AND_HOLD);
        myShowParameterHintsCheckbox.setSelected(scalaApplicationSettings.XRAY_SHOW_PARAMETER_HINTS);
        myShowArgumentHintsCheckbox.setSelected(scalaApplicationSettings.XRAY_SHOW_ARGUMENT_HINTS);
        myShowTypeHintsCheckbox.setSelected(scalaApplicationSettings.XRAY_SHOW_TYPE_HINTS);
        myShowMemberVariablesCheckbox.setSelected(scalaApplicationSettings.XRAY_SHOW_MEMBER_VARIABLE_HINTS);
        myShowLocalVariablesCheckbox.setSelected(scalaApplicationSettings.XRAY_SHOW_LOCAL_VARIABLE_HINTS);
        myShowMethodResultsCheckbox.setSelected(scalaApplicationSettings.XRAY_SHOW_METHOD_RESULT_HINTS);
        myShowLambdaParametersCheckbox.setSelected(scalaApplicationSettings.XRAY_SHOW_LAMBDA_PARAMETER_HINTS);
        myShowLambdaPlaceholdersCheckbox.setSelected(scalaApplicationSettings.XRAY_SHOW_LAMBDA_PLACEHOLDER_HINTS);
        myShowVariablePatternsCheckbox.setSelected(scalaApplicationSettings.XRAY_SHOW_VARIABLE_PATTERN_HINTS);
        myShowMethodChainHintsCheckbox.setSelected(scalaApplicationSettings.XRAY_SHOW_METHOD_CHAIN_HINTS);
        myShowImplicitHintsCheckbox.setSelected(scalaApplicationSettings.XRAY_SHOW_IMPLICIT_HINTS);
        myShowIndentGuidesCheckbox.setSelected(scalaApplicationSettings.XRAY_SHOW_INDENT_GUIDES);
        myShowMethodSeparatorsCheckbox.setSelected(scalaApplicationSettings.XRAY_SHOW_METHOD_SEPARATORS);
        myWidgetModeCombobox.setSelectedItem(scalaApplicationSettings.XRAY_WIDGET_MODE);
    }

    private void updateTypeHintCheckboxes() {
        boolean b = myShowTypeHintsCheckbox.isSelected();
        myShowMemberVariablesCheckbox.setEnabled(b);
        myShowLocalVariablesCheckbox.setEnabled(b);
        myShowMethodResultsCheckbox.setEnabled(b);
        myShowMethodResultsCheckbox.setEnabled(b);
        myShowLambdaParametersCheckbox.setEnabled(b);
        myShowLambdaPlaceholdersCheckbox.setEnabled(b);
        myShowVariablePatternsCheckbox.setEnabled(b);
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
        rootPanel = new JPanel();
        rootPanel.setLayout(new GridLayoutManager(22, 1, new Insets(9, 9, 9, 9), -1, -1));
        final JLabel label1 = new JLabel();
        this.$$$loadLabelText$$$(label1, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.activate"));
        rootPanel.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        rootPanel.add(spacer1, new GridConstraints(21, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        this.$$$loadLabelText$$$(label2, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.show"));
        rootPanel.add(label2, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myShowMethodChainHintsCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(myShowMethodChainHintsCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.method.chain.hints"));
        rootPanel.add(myShowMethodChainHintsCheckbox, new GridConstraints(14, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        myShowIndentGuidesCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(myShowIndentGuidesCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.indent.guides"));
        rootPanel.add(myShowIndentGuidesCheckbox, new GridConstraints(16, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        myShowMethodSeparatorsCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(myShowMethodSeparatorsCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.method.separators"));
        rootPanel.add(myShowMethodSeparatorsCheckbox, new GridConstraints(17, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        myShowImplicitHintsCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(myShowImplicitHintsCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.implicit.hints"));
        rootPanel.add(myShowImplicitHintsCheckbox, new GridConstraints(15, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        myShowTypeHintsCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(myShowTypeHintsCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.type.hints"));
        rootPanel.add(myShowTypeHintsCheckbox, new GridConstraints(7, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        myShowMethodResultsCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(myShowMethodResultsCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.method.results"));
        rootPanel.add(myShowMethodResultsCheckbox, new GridConstraints(10, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 2, false));
        myShowMemberVariablesCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(myShowMemberVariablesCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.member.variables"));
        rootPanel.add(myShowMemberVariablesCheckbox, new GridConstraints(8, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 2, false));
        myShowLocalVariablesCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(myShowLocalVariablesCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.local.variables"));
        rootPanel.add(myShowLocalVariablesCheckbox, new GridConstraints(9, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 2, false));
        myShowLambdaParametersCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(myShowLambdaParametersCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.lambda.parameters"));
        rootPanel.add(myShowLambdaParametersCheckbox, new GridConstraints(11, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 2, false));
        myShowLambdaPlaceholdersCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(myShowLambdaPlaceholdersCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.lambda.placeholders"));
        rootPanel.add(myShowLambdaPlaceholdersCheckbox, new GridConstraints(12, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 2, false));
        myShowVariablePatternsCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(myShowVariablePatternsCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.variable.patterns"));
        rootPanel.add(myShowVariablePatternsCheckbox, new GridConstraints(13, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 2, false));
        myShowParameterHintsCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(myShowParameterHintsCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.parameter.name.hints"));
        rootPanel.add(myShowParameterHintsCheckbox, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        myShowArgumentHintsCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(myShowArgumentHintsCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.by-name.argument.hints"));
        rootPanel.add(myShowArgumentHintsCheckbox, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        myDoublePressAndHoldCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(myDoublePressAndHoldCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.double.press.and.hold"));
        rootPanel.add(myDoublePressAndHoldCheckbox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        myPressAndHoldCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(myPressAndHoldCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.press.and.hold"));
        rootPanel.add(myPressAndHoldCheckbox, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        final JLabel label3 = new JLabel();
        label3.setText("");
        rootPanel.add(label3, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("");
        rootPanel.add(label4, new GridConstraints(18, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        rootPanel.add(panel1, new GridConstraints(20, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        myWidgetModeCombobox = new JComboBox();
        final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
        myWidgetModeCombobox.setModel(defaultComboBoxModel1);
        panel1.add(myWidgetModeCombobox, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(200, -1), null, 1, false));
        final JLabel label5 = new JLabel();
        this.$$$loadLabelText$$$(label5, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.widget.display"));
        panel1.add(label5, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        final JLabel label6 = new JLabel();
        this.$$$loadLabelText$$$(label6, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.widget"));
        rootPanel.add(label6, new GridConstraints(19, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
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
        return rootPanel;
    }

}
