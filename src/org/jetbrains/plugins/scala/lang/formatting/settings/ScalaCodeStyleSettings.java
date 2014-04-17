package org.jetbrains.plugins.scala.lang.formatting.settings;

import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;

/**
 * User: Alexander Podkhalyuzin
 * Date: 28.07.2008
 */
public class ScalaCodeStyleSettings extends CustomCodeStyleSettings {

  public static ScalaCodeStyleSettings getInstance(Project project) {
    return CodeStyleSettingsManager.getSettings(project).getCustomSettings(ScalaCodeStyleSettings.class);
  }

  public boolean WRAP_BEFORE_WITH_KEYWORD = false;
  public int METHOD_BRACE_FORCE = 0;
  public int FINALLY_BRACE_FORCE = 0;
  public int TRY_BRACE_FORCE = 0;
  public int CLOSURE_BRACE_FORCE = 0;
  public int CASE_CLAUSE_BRACE_FORCE = 0;
  public boolean PLACE_CLOSURE_PARAMETERS_ON_NEW_LINE = false;
  public boolean SPACE_INSIDE_CLOSURE_BRACES = true;
  public boolean PLACE_SELF_TYPE_ON_NEW_LINE = true;
  public boolean ALIGN_IF_ELSE = false;
  //indents
  public boolean NOT_CONTINUATION_INDENT_FOR_PARAMS = false;
  public boolean ALIGN_IN_COLUMNS_CASE_BRANCH = false;
  public boolean ALIGN_COMPOSITE_PATTERN = true;

  public boolean SPACE_AFTER_MODIFIERS_CONSTRUCTOR = false;

  public boolean SPACE_BEFORE_INFIX_METHOD_CALL_PARENTHESES = false;
  public boolean INSERT_WHITESPACES_IN_SIMPLE_ONE_LINE_METHOD = true;

  public boolean SPACE_BEFORE_TYPE_COLON = false;
  public boolean SPACE_AFTER_TYPE_COLON = true;

  //todo: add to spacing settings
  //spcaing settings:
  public boolean SPACE_BEFORE_BRACE_METHOD_CALL = true;
  public boolean SPACE_BEFORE_MATCH_LBRACE = true;
  public boolean KEEP_ONE_LINE_LAMBDAS_IN_ARG_LIST = false;

  public boolean USE_SCALADOC2_FORMATTING = false;

  public boolean PRESERVE_SPACE_AFTER_METHOD_DECLARATION_NAME = false;
  public boolean SPACE_BEFORE_INFIX_LIKE_METHOD_PARENTHESES = false;

  public boolean SPACES_IN_ONE_LINE_BLOCKS = false;

  //xml formatting
  public boolean KEEP_XML_FORMATTING = false;

  //multiline strings support
  public int MULTILINE_STRING_SUPORT = MULTILINE_STRING_ALL;
  public char MARGIN_CHAR = '|';
  public boolean MULTI_LINE_QUOTES_ON_NEW_LINE = true;
  public boolean KEEP_MULTI_LINE_QUOTES = true;
  public int MULTI_LINE_STRING_MARGIN_INDENT = 2;
  public boolean PROCESS_MARGIN_ON_COPY_PASTE = true;

  public static final int MULTILINE_STRING_NONE = 0;
  public static final int MULTILINE_STRING_QUOTES_AND_INDENT = 1;
  public static final int MULTILINE_STRING_ALL = 2;

  //type annotations
  public int LOCAL_PROPERTY_TYPE_ANNOTATION = TypeAnnotationRequirement.Optional.ordinal();
  public int PUBLIC_PROPERTY_TYPE_ANNOTATION = TypeAnnotationRequirement.Preferred.ordinal();
  public int PROTECTED_PROPERTY_TYPE_ANNOTATION = TypeAnnotationRequirement.Preferred.ordinal();
  public int PRIVATE_PROPERTY_TYPE_ANNOTATION = TypeAnnotationRequirement.Optional.ordinal();
  public int OVERRIDING_PROPERTY_TYPE_ANNOTATION = TypeAnnotationPolicy.Regular.ordinal();
  public int SIMPLE_PROPERTY_TYPE_ANNOTATION = TypeAnnotationPolicy.Optional.ordinal();

  public int LOCAL_METHOD_TYPE_ANNOTATION = TypeAnnotationRequirement.Optional.ordinal();
  public int PUBLIC_METHOD_TYPE_ANNOTATION = TypeAnnotationRequirement.Preferred.ordinal();
  public int PROTECTED_METHOD_TYPE_ANNOTATION = TypeAnnotationRequirement.Preferred.ordinal();
  public int PRIVATE_METHOD_TYPE_ANNOTATION = TypeAnnotationRequirement.Optional.ordinal();
  public int OVERRIDING_METHOD_TYPE_ANNOTATION = TypeAnnotationPolicy.Regular.ordinal();
  public int SIMPLE_METHOD_TYPE_ANNOTATION = TypeAnnotationPolicy.Optional.ordinal();

  public ScalaCodeStyleSettings(CodeStyleSettings container) {
    super("ScalaCodeStyleSettings", container);
  }
}
