package org.jetbrains.plugins.scala.lang.formatting.settings;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.ScalaLanguage;
import org.jetbrains.plugins.scala.highlighter.ScalaEditorHighlighter;

import javax.swing.*;
import java.awt.*;

/**
 * User: Dmitry.Naydanov
 * Date: 09.07.14.
 */
public class OtherCodeStylePanel extends CodeStyleAbstractPanel {
  private JCheckBox enforceFunctionalSyntaxForCheckBox;
  private JPanel contentPanel;
  private JCheckBox replaceWithUnicodeSymbolCheckBox;
  private JCheckBox replaceWithUnicodeSymbolCheckBox1;
  private JCheckBox replaceInForGeneratorCheckBox;
  private JCheckBox replaceLambdaWithGreekLetter;
  private JCheckBox lineCommentAtFirstColumnCheckBox;
  private JCheckBox alternateIndentationForParamsCheckBox;
  private JSpinner alternateIndentationForParamsSpinner;
  private JPanel alternateParamIndentPanel;
  private JLabel spacesLabel;

  protected OtherCodeStylePanel(@NotNull CodeStyleSettings settings) {
    super(settings);
    alternateIndentationForParamsSpinner.setModel(new SpinnerNumberModel(4, 1, null, 1));
    resetImpl(settings);
  }

  @Override
  protected String getTabTitle() {
    return "Other";
  }

  @Override
  protected int getRightMargin() {
    return 0;
  }

  @Nullable
  @Override
  protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
    return new ScalaEditorHighlighter(null, null, scheme);
  }

  @NotNull
  @Override
  protected FileType getFileType() {
      return ScalaFileType.INSTANCE;
  }

  @Nullable
  @Override
  protected String getPreviewText() {
    return null;
  }

  @Override
  public void apply(CodeStyleSettings settings) throws ConfigurationException {
    if (!isModified(settings)) return;

    ScalaCodeStyleSettings scalaCodeStyleSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);
      CommonCodeStyleSettings commonCodeStyleSettings = settings.getCommonSettings(ScalaLanguage.INSTANCE);
    scalaCodeStyleSettings.ENFORCE_FUNCTIONAL_SYNTAX_FOR_UNIT = enforceFunctionalSyntaxForCheckBox.isSelected();
    scalaCodeStyleSettings.REPLACE_CASE_ARROW_WITH_UNICODE_CHAR = replaceWithUnicodeSymbolCheckBox.isSelected();
    scalaCodeStyleSettings.REPLACE_MAP_ARROW_WITH_UNICODE_CHAR = replaceWithUnicodeSymbolCheckBox1.isSelected();
    scalaCodeStyleSettings.REPLACE_FOR_GENERATOR_ARROW_WITH_UNICODE_CHAR = replaceInForGeneratorCheckBox.isSelected();
    scalaCodeStyleSettings.REPLACE_LAMBDA_WITH_GREEK_LETTER = replaceLambdaWithGreekLetter.isSelected();
    commonCodeStyleSettings.LINE_COMMENT_AT_FIRST_COLUMN = lineCommentAtFirstColumnCheckBox.isSelected();
    scalaCodeStyleSettings.USE_ALTERNATE_CONTINUATION_INDENT_FOR_PARAMS = alternateIndentationForParamsCheckBox.isSelected();
    scalaCodeStyleSettings.ALTERNATE_CONTINUATION_INDENT_FOR_PARAMS = (Integer) alternateIndentationForParamsSpinner.getValue();
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    ScalaCodeStyleSettings scalaCodeStyleSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);
      CommonCodeStyleSettings commonCodeStyleSettings = settings.getCommonSettings(ScalaLanguage.INSTANCE);

    if (scalaCodeStyleSettings.ENFORCE_FUNCTIONAL_SYNTAX_FOR_UNIT != enforceFunctionalSyntaxForCheckBox.isSelected())
      return true;
    if (scalaCodeStyleSettings.REPLACE_CASE_ARROW_WITH_UNICODE_CHAR != replaceWithUnicodeSymbolCheckBox.isSelected())
      return true;
    if (scalaCodeStyleSettings.REPLACE_MAP_ARROW_WITH_UNICODE_CHAR != replaceWithUnicodeSymbolCheckBox1.isSelected())
      return true;
    if (scalaCodeStyleSettings.REPLACE_FOR_GENERATOR_ARROW_WITH_UNICODE_CHAR != replaceInForGeneratorCheckBox.isSelected())
      return true;
    if (scalaCodeStyleSettings.REPLACE_LAMBDA_WITH_GREEK_LETTER != replaceLambdaWithGreekLetter.isSelected())
      return true;
    if (commonCodeStyleSettings.LINE_COMMENT_AT_FIRST_COLUMN != lineCommentAtFirstColumnCheckBox.isSelected())
      return true;
    if (scalaCodeStyleSettings.USE_ALTERNATE_CONTINUATION_INDENT_FOR_PARAMS != alternateIndentationForParamsCheckBox.isSelected())
      return true;
    if (scalaCodeStyleSettings.ALTERNATE_CONTINUATION_INDENT_FOR_PARAMS != (Integer) alternateIndentationForParamsSpinner.getValue())
      return true;


    return false;
  }

  @Nullable
  @Override
  public JComponent getPanel() {
    return contentPanel;
  }

  @Override
  protected void resetImpl(CodeStyleSettings settings) {
    ScalaCodeStyleSettings scalaCodeStyleSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);
      CommonCodeStyleSettings commonCodeStyleSettings = settings.getCommonSettings(ScalaLanguage.INSTANCE);
    enforceFunctionalSyntaxForCheckBox.setSelected(scalaCodeStyleSettings.ENFORCE_FUNCTIONAL_SYNTAX_FOR_UNIT);
    replaceWithUnicodeSymbolCheckBox.setSelected(scalaCodeStyleSettings.REPLACE_CASE_ARROW_WITH_UNICODE_CHAR);
    replaceWithUnicodeSymbolCheckBox1.setSelected(scalaCodeStyleSettings.REPLACE_MAP_ARROW_WITH_UNICODE_CHAR);
    replaceInForGeneratorCheckBox.setSelected(scalaCodeStyleSettings.REPLACE_FOR_GENERATOR_ARROW_WITH_UNICODE_CHAR);
    replaceLambdaWithGreekLetter.setSelected(scalaCodeStyleSettings.REPLACE_LAMBDA_WITH_GREEK_LETTER);
    lineCommentAtFirstColumnCheckBox.setSelected(commonCodeStyleSettings.LINE_COMMENT_AT_FIRST_COLUMN);
    alternateIndentationForParamsCheckBox.setSelected(scalaCodeStyleSettings.USE_ALTERNATE_CONTINUATION_INDENT_FOR_PARAMS);
    alternateIndentationForParamsSpinner.setValue(scalaCodeStyleSettings.ALTERNATE_CONTINUATION_INDENT_FOR_PARAMS);
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
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    contentPanel = new JPanel();
    contentPanel.setLayout(new GridLayoutManager(8, 1, new Insets(0, 0, 0, 0), -1, -1));
    panel1.add(contentPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    enforceFunctionalSyntaxForCheckBox = new JCheckBox();
    enforceFunctionalSyntaxForCheckBox.setText("Enforce procedure syntax for methods with Unit return type");
    contentPanel.add(enforceFunctionalSyntaxForCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    contentPanel.add(spacer1, new GridConstraints(7, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    replaceWithUnicodeSymbolCheckBox = new JCheckBox();
    replaceWithUnicodeSymbolCheckBox.setText("Replace '=>' with unicode symbol");
    contentPanel.add(replaceWithUnicodeSymbolCheckBox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    replaceWithUnicodeSymbolCheckBox1 = new JCheckBox();
    replaceWithUnicodeSymbolCheckBox1.setText("Replace '->' with unicode symbol");
    contentPanel.add(replaceWithUnicodeSymbolCheckBox1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    replaceInForGeneratorCheckBox = new JCheckBox();
    replaceInForGeneratorCheckBox.setText("Replace '<-' in \"for\" generator with unicode symbol");
    contentPanel.add(replaceInForGeneratorCheckBox, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    replaceLambdaWithGreekLetter = new JCheckBox();
    replaceLambdaWithGreekLetter.setSelected(false);
    replaceLambdaWithGreekLetter.setText("Kind Projector: Replace 'Lambda' with unicode symbol");
    contentPanel.add(replaceLambdaWithGreekLetter, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    lineCommentAtFirstColumnCheckBox = new JCheckBox();
    lineCommentAtFirstColumnCheckBox.setSelected(false);
    lineCommentAtFirstColumnCheckBox.setText("Line comment on first column");
    contentPanel.add(lineCommentAtFirstColumnCheckBox, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    alternateParamIndentPanel = new JPanel();
    alternateParamIndentPanel.setLayout(new GridLayoutManager(1, 4, new Insets(0, 0, 0, 0), -1, -1));
    contentPanel.add(alternateParamIndentPanel, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    alternateIndentationForParamsCheckBox = new JCheckBox();
    alternateIndentationForParamsCheckBox.setText("Alternate indentation for constructor args and parameter declarations:");
    alternateIndentationForParamsCheckBox.setVerticalAlignment(1);
    alternateParamIndentPanel.add(alternateIndentationForParamsCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    alternateIndentationForParamsSpinner = new JSpinner();
    alternateParamIndentPanel.add(alternateIndentationForParamsSpinner, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, new Dimension(1, -1), new Dimension(2, -1), null, 1, false));
    spacesLabel = new JLabel();
    spacesLabel.setText("spaces");
    spacesLabel.setVerticalAlignment(1);
    alternateParamIndentPanel.add(spacesLabel, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer2 = new Spacer();
    alternateParamIndentPanel.add(spacer2, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
  }
}
