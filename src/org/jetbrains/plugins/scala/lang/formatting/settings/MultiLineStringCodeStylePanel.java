package org.jetbrains.plugins.scala.lang.formatting.settings;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.highlighter.ScalaEditorHighlighter;

import javax.swing.*;
import java.awt.event.*;

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
  protected void prepareForReformat(PsiFile psiFile) { }

  @Override
  protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
    return new ScalaEditorHighlighter(null, null, scheme);
  }

  @NotNull
  @Override
  protected FileType getFileType() {
    return ScalaFileType.SCALA_FILE_TYPE;
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
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    ScalaCodeStyleSettings scalaSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);

    if (scalaSettings.MULTILINE_STRING_SUPORT != supportLevelChooser.getSelectedIndex()) return true;
    if (!(scalaSettings.MARGIN_CHAR + "").equals(marginCharTextField.getText())) return true;
    if (scalaSettings.MULTI_LINE_QUOTES_ON_NEW_LINE != openingQuotesOnNewCheckBox.isSelected()) return true;
    if (scalaSettings.KEEP_MULTI_LINE_QUOTES != keepOpeningQuotesCheckBox.isSelected()) return true;
    if (scalaSettings.MULTI_LINE_STRING_MARGIN_INDENT != (Integer) marginIndentSpinner.getValue()) return true;

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
  }

  private static boolean isInvalidInput(@NotNull String text, String selectedText, KeyEvent e) {
    return text.length() > 0 && !e.isActionKey() && e.getKeyChar() != KeyEvent.VK_BACK_SPACE &&
        e.getKeyChar() != KeyEvent.VK_DELETE && !text.equals(selectedText);
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
