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

  protected OtherCodeStylePanel(@NotNull CodeStyleSettings settings) {
    super(settings);

    ScalaCodeStyleSettings scalaCodeStyleSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);
    enforceProcedureSyntaxForCheckBox.setSelected(scalaCodeStyleSettings.ENFORCE_PROCEDURE_SYNTAX_FOR_UNIT);
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
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    ScalaCodeStyleSettings scalaCodeStyleSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);

    if (scalaCodeStyleSettings.ENFORCE_PROCEDURE_SYNTAX_FOR_UNIT != enforceProcedureSyntaxForCheckBox.isSelected()) return true;

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
  }
}
