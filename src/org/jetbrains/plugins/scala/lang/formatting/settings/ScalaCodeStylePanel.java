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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.07.2008
 */
public class ScalaCodeStylePanel extends CodeStyleAbstractPanel {
  private JPanel myPanel;
  private JTabbedPane tabbedPane;
  private JPanel spacingPanel;
  private JCheckBox beforeCommaBox;
  private JCheckBox afterCommaBox;
  private JCheckBox beforeColonBox;
  private JCheckBox afterColonBox;
  private JCheckBox beforeSemicolonBox;
  private JCheckBox afterSemicolonBox;
  private JCheckBox beforeIfBox;
  private JCheckBox beforeForBox;
  private JCheckBox beforeMethodBox;
  private JCheckBox beforeMethodCallBox;
  private JCheckBox withinForBox;
  private JCheckBox withinIfBox;
  private JCheckBox withinWhileBox;
  private JCheckBox withinMethodBox;
  private JCheckBox withinMethodCallBox;
  private JCheckBox withinBox;
  private JCheckBox withinBracketsBox;
  private JCheckBox beforeClassLBraceBox;
  private JCheckBox beforeMethodLBraceBox;
  private JCheckBox beforeIfLBraceBox;
  private JCheckBox beforeWhileLBraceBox;
  private JCheckBox beforeDoLBraceBox;
  private JCheckBox beforeForLBraceBox;
  private JCheckBox beforeMatchLBrace;
  private JCheckBox beforeTryLBraceBox;
  private JCheckBox beforeCatchLBraceBox;
  private JCheckBox beforeFinallyLBraceBox;
  private JCheckBox beforeElseLBraceBox;
  private JCheckBox beforeWhileBox;
  private JPanel blackLines;
  private JPanel alignmentTab;
  private JCheckBox alignListOfIdentifiersCheckBox;
  private JCheckBox alignExtendsListCheckBox;
  private JCheckBox alignForStatementCheckBox;
  private JCheckBox elseOnNewLineCheckBox;
  private JCheckBox finallyOnNewLineCheckBox;
  private JCheckBox catchOnNewLineCheckBox;
  private JCheckBox whileOnNewLineCheckBox;
  private JCheckBox specialElseIfTreatmentCheckBox;
  private JCheckBox indentCaseFromMatchCheckBox;
  private JSpinner classCountSpinner;
  private JCheckBox addUnambiguousImportsOnCheckBox;
  private JCheckBox alignIfElseStatementCheckBox;
  private JCheckBox donTUseContinuationCheckBox;
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
  private JCheckBox beforeMethodBracesCallCheckBox;
  private JCheckBox closureParametersOnNewCheckBox;
  private JCheckBox enableConversionOnCopyCheckBox;
  private JCheckBox donTShowDialogCheckBox;
  private JCheckBox addFullQualifiedImportsCheckBox;
  private JCheckBox enableExpetimentalErrorHighlightingCheckBox;
  private JCheckBox showImplicitConversionsInCheckBox;

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
    scalaSettings.SPACE_AFTER_COLON = afterColonBox.isSelected();
    scalaSettings.SPACE_AFTER_COMMA = afterCommaBox.isSelected();
    scalaSettings.SPACE_AFTER_SEMICOLON = afterSemicolonBox.isSelected();
    scalaSettings.SPACE_BEFORE_CATCH_LBRACE = beforeCatchLBraceBox.isSelected();
    scalaSettings.SPACE_BEFORE_CLASS_LBRACE = beforeClassLBraceBox.isSelected();
    scalaSettings.SPACE_BEFORE_FINALLY_LBRACE = beforeFinallyLBraceBox.isSelected();
    scalaSettings.SPACE_BEFORE_COLON = beforeColonBox.isSelected();
    scalaSettings.SPACE_BEFORE_COMMA = beforeCommaBox.isSelected();
    scalaSettings.SPACE_BEFORE_DO_LBRACE = beforeDoLBraceBox.isSelected();
    scalaSettings.SPACE_BEFORE_FOR_LBRACE = beforeForLBraceBox.isSelected();
    scalaSettings.SPACE_BEFORE_FOR_PARENTHESES = beforeForBox.isSelected();
    scalaSettings.SPACE_BEFORE_IF_LBRACE = beforeIfLBraceBox.isSelected();
    scalaSettings.SPACE_BEFORE_ELSE_LBRACE = beforeElseLBraceBox.isSelected();
    scalaSettings.SPACE_BEFORE_IF_PARENTHESES = beforeIfBox.isSelected();
    scalaSettings.SPACE_BEFORE_WHILE_PARENTHESES = beforeWhileBox.isSelected();
    scalaSettings.SPACE_BEFORE_MATCH_LBRACE = beforeMatchLBrace.isSelected();
    scalaSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES = beforeMethodCallBox.isSelected();
    scalaSettings.SPACE_BEFORE_BRACE_METHOD_CALL = beforeMethodBracesCallCheckBox.isSelected();
    scalaSettings.SPACE_BEFORE_METHOD_LBRACE = beforeMethodLBraceBox.isSelected();
    scalaSettings.SPACE_BEFORE_METHOD_PARENTHESES = beforeMethodBox.isSelected();
    scalaSettings.SPACE_BEFORE_SEMICOLON = beforeSemicolonBox.isSelected();
    scalaSettings.SPACE_BEFORE_TRY_LBRACE = beforeTryLBraceBox.isSelected();
    scalaSettings.SPACE_BEFORE_WHILE_LBRACE = beforeWhileLBraceBox.isSelected();
    scalaSettings.SPACE_WITHIN_BRACKETS = withinBracketsBox.isSelected();
    scalaSettings.SPACE_WITHIN_FOR_PARENTHESES = withinForBox.isSelected();
    scalaSettings.SPACE_WITHIN_IF_PARENTHESES = withinIfBox.isSelected();
    scalaSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = withinMethodCallBox.isSelected();
    scalaSettings.SPACE_WITHIN_METHOD_PARENTHESES = withinMethodBox.isSelected();
    scalaSettings.SPACE_WITHIN_PARENTHESES = withinBox.isSelected();
    scalaSettings.SPACE_WITHIN_WHILE_PARENTHESES = withinWhileBox.isSelected();
    scalaSettings.NOT_CONTINUATION_INDENT_FOR_PARAMS = donTUseContinuationCheckBox.isSelected();

    scalaSettings.ALIGN_MULTILINE_FOR = alignForStatementCheckBox.isSelected();
    scalaSettings.ALIGN_IF_ELSE = alignIfElseStatementCheckBox.isSelected();

    scalaSettings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = addUnambiguousImportsOnCheckBox.isSelected();
    scalaSettings.ADD_IMPORT_MOST_CLOSE_TO_REFERENCE = addImportStatementInCheckBox.isSelected();
    scalaSettings.ADD_FULL_QUALIFIED_IMPORTS = addFullQualifiedImportsCheckBox.isSelected();
    scalaSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = (Integer) classCountSpinner.getValue();

    scalaSettings.SEARCH_ALL_SYMBOLS = searchAllSymbolsIncludeCheckBox.isSelected();
    scalaSettings.ENABLE_JAVA_TO_SCALA_CONVERSION = enableConversionOnCopyCheckBox.isSelected();
    scalaSettings.DONT_SHOW_CONVERSION_DIALOG = donTShowDialogCheckBox.isSelected();

    scalaSettings.FOLD_FILE_HEADER = fileHeaderCheckBox.isSelected();
    scalaSettings.FOLD_IMPORT_STATEMETS = importStatementsCheckBox.isSelected();
    scalaSettings.FOLD_SCALADOC = scaladocCommentsCheckBox.isSelected();
    scalaSettings.FOLD_BLOCK = blockExpressionsCheckBox.isSelected();
    scalaSettings.FOLD_TEMPLATE_BODIES = templateBodiesCheckBox.isSelected();
    scalaSettings.FOLD_SHELL_COMMENTS = shellCommentsInScriptCheckBox.isSelected();
    scalaSettings.FOLD_PACKAGINGS = packagingsCheckBox.isSelected();
    scalaSettings.FOLD_IMPORT_IN_HEADER = headerImportStatementsCheckBox.isSelected();
    scalaSettings.FOLD_BLOCK_COMMENTS = blockCommentsCheckBox.isSelected();
    scalaSettings.PLACE_CLOSURE_PARAMETERS_ON_NEW_LINE = closureParametersOnNewCheckBox.isSelected();

    scalaSettings.ENABLE_ERROR_HIGHLIGHTING = enableExpetimentalErrorHighlightingCheckBox.isSelected();
    scalaSettings.SHOW_IMPLICIT_CONVERSIONS = showImplicitConversionsInCheckBox.isSelected();
  }

  @SuppressWarnings({"ConstantConditions", "RedundantIfStatement"})
  public boolean isModified(CodeStyleSettings settings) {
    ScalaCodeStyleSettings scalaSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);
    if (scalaSettings.SPACE_AFTER_COLON != afterColonBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_AFTER_COMMA != afterCommaBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_AFTER_SEMICOLON != afterSemicolonBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_CATCH_LBRACE != beforeCatchLBraceBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_CLASS_LBRACE != beforeClassLBraceBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_FINALLY_LBRACE != beforeFinallyLBraceBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_COLON != beforeColonBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_COMMA != beforeCommaBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_DO_LBRACE != beforeDoLBraceBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_FOR_LBRACE != beforeForLBraceBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_FOR_PARENTHESES != beforeForBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_IF_LBRACE != beforeIfLBraceBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_ELSE_LBRACE != beforeElseLBraceBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_IF_PARENTHESES != beforeIfBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_WHILE_PARENTHESES != beforeWhileBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_MATCH_LBRACE != beforeMatchLBrace.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES != beforeMethodCallBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_BRACE_METHOD_CALL != beforeMethodBracesCallCheckBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_METHOD_LBRACE != beforeMethodLBraceBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_METHOD_PARENTHESES != beforeMethodBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_SEMICOLON != beforeSemicolonBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_TRY_LBRACE != beforeTryLBraceBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_WHILE_LBRACE != beforeWhileLBraceBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_WITHIN_BRACKETS != withinBracketsBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_WITHIN_FOR_PARENTHESES != withinForBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_WITHIN_IF_PARENTHESES != withinIfBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES != withinMethodCallBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_WITHIN_METHOD_PARENTHESES != withinMethodBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_WITHIN_PARENTHESES != withinBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_WITHIN_WHILE_PARENTHESES != withinWhileBox.isSelected()) {
      return true;
    }
    if (scalaSettings.NOT_CONTINUATION_INDENT_FOR_PARAMS != donTUseContinuationCheckBox.isSelected()) {
      return true;
    }

    if (scalaSettings.SHOW_IMPLICIT_CONVERSIONS != showImplicitConversionsInCheckBox.isSelected()) {
      return true;
    }
    if (scalaSettings.ENABLE_ERROR_HIGHLIGHTING != enableExpetimentalErrorHighlightingCheckBox.isSelected()) {
      return true;
    }

    if (scalaSettings.ALIGN_MULTILINE_FOR != alignForStatementCheckBox.isSelected()) return true;
    if (scalaSettings.ALIGN_IF_ELSE != alignIfElseStatementCheckBox.isSelected()) return true;

    if (scalaSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND != (Integer) classCountSpinner.getValue()) return true;
    if (scalaSettings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY != addUnambiguousImportsOnCheckBox.isSelected()) return true;
    if (scalaSettings.ADD_IMPORT_MOST_CLOSE_TO_REFERENCE != addImportStatementInCheckBox.isSelected()) return true;
    if (scalaSettings.ADD_FULL_QUALIFIED_IMPORTS != addFullQualifiedImportsCheckBox.isSelected()) return true;

    if (scalaSettings.SEARCH_ALL_SYMBOLS != searchAllSymbolsIncludeCheckBox.isSelected()) return true;
    if (scalaSettings.ENABLE_JAVA_TO_SCALA_CONVERSION != enableConversionOnCopyCheckBox.isSelected()) return true;
    if (scalaSettings.DONT_SHOW_CONVERSION_DIALOG != donTShowDialogCheckBox.isSelected()) return true;

    if (scalaSettings.FOLD_BLOCK != blockExpressionsCheckBox.isSelected()) return true;
    if (scalaSettings.FOLD_BLOCK_COMMENTS != blockCommentsCheckBox.isSelected()) return true;
    if (scalaSettings.FOLD_FILE_HEADER != fileHeaderCheckBox.isSelected()) return true;
    if (scalaSettings.FOLD_IMPORT_IN_HEADER != headerImportStatementsCheckBox.isSelected()) return true;
    if (scalaSettings.FOLD_IMPORT_STATEMETS != importStatementsCheckBox.isSelected()) return true;
    if (scalaSettings.FOLD_PACKAGINGS != packagingsCheckBox.isSelected()) return true;
    if (scalaSettings.FOLD_SCALADOC != scaladocCommentsCheckBox.isSelected()) return true;
    if (scalaSettings.FOLD_SHELL_COMMENTS != shellCommentsInScriptCheckBox.isSelected()) return true;
    if (scalaSettings.FOLD_TEMPLATE_BODIES != templateBodiesCheckBox.isSelected()) return true;
    if (scalaSettings.PLACE_CLOSURE_PARAMETERS_ON_NEW_LINE != closureParametersOnNewCheckBox.isSelected()) return true;

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
    //set spacing settings
    setValue(afterColonBox, settings.SPACE_AFTER_COLON);
    setValue(beforeColonBox, settings.SPACE_BEFORE_COLON);
    setValue(afterCommaBox, settings.SPACE_AFTER_COMMA);
    setValue(beforeCommaBox, settings.SPACE_BEFORE_COMMA);
    setValue(afterSemicolonBox, settings.SPACE_AFTER_SEMICOLON);
    setValue(beforeSemicolonBox, settings.SPACE_BEFORE_SEMICOLON);
    setValue(beforeIfBox, settings.SPACE_BEFORE_IF_PARENTHESES);
    setValue(beforeWhileBox, settings.SPACE_BEFORE_WHILE_PARENTHESES);
    setValue(beforeForBox, settings.SPACE_BEFORE_FOR_PARENTHESES);
    setValue(beforeMethodBox, settings.SPACE_BEFORE_METHOD_PARENTHESES);
    setValue(beforeMethodCallBox, settings.SPACE_BEFORE_METHOD_CALL_PARENTHESES);
    setValue(beforeMethodBracesCallCheckBox, settings.SPACE_BEFORE_BRACE_METHOD_CALL);
    setValue(withinForBox, settings.SPACE_WITHIN_FOR_PARENTHESES);
    setValue(withinIfBox, settings.SPACE_WITHIN_IF_PARENTHESES);
    setValue(withinWhileBox, settings.SPACE_WITHIN_WHILE_PARENTHESES);
    setValue(withinBox, settings.SPACE_WITHIN_PARENTHESES);
    setValue(withinMethodBox, settings.SPACE_WITHIN_METHOD_PARENTHESES);
    setValue(withinMethodCallBox, settings.SPACE_WITHIN_METHOD_CALL_PARENTHESES);
    setValue(withinBracketsBox, settings.SPACE_WITHIN_BRACKETS);
    setValue(beforeClassLBraceBox, settings.SPACE_BEFORE_CLASS_LBRACE);
    setValue(beforeMethodLBraceBox, settings.SPACE_BEFORE_METHOD_LBRACE);
    setValue(beforeIfLBraceBox, settings.SPACE_BEFORE_IF_LBRACE);
    setValue(beforeElseLBraceBox, settings.SPACE_BEFORE_IF_LBRACE);
    setValue(beforeWhileLBraceBox, settings.SPACE_BEFORE_WHILE_LBRACE);
    setValue(beforeDoLBraceBox, settings.SPACE_BEFORE_DO_LBRACE);
    setValue(beforeForLBraceBox, settings.SPACE_BEFORE_FOR_LBRACE);
    setValue(beforeMatchLBrace, settings.SPACE_BEFORE_MATCH_LBRACE);
    setValue(beforeTryLBraceBox, settings.SPACE_BEFORE_TRY_LBRACE);
    setValue(beforeCatchLBraceBox, settings.SPACE_BEFORE_CATCH_LBRACE);
    setValue(beforeFinallyLBraceBox, settings.SPACE_BEFORE_FINALLY_LBRACE);
    setValue(donTUseContinuationCheckBox, settings.NOT_CONTINUATION_INDENT_FOR_PARAMS);

    setValue(alignForStatementCheckBox, settings.ALIGN_MULTILINE_FOR);
    setValue(alignIfElseStatementCheckBox, settings.ALIGN_IF_ELSE);

    setValue(addUnambiguousImportsOnCheckBox, settings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY);
    setValue(addImportStatementInCheckBox, settings.ADD_IMPORT_MOST_CLOSE_TO_REFERENCE);
    setValue(addFullQualifiedImportsCheckBox, settings.ADD_FULL_QUALIFIED_IMPORTS);
    setValue(classCountSpinner, settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND);

    setValue(searchAllSymbolsIncludeCheckBox, settings.SEARCH_ALL_SYMBOLS);
    setValue(enableConversionOnCopyCheckBox, settings.ENABLE_JAVA_TO_SCALA_CONVERSION);
    setValue(donTShowDialogCheckBox, settings.DONT_SHOW_CONVERSION_DIALOG);

    setValue(blockExpressionsCheckBox, settings.FOLD_BLOCK);
    setValue(blockCommentsCheckBox, settings.FOLD_BLOCK_COMMENTS);
    setValue(fileHeaderCheckBox, settings.FOLD_FILE_HEADER);
    setValue(headerImportStatementsCheckBox, settings.FOLD_IMPORT_IN_HEADER);
    setValue(importStatementsCheckBox, settings.FOLD_IMPORT_STATEMETS);
    setValue(packagingsCheckBox, settings.FOLD_PACKAGINGS);
    setValue(scaladocCommentsCheckBox, settings.FOLD_SCALADOC);
    setValue(shellCommentsInScriptCheckBox, settings.FOLD_SHELL_COMMENTS);
    setValue(templateBodiesCheckBox, settings.FOLD_TEMPLATE_BODIES);
    setValue(closureParametersOnNewCheckBox, settings.PLACE_CLOSURE_PARAMETERS_ON_NEW_LINE);

    setValue(showImplicitConversionsInCheckBox, settings.SHOW_IMPLICIT_CONVERSIONS);
    setValue(enableExpetimentalErrorHighlightingCheckBox, settings.ENABLE_ERROR_HIGHLIGHTING);
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
