package org.jetbrains.plugins.scala.lang.formatting.settings;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.highlighter.ScalaEditorHighlighter;

import javax.swing.*;

/**
 * User: Dmitry.Naydanov
 * Date: 09.07.14.
 */
public class OtherCodeStylePanel extends CodeStyleAbstractPanel {
  private JCheckBox enforceProcedureSyntaxForCheckBox;
  private JPanel contentPanel;
  private JCheckBox addAfterCaseCheckBox;
  private JCheckBox replaceWithUnicodeSymbolCheckBox;
  private JCheckBox replaceWithUnicodeSymbolCheckBox1;
  private JCheckBox replaceInForGeneratorCheckBox;

  protected OtherCodeStylePanel(@NotNull CodeStyleSettings settings) {
    super(settings);
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
    return ScalaFileType.SCALA_FILE_TYPE;
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
    scalaCodeStyleSettings.ENFORCE_PROCEDURE_SYNTAX_FOR_UNIT = enforceProcedureSyntaxForCheckBox.isSelected();
    scalaCodeStyleSettings.ADD_ARROW_AFTER_INDENT_CASE = addAfterCaseCheckBox.isSelected();
    scalaCodeStyleSettings.REPLACE_CASE_ARROW_WITH_UNICODE_CHAR = replaceWithUnicodeSymbolCheckBox.isSelected();
    scalaCodeStyleSettings.REPLACE_MAP_ARROW_WITH_UNICODE_CHAR = replaceWithUnicodeSymbolCheckBox1.isSelected();
    scalaCodeStyleSettings.REPLACE_FOR_GENERATOR_ARROW_WITH_UNICODE_CHAR = replaceInForGeneratorCheckBox.isSelected();
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    ScalaCodeStyleSettings scalaCodeStyleSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);

    if (scalaCodeStyleSettings.ENFORCE_PROCEDURE_SYNTAX_FOR_UNIT != enforceProcedureSyntaxForCheckBox.isSelected()) return true;
    if (scalaCodeStyleSettings.ADD_ARROW_AFTER_INDENT_CASE != addAfterCaseCheckBox.isSelected()) return true;
    if (scalaCodeStyleSettings.REPLACE_CASE_ARROW_WITH_UNICODE_CHAR != replaceWithUnicodeSymbolCheckBox.isSelected()) return true;
    if (scalaCodeStyleSettings.REPLACE_MAP_ARROW_WITH_UNICODE_CHAR != replaceWithUnicodeSymbolCheckBox1.isSelected()) return true;
    if (scalaCodeStyleSettings.REPLACE_FOR_GENERATOR_ARROW_WITH_UNICODE_CHAR != replaceInForGeneratorCheckBox.isSelected()) return true;

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
    enforceProcedureSyntaxForCheckBox.setSelected(scalaCodeStyleSettings.ENFORCE_PROCEDURE_SYNTAX_FOR_UNIT);
    addAfterCaseCheckBox.setSelected(scalaCodeStyleSettings.ADD_ARROW_AFTER_INDENT_CASE);
    replaceWithUnicodeSymbolCheckBox.setSelected(scalaCodeStyleSettings.REPLACE_CASE_ARROW_WITH_UNICODE_CHAR);
    replaceWithUnicodeSymbolCheckBox1.setSelected(scalaCodeStyleSettings.REPLACE_MAP_ARROW_WITH_UNICODE_CHAR);
    replaceInForGeneratorCheckBox.setSelected(scalaCodeStyleSettings.REPLACE_FOR_GENERATOR_ARROW_WITH_UNICODE_CHAR);
  }
}
