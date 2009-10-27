package org.jetbrains.plugins.scala.lang.formatting.settings;

import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;

/**
 * User: Alexander Podkhalyuzin
 * Date: 28.07.2008
 */
public class ScalaCodeStyleSettings extends CustomCodeStyleSettings {

  public static ScalaCodeStyleSettings getInstance(Project project) {
    return CodeStyleSettingsManager.getSettings(project).getCustomSettings(ScalaCodeStyleSettings.class);
  }


  //spcaing settings:
  public boolean SPACE_BEFORE_COMMA = false;
  public boolean SPACE_AFTER_COMMA = true;
  public boolean SPACE_BEFORE_COLON = false;
  public boolean SPACE_AFTER_COLON = true;
  public boolean SPACE_BEFORE_IF_PARENTHESES = true;
  public boolean SPACE_BEFORE_FOR_PARENTHESES = true;
  public boolean SPACE_BEFORE_METHOD_PARENTHESES = false;
  public boolean SPACE_BEFORE_METHOD_CALL_PARENTHESES = false;
  public boolean SPACE_WITHIN_FOR_PARENTHESES = false;
  public boolean SPACE_WITHIN_IF_PARENTHESES = false;
  public boolean SPACE_WITHIN_WHILE_PARENTHESES = false;
  public boolean SPACE_WITHIN_PARENTHESES = false;
  public boolean SPACE_WITHIN_METHOD_PARENTHESES = false;
  public boolean SPACE_WITHIN_METHOD_CALL_PARENTHESES = false;
  public boolean SPACE_BEFORE_BRACE_METHOD_CALL = true;
  public boolean SPACE_WITHIN_BRACKETS = false;
  public boolean SPACE_BEFORE_CLASS_LBRACE = true;
  public boolean SPACE_BEFORE_METHOD_LBRACE = true;
  public boolean SPACE_BEFORE_IF_LBRACE = true;
  public boolean SPACE_BEFORE_WHILE_LBRACE = true;
  public boolean SPACE_BEFORE_DO_LBRACE = true;
  public boolean SPACE_BEFORE_FOR_LBRACE = true;
  public boolean SPACE_BEFORE_MATCH_LBRACE = true;
  public boolean SPACE_BEFORE_TRY_LBRACE = true;
  public boolean SPACE_BEFORE_CATCH_LBRACE = true;
  public boolean SPACE_BEFORE_FINALLY_LBRACE = true;
  public boolean SPACE_BEFORE_WHILE_PARENTHESES = true;
  public boolean SPACE_BEFORE_ELSE_LBRACE = true;
  public boolean SPACE_BEFORE_SEMICOLON = false;
  public boolean SPACE_AFTER_SEMICOLON = true;

  //keep blank lines
  public int KEEP_BLANK_LINES_IN_CODE = 2;
  public int KEEP_BLANK_LINES_BEFORE_RBRACE = 2;
  public int BLANK_LINES_AFTER_LBRACE = 0;
  public boolean KEEP_LINE_BREAKS = true;

  //indents
  public boolean NOT_CONTINUATION_INDENT_FOR_PARAMS = false;

  //Alignments
  public boolean ALIGN_MULTILINE_PARAMETERS = true;
  public boolean ALIGN_MULTILINE_PARAMETERS_IN_CALLS = false;
  public boolean ALIGN_MULTILINE_EXTENDS_LIST = false;
  public boolean ALIGN_MULTILINE_FOR = true;
  public boolean ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION = false;
  public boolean ALIGN_MULTILINE_BINARY_OPERATION = false;
  public boolean ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = false;
  public boolean ALIGN_IF_ELSE = false;

  public boolean PLACE_CLOSURE_PARAMETERS_ON_NEW_LINE = true;
  public boolean ELSE_ON_NEW_LINE = false;
  public boolean WHILE_ON_NEW_LINE = false;
  public boolean CATCH_ON_NEW_LINE = false;
  public boolean FINALLY_ON_NEW_LINE = false;
  public boolean SPECIAL_ELSE_IF_TREATMENT = false;
  public boolean INDENT_CASE_FROM_SWITCH = true;

  public boolean ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = false;
  public int CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 5;
  public boolean ADD_IMPORT_MOST_CLOSE_TO_REFERENCE = false;

  public boolean SEARCH_ALL_SYMBOLS = false;
  public boolean SHOW_FILES_IN_PROJECT_VIEW = false;

  //collapse by default
  public boolean FOLD_FILE_HEADER = true;
  public boolean FOLD_IMPORT_STATEMETS = false;
  public boolean FOLD_SCALADOC = false;
  public boolean FOLD_BLOCK = false;
  public boolean FOLD_TEMPLATE_BODIES = false;
  public boolean FOLD_SHELL_COMMENTS = true;
  public boolean FOLD_BLOCK_COMMENTS = false;
  public boolean FOLD_PACKAGINGS = false;
  public boolean FOLD_IMPORT_IN_HEADER = true;

  public ScalaCodeStyleSettings(CodeStyleSettings container) {
    super("ScalaCodeStyleSettings", container);
  }
}
