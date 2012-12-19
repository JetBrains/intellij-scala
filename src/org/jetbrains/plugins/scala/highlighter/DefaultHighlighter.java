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

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.SyntaxHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NonNls;

import java.awt.*;

/**
 * @author ilyas
 *         Date: 24.09.2006
 */
public class DefaultHighlighter {
  // Comments
  @NonNls
  static final String LINE_COMMENT_ID = "Scala Line comment";
  @NonNls
  static final String BLOCK_COMMENT_ID = "Scala Block comment";
  @NonNls
  static final String KEYWORD_ID = "Scala Keyword";
  @NonNls
  static final String NUMBER_ID = "Scala Number";
  @NonNls
  static final String STRING_ID = "Scala String";
  @NonNls
  static final String VALID_STRING_ESCAPE_ID = "Scala Valid escape in string";
  @NonNls
  static final String INVALID_STRING_ESCAPE_ID = "Scala Invalid escape in string";
  @NonNls
  static final String BRACKETS_ID = "Scala Brackets";
  @NonNls
  static final String BRACES_ID = "Scala Braces";
  @NonNls
  static final String COLON_ID = "Scala Colon";
  @NonNls
  static final String PARENTHESES_ID = "Scala Parentheses";
  @NonNls
  static final String ASSIGN_ID = "Scala Assign";
  @NonNls
  static final String SEMICOLON_ID = "Scala Semicolon";
  @NonNls
  static final String DOT_ID = "Scala Dot";
  @NonNls
  static final String COMMA_ID = "Scala Comma";
  @NonNls
  static final String INTERPOLATED_STRING_INJECTION_ID = "Interpolated String Injection";
  @NonNls
  static final String MUTABLE_COLLECTION_ID = "Scala Mutable Collection";
  @NonNls
  static final String IMMUTABLE_COLLECTION_ID = "Scala Immutable Collection";
  @NonNls
  static final String JAVA_COLLECTION_ID = "Standart Java Collection";
  @NonNls
  static final String TYPEPARAM_ID = "Scala Type parameter";
  @NonNls
  static final String PREDEF_ID = "Scala Predefined types";
  @NonNls
  static final String OBJECT_ID = "Scala Object";
  @NonNls
  static final String CLASS_ID = "Scala Class";
  @NonNls
  static final String BAD_CHARACTER_ID = "Scala Bad character";
  @NonNls
  static final String DOC_COMMENT_ID = "ScalaDoc comment";
  @NonNls
  static final String SCALA_DOC_TAG_ID = "ScalaDoc comment tag";
  @NonNls
  static final String SCALA_DOC_HTML_TAG_ID = "ScalaDoc html tag";
  @NonNls
  static final String SCALA_DOC_WIKI_SYNTAX_ID = "ScalaDoc wiki syntax elements";
  @NonNls
  static final String SCALA_DOC_HTML_ESCAPE_ID = "ScalaDoc html escape sequences";
  @NonNls
  static final String SCALA_DOC_MARKUP_ID = "ScalaDoc comment markup";
  @NonNls
  static final String SCALA_DOC_TAG_PARAM_VALUE_ID = "ScalaDoc @param value";
  @NonNls
  static final String SCALA_IMPLICITS_ID = "Implicit conversion";
  @NonNls
  static final String ABSTRACT_CLASS_ID = "Scala Abstract class";
  @NonNls
  static final String TRAIT_ID = "Scala Trait";
  @NonNls
  static final String LOCAL_VALUES_ID = "Scala Local value";
  @NonNls
  static final String LOCAL_VARIABLES_ID = "Scala Local variable";
  @NonNls
  static final String LOCAL_LAZY_ID = "Scala Local lazy val/var";
  @NonNls
  static final String VALUES_ID = "Scala Template val";
  @NonNls
  static final String VARIABLES_ID = "Scala Template var";
  @NonNls
  static final String LAZY_ID = "Scala Template lazy val/var";
  @NonNls
  static final String PARAMETER_ID = "Scala Parameter";
  @NonNls
  static final String METHOD_CALL_ID = "Scala Class method call";
  @NonNls
  static final String OBJECT_METHOD_CALL_ID = "Scala Object method call";
  @NonNls
  static final String LOCAL_METHOD_CALL_ID = "Scala Local method call";
  @NonNls
  static final String METHOD_DECLARATION_ID = "Scala Method declaration";
  @NonNls
  static final String ANNOTATION_ID = "Scala Annotation name";
  @NonNls
  static final String ANNOTATION_ATTRIBUTE_ID = "Scala Annotation attribute name";
  @NonNls
  static final String PATTERN_ID = "Scala Pattern value";
  @NonNls
  static final String TYPE_ALIAS_ID = "Scala Type Alias";
  @NonNls
  static final String XML_TEXT_ID = "Scala XML Text";
  @NonNls
  static final String XML_TAG_ID = "Scala XML Tag";
  @NonNls
  static final String IMPLICIT_FIRST_PART_ID = "Implicit conversion first part";
  @NonNls
  static final String IMPLICIT_SECOND_PART_ID = "Implicit conversion second part";

  public static TextAttributesKey LINE_COMMENT = createKey(LINE_COMMENT_ID, SyntaxHighlighterColors.LINE_COMMENT);
  public static TextAttributesKey BLOCK_COMMENT = createKey(BLOCK_COMMENT_ID, SyntaxHighlighterColors.JAVA_BLOCK_COMMENT);
  public static TextAttributesKey KEYWORD = createKey(KEYWORD_ID, SyntaxHighlighterColors.KEYWORD);
  public static TextAttributesKey NUMBER = createKey(NUMBER_ID, SyntaxHighlighterColors.NUMBER);
  public static TextAttributesKey STRING = createKey(STRING_ID, SyntaxHighlighterColors.STRING);
  public static TextAttributesKey VALID_STRING_ESCAPE = createKey(VALID_STRING_ESCAPE_ID, SyntaxHighlighterColors.VALID_STRING_ESCAPE);
  public static TextAttributesKey INVALID_STRING_ESCAPE = createKey(INVALID_STRING_ESCAPE_ID, SyntaxHighlighterColors.INVALID_STRING_ESCAPE);
  public static TextAttributesKey BRACKETS = createKey(BRACKETS_ID, SyntaxHighlighterColors.BRACKETS);
  public static TextAttributesKey BRACES = createKey(BRACES_ID, SyntaxHighlighterColors.BRACES);
  public static TextAttributesKey PARENTHESES = createKey(PARENTHESES_ID, SyntaxHighlighterColors.PARENTHS);
  public static TextAttributesKey COLON = createKey(COLON_ID, SyntaxHighlighterColors.COMMA);
  public static TextAttributesKey SEMICOLON = createKey(SEMICOLON_ID, SyntaxHighlighterColors.COMMA);
  public static TextAttributesKey DOT = createKey(DOT_ID, SyntaxHighlighterColors.DOT);
  public static TextAttributesKey COMMA = createKey(COMMA_ID, SyntaxHighlighterColors.COMMA);
  public static TextAttributesKey INTERPOLATED_STRING_INJECTION = TextAttributesKey.createTextAttributesKey(INTERPOLATED_STRING_INJECTION_ID,
      new TextAttributes(new Color(0, 184, 187), null, null, null, Font.BOLD));
  public static TextAttributesKey MUTABLE_COLLECTION = TextAttributesKey.createTextAttributesKey(MUTABLE_COLLECTION_ID,
      new TextAttributes(null, null, new Color(226, 158, 194), EffectType.BOLD_DOTTED_LINE, Font.PLAIN));
  public static TextAttributesKey IMMUTABLE_COLLECTION = TextAttributesKey.createTextAttributesKey(IMMUTABLE_COLLECTION_ID,
      new TextAttributes(null, null, new Color(172, 203, 121), EffectType.BOLD_DOTTED_LINE, Font.PLAIN));
  public static TextAttributesKey JAVA_COLLECTION = TextAttributesKey.createTextAttributesKey(JAVA_COLLECTION_ID,
      new TextAttributes(null, null, new Color(129, 194, 195), EffectType.BOLD_DOTTED_LINE, Font.PLAIN));
  public static TextAttributesKey PREDEF = createKey(PREDEF_ID, SyntaxHighlighterColors.COMMA);
  public static TextAttributesKey TYPEPARAM = createKey(TYPEPARAM_ID, HighlightInfoType.TYPE_PARAMETER_NAME.getAttributesKey());
  public static TextAttributesKey OBJECT = createKey(OBJECT_ID, HighlightInfoType.CLASS_NAME.getAttributesKey());
  public static TextAttributesKey CLASS = createKey(CLASS_ID, HighlightInfoType.CLASS_NAME.getAttributesKey());
  public static TextAttributesKey SCALA_DOC_TAG = createKey(SCALA_DOC_TAG_ID, SyntaxHighlighterColors.DOC_COMMENT_TAG);
  public static TextAttributesKey SCALA_DOC_HTML_TAG = createKey(SCALA_DOC_HTML_TAG_ID, SyntaxHighlighterColors.DOC_COMMENT_MARKUP);
  public static TextAttributesKey SCALA_DOC_WIKI_SYNTAX = createKey(SCALA_DOC_WIKI_SYNTAX_ID,SyntaxHighlighterColors.DOC_COMMENT_MARKUP);
  public static TextAttributesKey SCALA_DOC_HTML_ESCAPE = createKey(SCALA_DOC_HTML_ESCAPE_ID, SyntaxHighlighterColors.DOC_COMMENT_MARKUP);
  public static TextAttributesKey SCALA_DOC_MARKUP = createKey(SCALA_DOC_MARKUP_ID, SyntaxHighlighterColors.DOC_COMMENT_MARKUP);
  public static TextAttributesKey SCALA_DOC_TAG_PARAM_VALUE = createKey(SCALA_DOC_TAG_PARAM_VALUE_ID, SyntaxHighlighterColors.DOC_COMMENT_TAG);
  public static TextAttributesKey IMPLICIT_CONVERSIONS = TextAttributesKey.createTextAttributesKey(SCALA_IMPLICITS_ID,
      new TextAttributes(null, null, Color.LIGHT_GRAY, EffectType.LINE_UNDERSCORE, Font.PLAIN));
  public static TextAttributesKey ABSTRACT_CLASS = createKey(ABSTRACT_CLASS_ID, HighlightInfoType.ABSTRACT_CLASS_NAME.getAttributesKey());
  public static TextAttributesKey TRAIT = createKey(TRAIT_ID, HighlightInfoType.INTERFACE_NAME.getAttributesKey());
  public static TextAttributesKey LOCAL_VALUES = createKey(LOCAL_VALUES_ID, HighlightInfoType.LOCAL_VARIABLE.getAttributesKey());
  public static TextAttributesKey LOCAL_VARIABLES = createKey(LOCAL_VARIABLES_ID, HighlightInfoType.LOCAL_VARIABLE.getAttributesKey());
  public static TextAttributesKey LOCAL_LAZY = createKey(LOCAL_LAZY_ID, HighlightInfoType.LOCAL_VARIABLE.getAttributesKey());
  public static TextAttributesKey VALUES = createKey(VALUES_ID, HighlightInfoType.STATIC_FIELD.getAttributesKey());
  public static TextAttributesKey VARIABLES = createKey(VARIABLES_ID, HighlightInfoType.STATIC_FIELD.getAttributesKey());
  public static TextAttributesKey LAZY = createKey(LAZY_ID, HighlightInfoType.STATIC_FIELD.getAttributesKey());
  public static TextAttributesKey PATTERN = createKey(PATTERN_ID, HighlightInfoType.LOCAL_VARIABLE.getAttributesKey());
  public static TextAttributesKey PARAMETER = createKey(PARAMETER_ID, HighlightInfoType.PARAMETER.getAttributesKey());
  public static TextAttributesKey METHOD_CALL = createKey(METHOD_CALL_ID, HighlightInfoType.METHOD_CALL.getAttributesKey());
  public static TextAttributesKey OBJECT_METHOD_CALL = createKey(OBJECT_METHOD_CALL_ID, HighlightInfoType.STATIC_METHOD.getAttributesKey());
  public static TextAttributesKey LOCAL_METHOD_CALL = createKey(LOCAL_METHOD_CALL_ID, HighlightInfoType.METHOD_CALL.getAttributesKey());
  public static TextAttributesKey METHOD_DECLARATION = createKey(METHOD_DECLARATION_ID, HighlightInfoType.METHOD_DECLARATION.getAttributesKey());
  public static TextAttributesKey ANNOTATION = createKey(ANNOTATION_ID, HighlightInfoType.ANNOTATION_NAME.getAttributesKey());
  public static TextAttributesKey ANNOTATION_ATTRIBUTE = createKey(ANNOTATION_ATTRIBUTE_ID, HighlightInfoType.ANNOTATION_ATTRIBUTE_NAME.getAttributesKey());
  public static TextAttributesKey TYPE_ALIAS = createKey(TYPE_ALIAS_ID, HighlightInfoType.TYPE_PARAMETER_NAME.getAttributesKey());
  public static TextAttributesKey ASSIGN = createKey(ASSIGN_ID, SyntaxHighlighterColors.OPERATION_SIGN);
  public static TextAttributesKey BAD_CHARACTER = createKey(BAD_CHARACTER_ID, HighlighterColors.BAD_CHARACTER);
  public static TextAttributesKey DOC_COMMENT = createKey(DOC_COMMENT_ID, SyntaxHighlighterColors.DOC_COMMENT);
  public static TextAttributesKey XML_TEXT = createKey(XML_TEXT_ID, SyntaxHighlighterColors.STRING);
  public static TextAttributesKey XML_TAG = createKey(XML_TAG_ID, SyntaxHighlighterColors.DOC_COMMENT_TAG);
  public static TextAttributesKey IMPLICIT_FIRST_PART = TextAttributesKey.createTextAttributesKey(IMPLICIT_FIRST_PART_ID, new TextAttributes(new Color(187, 223, 255), null, null, null, Font.PLAIN));
  public static TextAttributesKey IMPLICIT_SECOND_PART = TextAttributesKey.createTextAttributesKey(IMPLICIT_SECOND_PART_ID, new TextAttributes(new Color(223, 240, 255), null, null, null, Font.PLAIN));

  private static TextAttributesKey createKey(String externalName, TextAttributesKey prototype) {
    return TextAttributesKey.createTextAttributesKey(externalName, prototype.getDefaultAttributes());
  }
}


