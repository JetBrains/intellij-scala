package org.jetbrains.plugins.scala.lang.formatting.settings;

import com.intellij.openapi.util.Disposer;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaBundle;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.ResourceBundle;

// TODO: maybe group settings "Replace ... with unicode symbol"?
//  we currently have 4 similar settings in "Other" tab
public final class OtherCodeStylePanel extends ScalaCodeStylePanelBase {

    private JCheckBox enforceFunctionalSyntaxForCheckBox;
    private JPanel contentPanel;
    private JCheckBox replaceWithUnicodeSymbolCheckBox;
    private JCheckBox replaceWithUnicodeSymbolCheckBox1;
    private JCheckBox replaceInForGeneratorCheckBox;
    private JCheckBox replaceLambdaWithGreekLetter;
    private JCheckBox alternateIndentationForParamsCheckBox;
    private JSpinner alternateIndentationForParamsSpinner;
    private JPanel alternateParamIndentPanel;
    private JLabel spacesLabel;
    private JCheckBox reformatOnCompileCheckBox;
    private final TrailingCommaPanel trailingCommaPanel = new TrailingCommaPanel(getSettings());
    private JPanel trailingCommaInnerPanel;
    private JTextField implicitValueClassPrefix;
    private JTextField implicitValueClassSuffix;

    private final Scala3SettingsPanel scala3SettingsPanel = new Scala3SettingsPanel(getSettings());
    private JPanel scala3SettingsInnerPanel;

    OtherCodeStylePanel(@NotNull CodeStyleSettings settings) {
        super(settings, ScalaBundle.message("other.panel.title"));

        $$$setupUI$$$();
        alternateIndentationForParamsSpinner.setModel(new SpinnerNumberModel(4, 1, null, 1));
        resetImpl(settings);
    }

    @Override
    public void dispose() {
        super.dispose();
        Disposer.dispose(trailingCommaPanel);
        Disposer.dispose(scala3SettingsPanel);
    }

    @Override
    public void apply(CodeStyleSettings settings) {
        if (!isModified(settings)) return;

        ScalaCodeStyleSettings scalaCodeStyleSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);
        scalaCodeStyleSettings.ENFORCE_FUNCTIONAL_SYNTAX_FOR_UNIT = enforceFunctionalSyntaxForCheckBox.isSelected();
        scalaCodeStyleSettings.REPLACE_CASE_ARROW_WITH_UNICODE_CHAR = replaceWithUnicodeSymbolCheckBox.isSelected();
        scalaCodeStyleSettings.REPLACE_MAP_ARROW_WITH_UNICODE_CHAR = replaceWithUnicodeSymbolCheckBox1.isSelected();
        scalaCodeStyleSettings.REPLACE_FOR_GENERATOR_ARROW_WITH_UNICODE_CHAR = replaceInForGeneratorCheckBox.isSelected();
        scalaCodeStyleSettings.REPLACE_LAMBDA_WITH_GREEK_LETTER = replaceLambdaWithGreekLetter.isSelected();
        scalaCodeStyleSettings.USE_ALTERNATE_CONTINUATION_INDENT_FOR_PARAMS = alternateIndentationForParamsCheckBox.isSelected();
        scalaCodeStyleSettings.ALTERNATE_CONTINUATION_INDENT_FOR_PARAMS = (Integer) alternateIndentationForParamsSpinner.getValue();
        scalaCodeStyleSettings.REFORMAT_ON_COMPILE = reformatOnCompileCheckBox.isSelected();
        scalaCodeStyleSettings.IMPLICIT_VALUE_CLASS_PREFIX = implicitValueClassPrefix.getText();
        scalaCodeStyleSettings.IMPLICIT_VALUE_CLASS_SUFFIX = implicitValueClassSuffix.getText();
        trailingCommaPanel.apply(settings);
        scala3SettingsPanel.apply(settings);
    }

    @SuppressWarnings("RedundantIfStatement")
    @Override
    public boolean isModified(CodeStyleSettings settings) {
        ScalaCodeStyleSettings ss = settings.getCustomSettings(ScalaCodeStyleSettings.class);

        if (ss.ENFORCE_FUNCTIONAL_SYNTAX_FOR_UNIT != enforceFunctionalSyntaxForCheckBox.isSelected()) return true;
        if (ss.REPLACE_CASE_ARROW_WITH_UNICODE_CHAR != replaceWithUnicodeSymbolCheckBox.isSelected()) return true;
        if (ss.REPLACE_MAP_ARROW_WITH_UNICODE_CHAR != replaceWithUnicodeSymbolCheckBox1.isSelected()) return true;
        if (ss.REPLACE_FOR_GENERATOR_ARROW_WITH_UNICODE_CHAR != replaceInForGeneratorCheckBox.isSelected()) return true;
        if (ss.REPLACE_LAMBDA_WITH_GREEK_LETTER != replaceLambdaWithGreekLetter.isSelected()) return true;
        if (ss.USE_ALTERNATE_CONTINUATION_INDENT_FOR_PARAMS != alternateIndentationForParamsCheckBox.isSelected())
            return true;
        if (ss.ALTERNATE_CONTINUATION_INDENT_FOR_PARAMS != (Integer) alternateIndentationForParamsSpinner.getValue())
            return true;
        if (ss.REFORMAT_ON_COMPILE != reformatOnCompileCheckBox.isSelected()) return true;
        if (!ss.IMPLICIT_VALUE_CLASS_PREFIX.equals(implicitValueClassPrefix.getText())) return true;
        if (!ss.IMPLICIT_VALUE_CLASS_SUFFIX.equals(implicitValueClassSuffix.getText())) return true;
        if (trailingCommaPanel.isModified(settings)) return true;
        if (scala3SettingsPanel.isModified(settings)) return true;

        return false;
    }

    @Override
    protected JComponent getPanelInner() {
        return contentPanel;
    }

    @Override
    protected void resetImpl(CodeStyleSettings settings) {
        ScalaCodeStyleSettings ss = settings.getCustomSettings(ScalaCodeStyleSettings.class);
        enforceFunctionalSyntaxForCheckBox.setSelected(ss.ENFORCE_FUNCTIONAL_SYNTAX_FOR_UNIT);
        replaceWithUnicodeSymbolCheckBox.setSelected(ss.REPLACE_CASE_ARROW_WITH_UNICODE_CHAR);
        replaceWithUnicodeSymbolCheckBox1.setSelected(ss.REPLACE_MAP_ARROW_WITH_UNICODE_CHAR);
        replaceInForGeneratorCheckBox.setSelected(ss.REPLACE_FOR_GENERATOR_ARROW_WITH_UNICODE_CHAR);
        replaceLambdaWithGreekLetter.setSelected(ss.REPLACE_LAMBDA_WITH_GREEK_LETTER);
        alternateIndentationForParamsCheckBox.setSelected(ss.USE_ALTERNATE_CONTINUATION_INDENT_FOR_PARAMS);
        alternateIndentationForParamsSpinner.setValue(ss.ALTERNATE_CONTINUATION_INDENT_FOR_PARAMS);
        reformatOnCompileCheckBox.setSelected(ss.REFORMAT_ON_COMPILE);
        implicitValueClassPrefix.setText(ss.IMPLICIT_VALUE_CLASS_PREFIX);
        implicitValueClassSuffix.setText(ss.IMPLICIT_VALUE_CLASS_SUFFIX);
        trailingCommaPanel.resetImpl(settings);
        scala3SettingsPanel.resetImpl(settings);
    }

    private void createUIComponents() {
        trailingCommaInnerPanel = trailingCommaPanel.getPanel();
        scala3SettingsInnerPanel = scala3SettingsPanel.getPanel();
    }

    public void toggleExternalFormatter(boolean useExternalFormatter) {
        alternateIndentationForParamsCheckBox.setVisible(!useExternalFormatter);
        alternateIndentationForParamsSpinner.setVisible(!useExternalFormatter);
        spacesLabel.setVisible(!useExternalFormatter);
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        createUIComponents();
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        contentPanel = new JPanel();
        contentPanel.setLayout(new GridLayoutManager(11, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(contentPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        contentPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        enforceFunctionalSyntaxForCheckBox = new JCheckBox();
        this.$$$loadButtonText$$$(enforceFunctionalSyntaxForCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "other.panel.enforce.functional.syntax.for.methods.with.unit.return.type"));
        contentPanel.add(enforceFunctionalSyntaxForCheckBox, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        contentPanel.add(spacer1, new GridConstraints(10, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        replaceWithUnicodeSymbolCheckBox = new JCheckBox();
        this.$$$loadButtonText$$$(replaceWithUnicodeSymbolCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "other.panel.replace.with.unicode.symbol"));
        contentPanel.add(replaceWithUnicodeSymbolCheckBox, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        replaceWithUnicodeSymbolCheckBox1 = new JCheckBox();
        this.$$$loadButtonText$$$(replaceWithUnicodeSymbolCheckBox1, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "other.panel.replace.with.unicode.symbol1"));
        contentPanel.add(replaceWithUnicodeSymbolCheckBox1, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        replaceInForGeneratorCheckBox = new JCheckBox();
        this.$$$loadButtonText$$$(replaceInForGeneratorCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "other.panel.replace.in.for.generator.with.unicode.symbol"));
        contentPanel.add(replaceInForGeneratorCheckBox, new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        replaceLambdaWithGreekLetter = new JCheckBox();
        replaceLambdaWithGreekLetter.setSelected(false);
        this.$$$loadButtonText$$$(replaceLambdaWithGreekLetter, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "other.panel.kind.projector.replace.lambda.with.unicode.symbol"));
        contentPanel.add(replaceLambdaWithGreekLetter, new GridConstraints(5, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        alternateParamIndentPanel = new JPanel();
        alternateParamIndentPanel.setLayout(new GridLayoutManager(1, 4, new Insets(0, 0, 0, 0), -1, -1));
        contentPanel.add(alternateParamIndentPanel, new GridConstraints(6, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        alternateIndentationForParamsCheckBox = new JCheckBox();
        this.$$$loadButtonText$$$(alternateIndentationForParamsCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "other.panel.alternate.indentation.for.constructor.args.and.parameter.declarations"));
        alternateIndentationForParamsCheckBox.setVerticalAlignment(1);
        alternateParamIndentPanel.add(alternateIndentationForParamsCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        alternateIndentationForParamsSpinner = new JSpinner();
        alternateParamIndentPanel.add(alternateIndentationForParamsSpinner, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, new Dimension(1, -1), new Dimension(2, -1), null, 1, false));
        spacesLabel = new JLabel();
        this.$$$loadLabelText$$$(spacesLabel, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "other.panel.spaces"));
        spacesLabel.setVerticalAlignment(1);
        alternateParamIndentPanel.add(spacesLabel, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        alternateParamIndentPanel.add(spacer2, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        reformatOnCompileCheckBox = new JCheckBox();
        this.$$$loadButtonText$$$(reformatOnCompileCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "other.panel.reformat.on.compile"));
        contentPanel.add(reformatOnCompileCheckBox, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        contentPanel.add(trailingCommaInnerPanel, new GridConstraints(8, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 4, new Insets(0, 0, 0, 0), -1, -1));
        contentPanel.add(panel2, new GridConstraints(9, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final Spacer spacer3 = new Spacer();
        panel2.add(spacer3, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        implicitValueClassPrefix = new JTextField();
        panel2.add(implicitValueClassPrefix, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        this.$$$loadLabelText$$$(label1, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "other.panel.implicit.class.prefix.suffix"));
        label1.setToolTipText("");
        panel2.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        implicitValueClassSuffix = new JTextField();
        implicitValueClassSuffix.setText("Ops");
        panel2.add(implicitValueClassSuffix, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        contentPanel.add(scala3SettingsInnerPanel, new GridConstraints(7, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
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

}
