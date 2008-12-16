/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.highlighter;

import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.SyntaxHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import org.jetbrains.annotations.NonNls;

/**
 * @author ilyas
 *         Date: 24.09.2006
 */
public class DefaultHighlighter {
  // Comments
  @NonNls
  static final String LINE_COMMENT_ID = "Line comment";
  @NonNls
  static final String BLOCK_COMMENT_ID = "Block comment";
  @NonNls
  static final String KEYWORD_ID = "Keyword";
  @NonNls
  static final String NUMBER_ID = "Number";
  @NonNls
  static final String STRING_ID = "String";
  @NonNls
  static final String BRACKETS_ID = "Brackets";
  @NonNls
  static final String BRACES_ID = "Braces";
  @NonNls
  static final String COLON_ID = "Colon";
  @NonNls
  static final String PARENTHESES_ID = "Parentheses";
  @NonNls
  static final String ASSIGN_ID = "Assign";
  @NonNls
  static final String SEMICOLON_ID = "Semicolon";
  @NonNls
  static final String DOT_ID = "Dot";
  @NonNls
  static final String COMMA_ID = "Comma";

  @NonNls
  static final String TYPEPARAM_ID = "Type parameter";
  @NonNls
  static final String PREDEF_ID = "Predefined types";
  @NonNls
  static final String OBJECT_ID = "Object";
  @NonNls
  static final String CLASS_FIELD_DECLARATION_ID = "Field declaration";
  @NonNls
  static final String CLASS_ID = "Class";
  @NonNls
  static final String CLASS_FIELD_DEFINITION_ID = "Field definition";
  @NonNls
  static final String BAD_CHARACTER_ID = "Bad character";
  @NonNls
  static final String DOC_COMMENT_ID = "ScalaDoc comment";
  @NonNls
  static final String SCALA_DOC_TAG_ID = "ScalaDoc comment tag";
  @NonNls
  static final String SCALA_DOC_MARKUP_ID = "ScalaDoc comment markup";
  @NonNls
  static final String ABSTRACT_CLASS_ID = "Abstract class";
  @NonNls
  static final String TRAIT_ID = "Trait";
  @NonNls
  static final String LOCAL_VALUES_ID = "Local val";
  @NonNls
  static final String LOCAL_VARIABLES_ID = "Local var";
  @NonNls
  static final String LOCAL_LAZY_ID = "Local lazy val/var";
  @NonNls
  static final String PARAMETER_ID = "Parameter";
  @NonNls
  static final String METHOD_CALL_ID = "Class method call";
  @NonNls
  static final String OBJECT_METHOD_CALL_ID = "Object method call";
  @NonNls
  static final String LOCAL_METHOD_CALL_ID = "Local method call";
  @NonNls
  static final String METHOD_DECLARATION_ID = "Method declaration";
  @NonNls
  static final String ANNOTATION_ID = "Annotation name";
  @NonNls
  static final String ANNOTATION_ATTRIBUTE_ID = "Annotation attribute name";
  @NonNls
  static final String PATTERN_ID = "Pattern value";
  @NonNls
  static final String TYPE_ALIAS_ID = "Type Alias";

  // Registering TextAttributes
  static {
    TextAttributesKey.createTextAttributesKey(LINE_COMMENT_ID, SyntaxHighlighterColors.LINE_COMMENT.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(BLOCK_COMMENT_ID, SyntaxHighlighterColors.JAVA_BLOCK_COMMENT.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(KEYWORD_ID, SyntaxHighlighterColors.KEYWORD.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(NUMBER_ID, SyntaxHighlighterColors.NUMBER.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(STRING_ID, SyntaxHighlighterColors.STRING.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(BRACKETS_ID, SyntaxHighlighterColors.BRACKETS.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(BRACKETS_ID, SyntaxHighlighterColors.BRACKETS.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(BRACES_ID, SyntaxHighlighterColors.BRACES.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(PARENTHESES_ID, SyntaxHighlighterColors.PARENTHS.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(COLON_ID, SyntaxHighlighterColors.COMMA.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(SEMICOLON_ID, SyntaxHighlighterColors.COMMA.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(DOT_ID, SyntaxHighlighterColors.DOT.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(COMMA_ID, SyntaxHighlighterColors.COMMA.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(PREDEF_ID, SyntaxHighlighterColors.COMMA.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(TYPEPARAM_ID, HighlightInfoType.TYPE_PARAMETER_NAME.getAttributesKey().getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(OBJECT_ID, HighlightInfoType.CLASS_NAME.getAttributesKey().getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(CLASS_FIELD_DECLARATION_ID, HighlightInfoType.STATIC_FIELD.getAttributesKey().getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(CLASS_ID, HighlightInfoType.CLASS_NAME.getAttributesKey().getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(CLASS_FIELD_DEFINITION_ID, HighlightInfoType.INSTANCE_FIELD.getAttributesKey().getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(SCALA_DOC_TAG_ID, SyntaxHighlighterColors.DOC_COMMENT_TAG.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(SCALA_DOC_MARKUP_ID, SyntaxHighlighterColors.DOC_COMMENT_MARKUP.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(ABSTRACT_CLASS_ID, HighlightInfoType.ABSTRACT_CLASS_NAME.getAttributesKey().getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(TRAIT_ID, HighlightInfoType.INTERFACE_NAME.getAttributesKey().getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(LOCAL_VALUES_ID, HighlightInfoType.LOCAL_VARIABLE.getAttributesKey().getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(LOCAL_VARIABLES_ID, HighlightInfoType.LOCAL_VARIABLE.getAttributesKey().getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(LOCAL_LAZY_ID, HighlightInfoType.LOCAL_VARIABLE.getAttributesKey().getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(PATTERN_ID, HighlightInfoType.LOCAL_VARIABLE.getAttributesKey().getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(PARAMETER_ID, HighlightInfoType.PARAMETER.getAttributesKey().getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(METHOD_CALL_ID, HighlightInfoType.METHOD_CALL.getAttributesKey().getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(OBJECT_METHOD_CALL_ID, HighlightInfoType.STATIC_METHOD.getAttributesKey().getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(LOCAL_METHOD_CALL_ID, HighlightInfoType.METHOD_CALL.getAttributesKey().getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(METHOD_DECLARATION_ID, HighlightInfoType.METHOD_DECLARATION.getAttributesKey().getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(ANNOTATION_ID, HighlightInfoType.ANNOTATION_NAME.getAttributesKey().getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(ANNOTATION_ATTRIBUTE_ID, HighlightInfoType.ANNOTATION_ATTRIBUTE_NAME.getAttributesKey().getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(TYPE_ALIAS_ID, HighlightInfoType.TYPE_PARAMETER_NAME.getAttributesKey().getDefaultAttributes());


    TextAttributesKey.createTextAttributesKey(ASSIGN_ID, SyntaxHighlighterColors.OPERATION_SIGN.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(BAD_CHARACTER_ID, HighlighterColors.BAD_CHARACTER.getDefaultAttributes());
  }

  public static TextAttributesKey LINE_COMMENT = TextAttributesKey.createTextAttributesKey(LINE_COMMENT_ID);
  public static TextAttributesKey BLOCK_COMMENT = TextAttributesKey.createTextAttributesKey(BLOCK_COMMENT_ID);
  public static TextAttributesKey KEYWORD = TextAttributesKey.createTextAttributesKey(KEYWORD_ID);
  public static TextAttributesKey NUMBER = TextAttributesKey.createTextAttributesKey(NUMBER_ID);
  public static TextAttributesKey STRING = TextAttributesKey.createTextAttributesKey(STRING_ID);
  public static TextAttributesKey BRACKETS = TextAttributesKey.createTextAttributesKey(BRACKETS_ID);
  public static TextAttributesKey BRACES = TextAttributesKey.createTextAttributesKey(BRACES_ID);
  public static TextAttributesKey PARENTHESES = TextAttributesKey.createTextAttributesKey(PARENTHESES_ID);
  public static TextAttributesKey COLON = TextAttributesKey.createTextAttributesKey(COLON_ID);
  public static TextAttributesKey SEMICOLON = TextAttributesKey.createTextAttributesKey(SEMICOLON_ID);
  public static TextAttributesKey COMMA = TextAttributesKey.createTextAttributesKey(COMMA_ID);
  public static TextAttributesKey DOT = TextAttributesKey.createTextAttributesKey(DOT_ID);
  public static TextAttributesKey TYPEPARAM = TextAttributesKey.createTextAttributesKey(TYPEPARAM_ID);
  public static TextAttributesKey PREDEF = TextAttributesKey.createTextAttributesKey(PREDEF_ID);
  public static TextAttributesKey CLASS = TextAttributesKey.createTextAttributesKey(CLASS_ID);
  public static TextAttributesKey CLASS_FIELD_DEFINITION = TextAttributesKey.createTextAttributesKey(CLASS_FIELD_DEFINITION_ID);
  public static TextAttributesKey OBJECT = TextAttributesKey.createTextAttributesKey(OBJECT_ID);
  public static TextAttributesKey CLASS_FIELD_DECLARATION = TextAttributesKey.createTextAttributesKey(CLASS_FIELD_DECLARATION_ID);
  public static TextAttributesKey SCALA_DOC_TAG = TextAttributesKey.createTextAttributesKey(SCALA_DOC_TAG_ID);
  public static TextAttributesKey SCALA_DOC_MARKUP = TextAttributesKey.createTextAttributesKey(SCALA_DOC_MARKUP_ID);
  public static TextAttributesKey ABSTRACT_CLASS = TextAttributesKey.createTextAttributesKey(ABSTRACT_CLASS_ID);
  public static TextAttributesKey TRAIT = TextAttributesKey.createTextAttributesKey(TRAIT_ID);
  public static TextAttributesKey LOCAL_VALUES = TextAttributesKey.createTextAttributesKey(LOCAL_VALUES_ID);
  public static TextAttributesKey LOCAL_VARIABLES = TextAttributesKey.createTextAttributesKey(LOCAL_VARIABLES_ID);
  public static TextAttributesKey LOCAL_LAZY = TextAttributesKey.createTextAttributesKey(LOCAL_LAZY_ID);
  public static TextAttributesKey PATTERN = TextAttributesKey.createTextAttributesKey(PATTERN_ID);
  public static TextAttributesKey PARAMETER = TextAttributesKey.createTextAttributesKey(PARAMETER_ID);
  public static TextAttributesKey METHOD_CALL = TextAttributesKey.createTextAttributesKey(METHOD_CALL_ID);
  public static TextAttributesKey OBJECT_METHOD_CALL = TextAttributesKey.createTextAttributesKey(OBJECT_METHOD_CALL_ID);
  public static TextAttributesKey LOCAL_METHOD_CALL = TextAttributesKey.createTextAttributesKey(LOCAL_METHOD_CALL_ID);
  public static TextAttributesKey METHOD_DECLARATION = TextAttributesKey.createTextAttributesKey(METHOD_DECLARATION_ID);
  public static TextAttributesKey ANNOTATION = TextAttributesKey.createTextAttributesKey(ANNOTATION_ID);
  public static TextAttributesKey ANNOTATION_ATTRIBUTE = TextAttributesKey.createTextAttributesKey(ANNOTATION_ATTRIBUTE_ID);                                                                                                            
  public static TextAttributesKey TYPE_ALIAS = TextAttributesKey.createTextAttributesKey(TYPE_ALIAS_ID);

  public static TextAttributesKey ASSIGN = TextAttributesKey.createTextAttributesKey(ASSIGN_ID);
  public static TextAttributesKey BAD_CHARACTER = TextAttributesKey.createTextAttributesKey(BAD_CHARACTER_ID);

  public static TextAttributesKey DOC_COMMENT = TextAttributesKey.createTextAttributesKey(DOC_COMMENT_ID,
          SyntaxHighlighterColors.DOC_COMMENT.getDefaultAttributes());

}


