package org.jetbrains.plugins.scala.lang.formatting.settings;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.highlighter.ScalaEditorHighlighter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ResourceBundle;

/**
 * User: Dmitry Naydanov
 * Date: 4/23/12
 */
public class MultiLineStringCodeStylePanel extends CodeStyleAbstractPanel {
  private JPanel panel1;
  private JComboBox supportLevelChooser;
  private JLabel supportLevelLabel;
  private JTextField marginCharTextField;
  private JLabel marginCharLabel;
  private JCheckBox openingQuotesOnNewCheckBox;
  private JCheckBox keepOpeningQuotesCheckBox;
  private JSpinner marginIndentSpinner;
  private JCheckBox processMarginCheckBox;

  protected MultiLineStringCodeStylePanel(CodeStyleSettings settings) {
    super(settings);
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

    supportLevelChooser.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (supportLevelChooser.getSelectedIndex() != ScalaCodeStyleSettings.MULTILINE_STRING_ALL) {
          processMarginCheckBox.setSelected(false);
          processMarginCheckBox.setEnabled(false);
        } else {
          processMarginCheckBox.setEnabled(true);
        }
      }
    });
  }

  @Override
  protected int getRightMargin() {
    return 0;
  }

  @Override
  protected String getTabTitle() {
    return ScalaBundle.message("multi.line.string.settings.panel");
  }

  @Override
  protected void prepareForReformat(PsiFile psiFile) {
  }

  @Override
  protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
    return new ScalaEditorHighlighter(null, null, scheme);
  }

  @NotNull
  @Override
  protected FileType getFileType() {
    return ScalaFileType.INSTANCE;
  }

  @Override
  protected String getPreviewText() {
    return "";
  }

  @Override
  public void apply(CodeStyleSettings settings) {
    if (!isModified(settings)) return;
    ScalaCodeStyleSettings scalaSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);

    scalaSettings.MULTILINE_STRING_SUPORT = supportLevelChooser.getSelectedIndex();
    scalaSettings.MARGIN_CHAR = marginCharTextField.getText().charAt(0);
    scalaSettings.MULTI_LINE_QUOTES_ON_NEW_LINE = openingQuotesOnNewCheckBox.isSelected();
    scalaSettings.KEEP_MULTI_LINE_QUOTES = keepOpeningQuotesCheckBox.isSelected();
    scalaSettings.MULTI_LINE_STRING_MARGIN_INDENT = (Integer) marginIndentSpinner.getValue();
    scalaSettings.PROCESS_MARGIN_ON_COPY_PASTE = processMarginCheckBox.isSelected();
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    ScalaCodeStyleSettings scalaSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);

    if (scalaSettings.MULTILINE_STRING_SUPORT != supportLevelChooser.getSelectedIndex()) return true;
    if (!(scalaSettings.MARGIN_CHAR + "").equals(marginCharTextField.getText())) return true;
    if (scalaSettings.MULTI_LINE_QUOTES_ON_NEW_LINE != openingQuotesOnNewCheckBox.isSelected()) return true;
    if (scalaSettings.KEEP_MULTI_LINE_QUOTES != keepOpeningQuotesCheckBox.isSelected()) return true;
    if (scalaSettings.MULTI_LINE_STRING_MARGIN_INDENT != (Integer) marginIndentSpinner.getValue()) return true;
    if (scalaSettings.PROCESS_MARGIN_ON_COPY_PASTE != processMarginCheckBox.isSelected()) return true;

    return false;
  }

  @Override
  public JComponent getPanel() {
    return panel1;
  }

  @Override
  protected void resetImpl(CodeStyleSettings settings) {
    ScalaCodeStyleSettings scalaSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);
    setSettings(scalaSettings);
  }

  private void setSettings(ScalaCodeStyleSettings scalaSettings) {
    supportLevelChooser.setSelectedIndex(scalaSettings.MULTILINE_STRING_SUPORT);
    marginCharTextField.setText(scalaSettings.MARGIN_CHAR + "");
    openingQuotesOnNewCheckBox.setSelected(scalaSettings.MULTI_LINE_QUOTES_ON_NEW_LINE);
    keepOpeningQuotesCheckBox.setSelected(scalaSettings.KEEP_MULTI_LINE_QUOTES);
    marginIndentSpinner.setValue(scalaSettings.MULTI_LINE_STRING_MARGIN_INDENT);
    processMarginCheckBox.setSelected(scalaSettings.PROCESS_MARGIN_ON_COPY_PASTE);
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
    panel1.setLayout(new GridLayoutManager(7, 3, new Insets(0, 0, 0, 0), -1, -1));
    panel1.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10), null));
    supportLevelLabel = new JLabel();
    this.$$$loadLabelText$$$(supportLevelLabel, ResourceBundle.getBundle("org/jetbrains/plugins/scala/ScalaBundle").getString("multi.line.string.support.option"));
    panel1.add(supportLevelLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    supportLevelChooser = new JComboBox();
    final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
    defaultComboBoxModel1.addElement("None");
    defaultComboBoxModel1.addElement("Closing quotes on new line");
    defaultComboBoxModel1.addElement("Insert margin char");
    supportLevelChooser.setModel(defaultComboBoxModel1);
    panel1.add(supportLevelChooser, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    panel1.add(spacer1, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    marginCharLabel = new JLabel();
    this.$$$loadLabelText$$$(marginCharLabel, ResourceBundle.getBundle("org/jetbrains/plugins/scala/ScalaBundle").getString("multi.line.string.support.margin.char.label"));
    panel1.add(marginCharLabel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    marginCharTextField = new JTextField();
    marginCharTextField.setText("|");
    panel1.add(marginCharTextField, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    final JLabel label1 = new JLabel();
    this.$$$loadLabelText$$$(label1, ResourceBundle.getBundle("org/jetbrains/plugins/scala/ScalaBundle").getString("multi.line.string.margin.char.indent"));
    panel1.add(label1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    keepOpeningQuotesCheckBox = new JCheckBox();
    this.$$$loadButtonText$$$(keepOpeningQuotesCheckBox, ResourceBundle.getBundle("org/jetbrains/plugins/scala/ScalaBundle").getString("multi.line.string.keep.opening.quotes"));
    panel1.add(keepOpeningQuotesCheckBox, new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer2 = new Spacer();
    panel1.add(spacer2, new GridConstraints(6, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    marginIndentSpinner = new JSpinner();
    panel1.add(marginIndentSpinner, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    openingQuotesOnNewCheckBox = new JCheckBox();
    this.$$$loadButtonText$$$(openingQuotesOnNewCheckBox, ResourceBundle.getBundle("org/jetbrains/plugins/scala/ScalaBundle").getString("multi.line.string.opening.quotes.on.new.line"));
    panel1.add(openingQuotesOnNewCheckBox, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    processMarginCheckBox = new JCheckBox();
    this.$$$loadButtonText$$$(processMarginCheckBox, ResourceBundle.getBundle("org/jetbrains/plugins/scala/ScalaBundle").getString("multi.line.string.process.margin.on.copy.paste"));
    panel1.add(processMarginCheckBox, new GridConstraints(5, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
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
