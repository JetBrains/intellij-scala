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
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.07.2008
 */
public class ScalaCodeStylePanel extends CodeStyleAbstractPanel {
  private JPanel myPanel;
  private JTabbedPane tabbedPane;
  private JPanel previewPanel;
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
  private JSpinner keepCodeSpinner;
  private JSpinner keepBeforeSpinner;
  private JCheckBox keepLineBreaksCheckBox;
  private JPanel alignmentTab;
  private JCheckBox alignParametersCheckBox;
  private JCheckBox alignListOfIdentifiersCheckBox;
  private JCheckBox alignBinaryOperationsCheckBox;
  private JCheckBox alignParenthesizedExpressionCheckBox;
  private JCheckBox alignExtendsListCheckBox;
  private JCheckBox alignParametersInCallsCheckBox;
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
  private JSpinner linesAfterLBrace;
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
  private JCheckBox implicitTypesCheckBox;

  private final Object LOCK = new Object();

  final private int PREVIEW_PANEL = 6;

  public ScalaCodeStylePanel(CodeStyleSettings settings) {
    super(settings);
    ScalaCodeStyleSettings scalaSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);
    installPreviewPanel(previewPanel);
    setSettings(scalaSettings);
    tabbedPane.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (tabbedPane.isEnabledAt(PREVIEW_PANEL)) {
          synchronized (LOCK) {
            updatePreview();
          }
        }
      }
    });
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
    return "package preview.file\n\n" +
        "" +
        "import scala.collection.mutable._\n\n" +
        "" +
        "abstract class R[T](x: Int) extends {val y = x} with R1[T] {\n" +
        "  def foo(z: Int): R1 = new R[Int](z);\n\n" +
        "  def default = foo(0)\n\n" +
        "  val x: T\n" +
        "}\n\n" +
        "" +
        "trait R1[T]";
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
    scalaSettings.KEEP_LINE_BREAKS = keepLineBreaksCheckBox.isSelected();
    if ((Integer) keepCodeSpinner.getValue() >= 0) {
      scalaSettings.KEEP_BLANK_LINES_IN_CODE = (Integer) keepCodeSpinner.getValue();
    } else {
      scalaSettings.KEEP_BLANK_LINES_IN_CODE = 0;
      keepCodeSpinner.setValue(0);
    }
    if ((Integer) keepBeforeSpinner.getValue() >= 0) {
      scalaSettings.KEEP_BLANK_LINES_BEFORE_RBRACE = (Integer) keepBeforeSpinner.getValue();
    } else {
      scalaSettings.KEEP_BLANK_LINES_BEFORE_RBRACE = 0;
      keepBeforeSpinner.setValue(0);
    }

    if ((Integer) linesAfterLBrace.getValue() >= 0) {
      scalaSettings.BLANK_LINES_AFTER_LBRACE = (Integer) linesAfterLBrace.getValue();
    } else {
      scalaSettings.BLANK_LINES_AFTER_LBRACE = 0;
      linesAfterLBrace.setValue(0);
    }

    scalaSettings.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = alignListOfIdentifiersCheckBox.isSelected();
    scalaSettings.ALIGN_MULTILINE_BINARY_OPERATION = alignBinaryOperationsCheckBox.isSelected();
    scalaSettings.ALIGN_MULTILINE_EXTENDS_LIST = alignExtendsListCheckBox.isSelected();
    scalaSettings.ALIGN_MULTILINE_FOR = alignForStatementCheckBox.isSelected();
    scalaSettings.ALIGN_MULTILINE_PARAMETERS = alignParametersCheckBox.isSelected();
    scalaSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = alignParametersInCallsCheckBox.isSelected();
    scalaSettings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION = alignParenthesizedExpressionCheckBox.isSelected();
    scalaSettings.ALIGN_IF_ELSE = alignIfElseStatementCheckBox.isSelected();

    scalaSettings.INDENT_CASE_FROM_SWITCH = indentCaseFromMatchCheckBox.isSelected();
    scalaSettings.SPECIAL_ELSE_IF_TREATMENT = specialElseIfTreatmentCheckBox.isSelected();
    scalaSettings.ELSE_ON_NEW_LINE = elseOnNewLineCheckBox.isSelected();
    scalaSettings.CATCH_ON_NEW_LINE = catchOnNewLineCheckBox.isSelected();
    scalaSettings.FINALLY_ON_NEW_LINE = finallyOnNewLineCheckBox.isSelected();
    scalaSettings.WHILE_ON_NEW_LINE = whileOnNewLineCheckBox.isSelected();

    scalaSettings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = addUnambiguousImportsOnCheckBox.isSelected();
    scalaSettings.ADD_IMPORT_MOST_CLOSE_TO_REFERENCE = addImportStatementInCheckBox.isSelected();
    scalaSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = (Integer) classCountSpinner.getValue();

    scalaSettings.SEARCH_ALL_SYMBOLS = searchAllSymbolsIncludeCheckBox.isSelected();

    scalaSettings.FOLD_FILE_HEADER = fileHeaderCheckBox.isSelected();
    scalaSettings.FOLD_IMPORT_STATEMETS = importStatementsCheckBox.isSelected();
    scalaSettings.FOLD_SCALADOC = scaladocCommentsCheckBox.isSelected();
    scalaSettings.FOLD_BLOCK = blockExpressionsCheckBox.isSelected();
    scalaSettings.FOLD_TEMPLATE_BODIES = templateBodiesCheckBox.isSelected();
    scalaSettings.FOLD_SHELL_COMMENTS = shellCommentsInScriptCheckBox.isSelected();
    scalaSettings.FOLD_PACKAGINGS = packagingsCheckBox.isSelected();
    scalaSettings.FOLD_IMPORT_IN_HEADER = headerImportStatementsCheckBox.isSelected();
    scalaSettings.FOLD_BLOCK_COMMENTS = blockCommentsCheckBox.isSelected();
    scalaSettings.CHECK_IMPLICITS = implicitTypesCheckBox.isSelected();
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
    if (scalaSettings.KEEP_BLANK_LINES_BEFORE_RBRACE != (Integer) keepBeforeSpinner.getValue()) return true;
    if (scalaSettings.KEEP_BLANK_LINES_IN_CODE != (Integer) keepCodeSpinner.getValue()) return true;
    if (scalaSettings.BLANK_LINES_AFTER_LBRACE != (Integer) linesAfterLBrace.getValue()) return true;
    if (scalaSettings.KEEP_LINE_BREAKS != keepLineBreaksCheckBox.isSelected()) return true;
    if (scalaSettings.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION != alignListOfIdentifiersCheckBox.isSelected())
      return true;
    if (scalaSettings.ALIGN_MULTILINE_BINARY_OPERATION != alignBinaryOperationsCheckBox.isSelected()) return true;
    if (scalaSettings.ALIGN_MULTILINE_EXTENDS_LIST != alignExtendsListCheckBox.isSelected()) return true;
    if (scalaSettings.ALIGN_MULTILINE_FOR != alignForStatementCheckBox.isSelected()) return true;
    if (scalaSettings.ALIGN_MULTILINE_PARAMETERS != alignParametersCheckBox.isSelected()) return true;
    if (scalaSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS != alignParametersInCallsCheckBox.isSelected()) return true;
    if (scalaSettings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION != alignParenthesizedExpressionCheckBox.isSelected())
      return true;
    if (scalaSettings.ALIGN_IF_ELSE != alignIfElseStatementCheckBox.isSelected()) return true;

    if (scalaSettings.ELSE_ON_NEW_LINE != elseOnNewLineCheckBox.isSelected()) return true;
    if (scalaSettings.WHILE_ON_NEW_LINE != whileOnNewLineCheckBox.isSelected()) return true;
    if (scalaSettings.CATCH_ON_NEW_LINE != catchOnNewLineCheckBox.isSelected()) return true;
    if (scalaSettings.FINALLY_ON_NEW_LINE != finallyOnNewLineCheckBox.isSelected()) return true;
    if (scalaSettings.SPECIAL_ELSE_IF_TREATMENT != specialElseIfTreatmentCheckBox.isSelected()) return true;
    if (scalaSettings.INDENT_CASE_FROM_SWITCH != indentCaseFromMatchCheckBox.isSelected()) return true;
    if (scalaSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND != (Integer) classCountSpinner.getValue()) return true;
    if (scalaSettings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY != addUnambiguousImportsOnCheckBox.isSelected()) return true;
    if (scalaSettings.ADD_IMPORT_MOST_CLOSE_TO_REFERENCE != addImportStatementInCheckBox.isSelected()) return true;

    if (scalaSettings.SEARCH_ALL_SYMBOLS != searchAllSymbolsIncludeCheckBox.isSelected()) return true;
    if (scalaSettings.CHECK_IMPLICITS != implicitTypesCheckBox.isSelected()) return true;

    if (scalaSettings.FOLD_BLOCK != blockExpressionsCheckBox.isSelected()) return true;
    if (scalaSettings.FOLD_BLOCK_COMMENTS != blockCommentsCheckBox.isSelected()) return true;
    if (scalaSettings.FOLD_FILE_HEADER != fileHeaderCheckBox.isSelected()) return true;
    if (scalaSettings.FOLD_IMPORT_IN_HEADER != headerImportStatementsCheckBox.isSelected()) return true;
    if (scalaSettings.FOLD_IMPORT_STATEMETS != importStatementsCheckBox.isSelected()) return true;
    if (scalaSettings.FOLD_PACKAGINGS != packagingsCheckBox.isSelected()) return true;
    if (scalaSettings.FOLD_SCALADOC != scaladocCommentsCheckBox.isSelected()) return true;
    if (scalaSettings.FOLD_SHELL_COMMENTS != shellCommentsInScriptCheckBox.isSelected()) return true;
    if (scalaSettings.FOLD_TEMPLATE_BODIES != templateBodiesCheckBox.isSelected()) return true;
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

    setValue(keepLineBreaksCheckBox, settings.KEEP_LINE_BREAKS);
    setValue(keepBeforeSpinner, settings.KEEP_BLANK_LINES_BEFORE_RBRACE);
    setValue(linesAfterLBrace, settings.BLANK_LINES_AFTER_LBRACE);
    setValue(keepCodeSpinner, settings.KEEP_BLANK_LINES_IN_CODE);

    setValue(alignListOfIdentifiersCheckBox, settings.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION);
    setValue(alignBinaryOperationsCheckBox, settings.ALIGN_MULTILINE_BINARY_OPERATION);
    setValue(alignExtendsListCheckBox, settings.ALIGN_MULTILINE_EXTENDS_LIST);
    setValue(alignForStatementCheckBox, settings.ALIGN_MULTILINE_FOR);
    setValue(alignParametersCheckBox, settings.ALIGN_MULTILINE_PARAMETERS);
    setValue(alignParametersInCallsCheckBox, settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS);
    setValue(alignParenthesizedExpressionCheckBox, settings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION);
    setValue(alignIfElseStatementCheckBox, settings.ALIGN_IF_ELSE);

    setValue(elseOnNewLineCheckBox, settings.ELSE_ON_NEW_LINE);
    setValue(whileOnNewLineCheckBox, settings.WHILE_ON_NEW_LINE);
    setValue(catchOnNewLineCheckBox, settings.CATCH_ON_NEW_LINE);
    setValue(finallyOnNewLineCheckBox, settings.FINALLY_ON_NEW_LINE);
    setValue(specialElseIfTreatmentCheckBox, settings.SPECIAL_ELSE_IF_TREATMENT);
    setValue(indentCaseFromMatchCheckBox, settings.INDENT_CASE_FROM_SWITCH);
    setValue(addUnambiguousImportsOnCheckBox, settings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY);
    setValue(addImportStatementInCheckBox, settings.ADD_IMPORT_MOST_CLOSE_TO_REFERENCE);
    setValue(classCountSpinner, settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND);
    setValue(searchAllSymbolsIncludeCheckBox, settings.SEARCH_ALL_SYMBOLS);
    setValue(implicitTypesCheckBox, settings.CHECK_IMPLICITS);

    setValue(blockExpressionsCheckBox, settings.FOLD_BLOCK);
    setValue(blockCommentsCheckBox, settings.FOLD_BLOCK_COMMENTS);
    setValue(fileHeaderCheckBox, settings.FOLD_FILE_HEADER);
    setValue(headerImportStatementsCheckBox, settings.FOLD_IMPORT_IN_HEADER);
    setValue(importStatementsCheckBox, settings.FOLD_IMPORT_STATEMETS);
    setValue(packagingsCheckBox, settings.FOLD_PACKAGINGS);
    setValue(scaladocCommentsCheckBox, settings.FOLD_SCALADOC);
    setValue(shellCommentsInScriptCheckBox, settings.FOLD_SHELL_COMMENTS);
    setValue(templateBodiesCheckBox, settings.FOLD_TEMPLATE_BODIES);
  }

  private static void setValue(JSpinner spinner, int value) {
    spinner.setValue(value);
  }

  private static void setValue(final JCheckBox box, final boolean value) {
    box.setSelected(value);
  }
}
