package org.jetbrains.plugins.scala.lang.formatting.settings;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.highlighter.ScalaEditorHighlighter;

import javax.swing.*;

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.07.2008
 */
public class ScalaCodeStylePanel extends CodeStyleAbstractPanel {
  private JPanel myPanel;
  private JTabbedPane tabbedPane;
  private JSpinner classCountSpinner;
  private JCheckBox addUnambiguousImportsOnCheckBox;
  private JCheckBox addImportStatementInCheckBox;
  private JCheckBox searchAllSymbolsIncludeCheckBox;
  private JCheckBox fileHeaderCheckBox;
  private JCheckBox importStatementsCheckBox;
  private JCheckBox scaladocCommentsCheckBox;
  private JCheckBox blockExpressionsCheckBox;
  private JCheckBox templateBodiesCheckBox;
  private JCheckBox shellCommentsInScriptCheckBox;
  private JCheckBox blockCommentsCheckBox;
  private JCheckBox packagingsCheckBox;
  private JCheckBox headerImportStatementsCheckBox;
  private JCheckBox enableConversionOnCopyCheckBox;
  private JCheckBox donTShowDialogCheckBox;
  private JCheckBox addFullQualifiedImportsCheckBox;
  private JCheckBox showImplicitConversionsInCheckBox;
  private JCheckBox typeLamdasCheckBox;
  private JCheckBox myResolveToAllClassesCheckBox;
  private JCheckBox showArgumentsToByNameParametersCheckBox;
  private JCheckBox includeBlockExpressionsExpressionsCheckBox;
  private JCheckBox includeLiteralsCheckBox;
  private JCheckBox treatDocCommentAsBlockComment;

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
    return "";
  }

  public void apply(CodeStyleSettings settings) {
    if (!isModified(settings)) return;
    ScalaCodeStyleSettings scalaSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);

    scalaSettings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = addUnambiguousImportsOnCheckBox.isSelected();
    scalaSettings.ADD_IMPORT_MOST_CLOSE_TO_REFERENCE = addImportStatementInCheckBox.isSelected();
    scalaSettings.ADD_FULL_QUALIFIED_IMPORTS = addFullQualifiedImportsCheckBox.isSelected();
    scalaSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = (Integer) classCountSpinner.getValue();

    scalaSettings.SEARCH_ALL_SYMBOLS = searchAllSymbolsIncludeCheckBox.isSelected();
    scalaSettings.ENABLE_JAVA_TO_SCALA_CONVERSION = enableConversionOnCopyCheckBox.isSelected();
    scalaSettings.DONT_SHOW_CONVERSION_DIALOG = donTShowDialogCheckBox.isSelected();
    scalaSettings.TREAT_DOC_COMMENT_AS_BLOCK_COMMENT = treatDocCommentAsBlockComment.isSelected();

    scalaSettings.FOLD_FILE_HEADER = fileHeaderCheckBox.isSelected();
    scalaSettings.FOLD_IMPORT_STATEMENTS = importStatementsCheckBox.isSelected();
    scalaSettings.FOLD_SCALADOC = scaladocCommentsCheckBox.isSelected();
    scalaSettings.FOLD_BLOCK = blockExpressionsCheckBox.isSelected();
    scalaSettings.FOLD_TEMPLATE_BODIES = templateBodiesCheckBox.isSelected();
    scalaSettings.FOLD_TYPE_LAMBDA = typeLamdasCheckBox.isSelected();
    scalaSettings.FOLD_SHELL_COMMENTS = shellCommentsInScriptCheckBox.isSelected();
    scalaSettings.FOLD_PACKAGINGS = packagingsCheckBox.isSelected();
    scalaSettings.FOLD_IMPORT_IN_HEADER = headerImportStatementsCheckBox.isSelected();
    scalaSettings.FOLD_BLOCK_COMMENTS = blockCommentsCheckBox.isSelected();

    scalaSettings.SHOW_IMPLICIT_CONVERSIONS = showImplicitConversionsInCheckBox.isSelected();
    scalaSettings.SHOW_ARGUMENTS_TO_BY_NAME_PARAMETERS = showArgumentsToByNameParametersCheckBox.isSelected();
    scalaSettings.INCLUDE_BLOCK_EXPRESSIONS = includeBlockExpressionsExpressionsCheckBox.isSelected();
    scalaSettings.INCLUDE_LITERALS = includeLiteralsCheckBox.isSelected();

    scalaSettings.IGNORE_PERFORMANCE_TO_FIND_ALL_CLASS_NAMES = myResolveToAllClassesCheckBox.isSelected();
  }

  @SuppressWarnings({"ConstantConditions", "RedundantIfStatement"})
  public boolean isModified(CodeStyleSettings settings) {
    ScalaCodeStyleSettings scalaSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);

    if (scalaSettings.SHOW_IMPLICIT_CONVERSIONS != showImplicitConversionsInCheckBox.isSelected()) {
      return true;
    }

    if (scalaSettings.SHOW_ARGUMENTS_TO_BY_NAME_PARAMETERS != showArgumentsToByNameParametersCheckBox.isSelected()) {
      return true;
    }

    if (scalaSettings.INCLUDE_BLOCK_EXPRESSIONS != includeBlockExpressionsExpressionsCheckBox.isSelected()) {
      return true;
    }

    if (scalaSettings.INCLUDE_LITERALS != includeLiteralsCheckBox.isSelected()) {
      return true;
    }

    if (scalaSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND != (Integer) classCountSpinner.getValue()) return true;
    if (scalaSettings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY != addUnambiguousImportsOnCheckBox.isSelected()) return true;
    if (scalaSettings.ADD_IMPORT_MOST_CLOSE_TO_REFERENCE != addImportStatementInCheckBox.isSelected()) return true;
    if (scalaSettings.ADD_FULL_QUALIFIED_IMPORTS != addFullQualifiedImportsCheckBox.isSelected()) return true;

    if (scalaSettings.SEARCH_ALL_SYMBOLS != searchAllSymbolsIncludeCheckBox.isSelected()) return true;
    if (scalaSettings.ENABLE_JAVA_TO_SCALA_CONVERSION != enableConversionOnCopyCheckBox.isSelected()) return true;
    if (scalaSettings.DONT_SHOW_CONVERSION_DIALOG != donTShowDialogCheckBox.isSelected()) return true;
    if (scalaSettings.TREAT_DOC_COMMENT_AS_BLOCK_COMMENT != treatDocCommentAsBlockComment.isSelected()) return true;

    if (scalaSettings.FOLD_BLOCK != blockExpressionsCheckBox.isSelected()) return true;
    if (scalaSettings.FOLD_BLOCK_COMMENTS != blockCommentsCheckBox.isSelected()) return true;
    if (scalaSettings.FOLD_FILE_HEADER != fileHeaderCheckBox.isSelected()) return true;
    if (scalaSettings.FOLD_IMPORT_IN_HEADER != headerImportStatementsCheckBox.isSelected()) return true;
    if (scalaSettings.FOLD_IMPORT_STATEMENTS != importStatementsCheckBox.isSelected()) return true;
    if (scalaSettings.FOLD_PACKAGINGS != packagingsCheckBox.isSelected()) return true;
    if (scalaSettings.FOLD_SCALADOC != scaladocCommentsCheckBox.isSelected()) return true;
    if (scalaSettings.FOLD_SHELL_COMMENTS != shellCommentsInScriptCheckBox.isSelected()) return true;
    if (scalaSettings.FOLD_TEMPLATE_BODIES != templateBodiesCheckBox.isSelected()) return true;
    if (scalaSettings.FOLD_TYPE_LAMBDA != typeLamdasCheckBox.isSelected()) return true;

    if (scalaSettings.IGNORE_PERFORMANCE_TO_FIND_ALL_CLASS_NAMES != myResolveToAllClassesCheckBox.isSelected())
      return true;

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
    setValue(addUnambiguousImportsOnCheckBox, settings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY);
    setValue(addImportStatementInCheckBox, settings.ADD_IMPORT_MOST_CLOSE_TO_REFERENCE);
    setValue(addFullQualifiedImportsCheckBox, settings.ADD_FULL_QUALIFIED_IMPORTS);
    setValue(classCountSpinner, settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND);

    setValue(searchAllSymbolsIncludeCheckBox, settings.SEARCH_ALL_SYMBOLS);
    setValue(enableConversionOnCopyCheckBox, settings.ENABLE_JAVA_TO_SCALA_CONVERSION);
    setValue(donTShowDialogCheckBox, settings.DONT_SHOW_CONVERSION_DIALOG);
    setValue(treatDocCommentAsBlockComment, settings.TREAT_DOC_COMMENT_AS_BLOCK_COMMENT);

    setValue(blockExpressionsCheckBox, settings.FOLD_BLOCK);
    setValue(blockCommentsCheckBox, settings.FOLD_BLOCK_COMMENTS);
    setValue(fileHeaderCheckBox, settings.FOLD_FILE_HEADER);
    setValue(headerImportStatementsCheckBox, settings.FOLD_IMPORT_IN_HEADER);
    setValue(importStatementsCheckBox, settings.FOLD_IMPORT_STATEMENTS);
    setValue(packagingsCheckBox, settings.FOLD_PACKAGINGS);
    setValue(scaladocCommentsCheckBox, settings.FOLD_SCALADOC);
    setValue(shellCommentsInScriptCheckBox, settings.FOLD_SHELL_COMMENTS);
    setValue(templateBodiesCheckBox, settings.FOLD_TEMPLATE_BODIES);
    setValue(typeLamdasCheckBox, settings.FOLD_TYPE_LAMBDA);

    setValue(showImplicitConversionsInCheckBox, settings.SHOW_IMPLICIT_CONVERSIONS);
    setValue(showArgumentsToByNameParametersCheckBox, settings.SHOW_ARGUMENTS_TO_BY_NAME_PARAMETERS);
    setValue(includeBlockExpressionsExpressionsCheckBox, settings.INCLUDE_BLOCK_EXPRESSIONS);
    setValue(includeLiteralsCheckBox, settings.INCLUDE_LITERALS);

    setValue(myResolveToAllClassesCheckBox, settings.IGNORE_PERFORMANCE_TO_FIND_ALL_CLASS_NAMES);
  }

  private static void setValue(JSpinner spinner, int value) {
    spinner.setValue(value);
  }

  private static void setValue(final JCheckBox box, final boolean value) {
    box.setSelected(value);
  }

  private static void setValue(final JComboBox box, final int value) {
    box.setSelectedIndex(value);
  }
}
