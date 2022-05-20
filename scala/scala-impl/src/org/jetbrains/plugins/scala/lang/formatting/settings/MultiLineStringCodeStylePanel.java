package org.jetbrains.plugins.scala.lang.formatting.settings;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaBundle;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.lang.reflect.Method;
import java.util.ResourceBundle;

public final class MultiLineStringCodeStylePanel extends ScalaCodeStylePanelBase {

    private JPanel panel1;
    private JCheckBox openingQuotesOnNewCheckBox;
    private JCheckBox closingQuotesOnNewLine;
    private JCheckBox insertMarginChar;
    private JCheckBox processMarginCheckBox;
    private JTextField marginCharTextField;
    private JSpinner marginIndentSpinner;
    private JCheckBox alignDanglingClosingQuotes;

    protected MultiLineStringCodeStylePanel(@NotNull CodeStyleSettings settings) {
        super(settings, ScalaBundle.message("multi.line.string.panel.title"));

        ScalaCodeStyleSettings scalaSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);
        marginIndentSpinner.setModel(new SpinnerNumberModel(1, 1, null, 1));
        setSettings(scalaSettings);

        //validation
        marginCharTextField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                final String text = marginCharTextField.getText();
                final String selectedText = marginCharTextField.getSelectedText();
                if (isInvalidInput(text, selectedText, e)) {
                    e.consume();
                }
            }
        });
        marginCharTextField.addFocusListener(new NonEmptyFieldValidator(marginCharTextField));
    }

    @Override
    public void apply(CodeStyleSettings settings) {
        if (!isModified(settings)) return;
        ScalaCodeStyleSettings ss = settings.getCustomSettings(ScalaCodeStyleSettings.class);

        ss.MULTILINE_STRING_OPENING_QUOTES_ON_NEW_LINE = openingQuotesOnNewCheckBox.isSelected();
        ss.MULTILINE_STRING_CLOSING_QUOTES_ON_NEW_LINE = closingQuotesOnNewLine.isSelected();
        ss.MULTILINE_STRING_ALIGN_DANGLING_CLOSING_QUOTES = alignDanglingClosingQuotes.isSelected();
        ss.MULTILINE_STRING_INSERT_MARGIN_ON_ENTER = insertMarginChar.isSelected();
        ss.MULTILINE_STRING_PROCESS_MARGIN_ON_COPY_PASTE = processMarginCheckBox.isSelected();
        ss.MULTILINE_STRING_MARGIN_CHAR = marginCharTextField.getText();
        ss.MULTILINE_STRING_MARGIN_INDENT = (Integer) marginIndentSpinner.getValue();
    }

    @SuppressWarnings("RedundantIfStatement")
    @Override
    public boolean isModified(CodeStyleSettings settings) {
        ScalaCodeStyleSettings ss = settings.getCustomSettings(ScalaCodeStyleSettings.class);

        if (ss.MULTILINE_STRING_OPENING_QUOTES_ON_NEW_LINE != openingQuotesOnNewCheckBox.isSelected()) return true;
        if (ss.MULTILINE_STRING_CLOSING_QUOTES_ON_NEW_LINE != closingQuotesOnNewLine.isSelected()) return true;
        if (ss.MULTILINE_STRING_ALIGN_DANGLING_CLOSING_QUOTES != alignDanglingClosingQuotes.isSelected()) return true;
        if (ss.MULTILINE_STRING_INSERT_MARGIN_ON_ENTER != insertMarginChar.isSelected()) return true;
        if (ss.MULTILINE_STRING_PROCESS_MARGIN_ON_COPY_PASTE != processMarginCheckBox.isSelected()) return true;
        if (!ss.MULTILINE_STRING_MARGIN_CHAR.equals(marginCharTextField.getText())) return true;
        if (ss.MULTILINE_STRING_MARGIN_INDENT != (Integer) marginIndentSpinner.getValue()) return true;

        return false;
    }

    @Override
    protected JComponent getPanelInner() {
        return panel1;
    }

    @Override
    protected void resetImpl(CodeStyleSettings settings) {
        ScalaCodeStyleSettings scalaSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);
        setSettings(scalaSettings);
    }

    private void setSettings(ScalaCodeStyleSettings ss) {
        openingQuotesOnNewCheckBox.setSelected(ss.MULTILINE_STRING_OPENING_QUOTES_ON_NEW_LINE);
        closingQuotesOnNewLine.setSelected(ss.MULTILINE_STRING_CLOSING_QUOTES_ON_NEW_LINE);
        alignDanglingClosingQuotes.setSelected(ss.MULTILINE_STRING_ALIGN_DANGLING_CLOSING_QUOTES);
        insertMarginChar.setSelected(ss.MULTILINE_STRING_INSERT_MARGIN_ON_ENTER);
        processMarginCheckBox.setSelected(ss.MULTILINE_STRING_PROCESS_MARGIN_ON_COPY_PASTE);
        marginCharTextField.setText(ss.MULTILINE_STRING_MARGIN_CHAR);
        marginIndentSpinner.setValue(ss.MULTILINE_STRING_MARGIN_INDENT);
    }

    private static boolean isInvalidInput(@NotNull String text, String selectedText, KeyEvent e) {
        return text.length() > 0 && !e.isActionKey() && e.getKeyChar() != KeyEvent.VK_BACK_SPACE &&
                e.getKeyChar() != KeyEvent.VK_DELETE && !text.equals(selectedText);
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
        panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(8, 3, new Insets(0, 0, 0, 0), -1, -1));
        panel1.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        openingQuotesOnNewCheckBox = new JCheckBox();
        this.$$$loadButtonText$$$(openingQuotesOnNewCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "multi.line.string.panel.opening.quotes.on.new.line"));
        panel1.add(openingQuotesOnNewCheckBox, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        closingQuotesOnNewLine = new JCheckBox();
        this.$$$loadButtonText$$$(closingQuotesOnNewLine, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "multi.line.string.panel.place.closing.quotes.on.new.line.on.enter.press"));
        panel1.add(closingQuotesOnNewLine, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        insertMarginChar = new JCheckBox();
        this.$$$loadButtonText$$$(insertMarginChar, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "multi.line.string.panel.insert.margin.char.on.enter"));
        panel1.add(insertMarginChar, new GridConstraints(3, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        this.$$$loadLabelText$$$(label1, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "multi.line.string.panel.margin.char.value"));
        panel1.add(label1, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        this.$$$loadLabelText$$$(label2, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "multi.line.string.panel.margin.char.indent"));
        panel1.add(label2, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        processMarginCheckBox = new JCheckBox();
        this.$$$loadButtonText$$$(processMarginCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "multi.line.string.panel.process.margin.on.copy.paste"));
        panel1.add(processMarginCheckBox, new GridConstraints(4, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        marginIndentSpinner = new JSpinner();
        panel1.add(marginIndentSpinner, new GridConstraints(6, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(7, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel1.add(spacer2, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        marginCharTextField = new JTextField();
        marginCharTextField.setText("|");
        panel1.add(marginCharTextField, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        alignDanglingClosingQuotes = new JCheckBox();
        this.$$$loadButtonText$$$(alignDanglingClosingQuotes, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "multi.line.string.panel.align.dangling.closing.quotes"));
        panel1.add(alignDanglingClosingQuotes, new GridConstraints(2, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
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
        return panel1;
    }

    private static class NonEmptyFieldValidator extends FocusAdapter {
        private String myOldText;
        private final JTextField myField;

        private NonEmptyFieldValidator(JTextField field) {
            super();
            myField = field;
        }

        @Override
        public void focusGained(FocusEvent e) {
            myOldText = myField.getText();
        }

        @Override
        public void focusLost(FocusEvent e) {
            if (myField.getText().length() == 0) {
                myField.setText(myOldText);
            }
        }
    }
}
