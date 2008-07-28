package org.jetbrains.plugins.scala.lang.formatting.settings;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;

import javax.swing.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.highlighter.ScalaEditorHighlighter;
import org.jetbrains.plugins.scala.ScalaFileType;

/**
 * User: Alexander Podkhalyuzin
 * Date: 28.07.2008
 */
public class ScalaCodeStylePanel extends CodeStyleAbstractPanel {
  private JPanel myPanel;
  private JCheckBox experimentBox;

  public ScalaCodeStylePanel(CodeStyleSettings settings) {
    super(settings);
    ScalaCodeStyleSettings scalaSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);
    setSettings(scalaSettings);
  }

  protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
    return new ScalaEditorHighlighter(null, null, scheme);
  }

  protected int getRightMargin() {
    return 0;
  }

  protected void prepareForReformat(PsiFile psiFile) {
  }

  @NotNull
  protected FileType getFileType() {
    return ScalaFileType.SCALA_FILE_TYPE;
  }

  protected String getPreviewText() {
    return "package preview.file";
  }

  public void apply(CodeStyleSettings settings) {
    ScalaCodeStyleSettings scalaSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);
    scalaSettings.EXPERIMENT = getBoxValue(experimentBox);
  }

  private boolean getBoxValue(JCheckBox checkBox) {
    return checkBox.isSelected();
  }

  public boolean isModified(CodeStyleSettings settings) {
    ScalaCodeStyleSettings scalaSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);
    if (scalaSettings.EXPERIMENT != getBoxValue(experimentBox)) return true;
    return false;
  }

  public JComponent getPanel() {
    return myPanel;
  }

  protected void resetImpl(CodeStyleSettings settings) {
    ScalaCodeStyleSettings scalaSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);
    setSettings(scalaSettings);
  }

  private void setSettings(ScalaCodeStyleSettings settings) {
    setValue(experimentBox, settings.EXPERIMENT);
  }

  private static void setValue(@NotNull final JComboBox box, final int value) {
    box.setSelectedIndex(value);
  }

  private static void setValue(@NotNull final JCheckBox box, final boolean value) {
    box.setSelected(value);
  }
}
