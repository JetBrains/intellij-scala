package org.jetbrains.plugins.scala.lang.formatting.settings;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;

/**
 * User: Alexander Podkhalyuzin
 * Date: 28.07.2008
 */
public class ScalaCodeStyleSettings extends CustomCodeStyleSettings {

  //spcaing settings:
  public boolean SPACE_BEFORE_COMMA = false;
  public boolean SPACE_AFTER_COMMA = true;
  public boolean SPACE_BEFORE_SEMICOLON = false;
  public boolean SPACE_AFTER_SEMICOLON = true;
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

  //keep blank lines
  public int KEEP_BLANK_LINES_IN_CODE = 2;
  public int KEEP_BLANK_LINES_BEFORE_RBRACE = 2;
  public boolean KEEP_LINE_BREAKS = true;

  public ScalaCodeStyleSettings(CodeStyleSettings container) {
    super("ScalaCodeStyleSettings", container);
  }
}
