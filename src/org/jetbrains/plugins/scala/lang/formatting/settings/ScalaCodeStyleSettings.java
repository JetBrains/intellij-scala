package org.jetbrains.plugins.scala.lang.formatting.settings;

import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import static com.intellij.psi.codeStyle.CommonCodeStyleSettings.*;

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

  //indents
  public boolean NOT_CONTINUATION_INDENT_FOR_PARAMS = false;

  //Alignments
  public boolean ALIGN_MULTILINE_FOR = true;
  public boolean ALIGN_IF_ELSE = false;

  public boolean PLACE_CLOSURE_PARAMETERS_ON_NEW_LINE = true;

  public boolean ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = false;
  public int CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 5;
  public boolean ADD_IMPORT_MOST_CLOSE_TO_REFERENCE = false;
  public boolean ADD_FULL_QUALIFIED_IMPORTS = false;

  public boolean SEARCH_ALL_SYMBOLS = false;
  public boolean ENABLE_JAVA_TO_SCALA_CONVERSION = true;
  public boolean DONT_SHOW_CONVERSION_DIALOG = false;

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

  public boolean ENABLE_ERROR_HIGHLIGHTING = false;
  public boolean SHOW_IMPLICIT_CONVERSIONS = true;

  public boolean WRAP_BEFORE_WITH_KEYWORD = false;

  //TODO: add this block as custom settings.
  public int METHOD_BRACE_FORCE = 0;
  public int FINALLY_BRACE_FORCE = 0;
  public int TRY_BRACE_FORCE = 0;
  public int CLOSURE_BRACE_FORCE = 0;
  public int CASE_CLAUSE_BRACE_FORCE = 0;

  public ScalaCodeStyleSettings(CodeStyleSettings container) {
    super("ScalaCodeStyleSettings", container);
  }
}
