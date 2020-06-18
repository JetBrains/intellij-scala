/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.highlighter;

import com.intellij.codeInsight.daemon.impl.JavaHighlightInfoTypes;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.XmlHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class DefaultHighlighter {

  // TODO: can we inline all these ids?
  static final String LINE_COMMENT_ID = "Scala Line comment";
  static final String BLOCK_COMMENT_ID = "Scala Block comment";
  static final String KEYWORD_ID = "Scala Keyword";
  static final String NUMBER_ID = "Scala Number";
  static final String STRING_ID = "Scala String";
  static final String VALID_STRING_ESCAPE_ID = "Scala Valid escape in string";
  static final String INVALID_STRING_ESCAPE_ID = "Scala Invalid escape in string";
  static final String BRACKETS_ID = "Scala Brackets";
  static final String BRACES_ID = "Scala Braces";
  static final String COLON_ID = "Scala Colon";
  static final String PARENTHESES_ID = "Scala Parentheses";
  static final String ASSIGN_ID = "Scala Assign";
  static final String ARROW_ID = "Scala Arrow";
  static final String SEMICOLON_ID = "Scala Semicolon";
  static final String DOT_ID = "Scala Dot";
  static final String COMMA_ID = "Scala Comma";
  static final String INTERPOLATED_STRING_INJECTION_ID = "Interpolated String Injection";
  static final String MUTABLE_COLLECTION_ID = "Scala Mutable Collection";
  static final String IMMUTABLE_COLLECTION_ID = "Scala Immutable Collection";
  static final String JAVA_COLLECTION_ID = "StandardF Java Collection";
  static final String TYPEPARAM_ID = "Scala Type parameter";
  static final String PREDEF_ID = "Scala Predefined types";
  static final String OBJECT_ID = "Scala Object";
  static final String CLASS_ID = "Scala Class";
  static final String BAD_CHARACTER_ID = "Scala Bad character";

  // ScalaDoc
  static final String DOC_COMMENT_ID = "ScalaDoc comment";
  static final String SCALA_DOC_TAG_ID = "ScalaDoc comment tag";
  static final String SCALA_DOC_HTML_TAG_ID = "ScalaDoc html tag";
  static final String SCALA_DOC_WIKI_SYNTAX_ID = "ScalaDoc wiki syntax elements";
  static final String SCALA_DOC_LIST_ITEM_HEAD_ID = "ScalaDoc list item head";
  static final String SCALA_DOC_HTML_ESCAPE_ID = "ScalaDoc html escape sequences";
  static final String SCALA_DOC_MARKUP_ID = "ScalaDoc comment markup";
  static final String SCALA_DOC_TAG_PARAM_VALUE_ID = "ScalaDoc @param value";

  static final String IMPLICIT_CONVERSIONS_ID = "Implicit conversion";
  static final String ABSTRACT_CLASS_ID = "Scala Abstract class";
  static final String TRAIT_ID = "Scala Trait";
  static final String LOCAL_VALUES_ID = "Scala Local value";
  static final String LOCAL_VARIABLES_ID = "Scala Local variable";
  static final String LOCAL_LAZY_ID = "Scala Local lazy val/var";
  static final String VALUES_ID = "Scala Template val";
  static final String VARIABLES_ID = "Scala Template var";
  static final String LAZY_ID = "Scala Template lazy val/var";
  static final String PARAMETER_ID = "Scala Parameter";
  static final String ANONYMOUS_PARAMETER_ID = "Scala Anonymous Parameter";
  static final String METHOD_CALL_ID = "Scala Class method call";
  static final String OBJECT_METHOD_CALL_ID = "Scala Object method call";
  static final String LOCAL_METHOD_CALL_ID = "Scala Local method call";
  static final String METHOD_DECLARATION_ID = "Scala Method declaration";
  static final String ANNOTATION_ID = "Scala Annotation name";
  static final String ANNOTATION_ATTRIBUTE_ID = "Scala Annotation attribute name";
  static final String PATTERN_ID = "Scala Pattern value";
  static final String GENERATOR_ID = "Scala For statement value";
  static final String TYPE_ALIAS_ID = "Scala Type Alias";
  static final String XML_TAG_ID = "Scala XML tag";
  static final String XML_TAG_NAME_ID = "Scala XML tag name";
  static final String XML_TAG_DATA_ID = "Scala XML tag data";
  static final String XML_ATTRIBUTE_NAME_ID = "Scala XML attribute name";
  static final String XML_ATTRIBUTE_VALUE_ID = "Scala XML attribute value";
  static final String XML_COMMENT_ID = "Scala XML comment";
  static final String SCALATEST_KEYWORD_ID = "Scalatest keyword";

  public static TextAttributesKey LINE_COMMENT = createKey(LINE_COMMENT_ID, DefaultLanguageHighlighterColors.LINE_COMMENT);
  public static TextAttributesKey BLOCK_COMMENT = createKey(BLOCK_COMMENT_ID, DefaultLanguageHighlighterColors.BLOCK_COMMENT);
  public static TextAttributesKey KEYWORD = createKey(KEYWORD_ID, DefaultLanguageHighlighterColors.KEYWORD);
  public static TextAttributesKey NUMBER = createKey(NUMBER_ID, DefaultLanguageHighlighterColors.NUMBER);
  public static TextAttributesKey STRING = createKey(STRING_ID, DefaultLanguageHighlighterColors.STRING);
  public static TextAttributesKey VALID_STRING_ESCAPE = createKey(VALID_STRING_ESCAPE_ID, DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE);
  public static TextAttributesKey INVALID_STRING_ESCAPE = createKey(INVALID_STRING_ESCAPE_ID, DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE);
  public static TextAttributesKey BRACKETS = createKey(BRACKETS_ID, DefaultLanguageHighlighterColors.BRACKETS);
  public static TextAttributesKey BRACES = createKey(BRACES_ID, DefaultLanguageHighlighterColors.BRACES);
  public static TextAttributesKey PARENTHESES = createKey(PARENTHESES_ID, DefaultLanguageHighlighterColors.PARENTHESES);
  public static TextAttributesKey COLON = createKey(COLON_ID, DefaultLanguageHighlighterColors.COMMA);
  public static TextAttributesKey SEMICOLON = createKey(SEMICOLON_ID, DefaultLanguageHighlighterColors.COMMA);
  public static TextAttributesKey DOT = createKey(DOT_ID, DefaultLanguageHighlighterColors.DOT);
  public static TextAttributesKey COMMA = createKey(COMMA_ID, DefaultLanguageHighlighterColors.COMMA);
  public static TextAttributesKey INTERPOLATED_STRING_INJECTION = createKey(INTERPOLATED_STRING_INJECTION_ID, DefaultLanguageHighlighterColors.IDENTIFIER);
  public static TextAttributesKey MUTABLE_COLLECTION = createKey(MUTABLE_COLLECTION_ID, DefaultLanguageHighlighterColors.IDENTIFIER);
  public static TextAttributesKey IMMUTABLE_COLLECTION = createKey(IMMUTABLE_COLLECTION_ID, DefaultLanguageHighlighterColors.IDENTIFIER);
  public static TextAttributesKey JAVA_COLLECTION = createKey(JAVA_COLLECTION_ID, DefaultLanguageHighlighterColors.IDENTIFIER);
  public static TextAttributesKey PREDEF = createKey(PREDEF_ID, DefaultLanguageHighlighterColors.COMMA);
  public static TextAttributesKey TYPEPARAM = createKey(TYPEPARAM_ID, JavaHighlightInfoTypes.TYPE_PARAMETER_NAME.getAttributesKey());
  public static TextAttributesKey OBJECT = createKey(OBJECT_ID, JavaHighlightInfoTypes.CLASS_NAME.getAttributesKey());
  public static TextAttributesKey CLASS = createKey(CLASS_ID, JavaHighlightInfoTypes.CLASS_NAME.getAttributesKey());

  // ScalaDoc
  private static final TextAttributesKey SCALA_DOC_COMMENT_MARKUP = DefaultLanguageHighlighterColors.DOC_COMMENT_MARKUP;

  public static TextAttributesKey DOC_COMMENT = createKey(DOC_COMMENT_ID, DefaultLanguageHighlighterColors.DOC_COMMENT);
  public static TextAttributesKey SCALA_DOC_TAG = createKey(SCALA_DOC_TAG_ID, DefaultLanguageHighlighterColors.DOC_COMMENT_TAG);
  public static TextAttributesKey SCALA_DOC_TAG_PARAM_VALUE = createKey(SCALA_DOC_TAG_PARAM_VALUE_ID, DefaultLanguageHighlighterColors.DOC_COMMENT_TAG);

  public static TextAttributesKey SCALA_DOC_HTML_TAG = createKey(SCALA_DOC_HTML_TAG_ID, SCALA_DOC_COMMENT_MARKUP);
  public static TextAttributesKey SCALA_DOC_WIKI_SYNTAX = createKey(SCALA_DOC_WIKI_SYNTAX_ID, SCALA_DOC_COMMENT_MARKUP);
  public static TextAttributesKey SCALA_DOC_HTML_ESCAPE = createKey(SCALA_DOC_HTML_ESCAPE_ID, SCALA_DOC_COMMENT_MARKUP);
  public static TextAttributesKey SCALA_DOC_MARKUP = createKey(SCALA_DOC_MARKUP_ID, SCALA_DOC_COMMENT_MARKUP);
  public static TextAttributesKey SCALA_DOC_LIST_ITEM_HEAD = createKey(SCALA_DOC_LIST_ITEM_HEAD_ID, SCALA_DOC_COMMENT_MARKUP);


  public static TextAttributesKey IMPLICIT_CONVERSIONS = createKey(IMPLICIT_CONVERSIONS_ID, DefaultLanguageHighlighterColors.IDENTIFIER);
  public static TextAttributesKey ABSTRACT_CLASS = createKey(ABSTRACT_CLASS_ID, JavaHighlightInfoTypes.ABSTRACT_CLASS_NAME.getAttributesKey());
  public static TextAttributesKey TRAIT = createKey(TRAIT_ID, JavaHighlightInfoTypes.INTERFACE_NAME.getAttributesKey());
  public static TextAttributesKey LOCAL_VALUES = createKey(LOCAL_VALUES_ID, JavaHighlightInfoTypes.LOCAL_VARIABLE.getAttributesKey());
  public static TextAttributesKey LOCAL_VARIABLES = createKey(LOCAL_VARIABLES_ID, JavaHighlightInfoTypes.LOCAL_VARIABLE.getAttributesKey());
  public static TextAttributesKey LOCAL_LAZY = createKey(LOCAL_LAZY_ID, JavaHighlightInfoTypes.LOCAL_VARIABLE.getAttributesKey());
  public static TextAttributesKey VALUES = createKey(VALUES_ID, JavaHighlightInfoTypes.STATIC_FIELD.getAttributesKey());
  public static TextAttributesKey VARIABLES = createKey(VARIABLES_ID, JavaHighlightInfoTypes.STATIC_FIELD.getAttributesKey());
  public static TextAttributesKey LAZY = createKey(LAZY_ID, JavaHighlightInfoTypes.STATIC_FIELD.getAttributesKey());
  public static TextAttributesKey PATTERN = createKey(PATTERN_ID, JavaHighlightInfoTypes.LOCAL_VARIABLE.getAttributesKey());
  public static TextAttributesKey GENERATOR = createKey(GENERATOR_ID, JavaHighlightInfoTypes.LOCAL_VARIABLE.getAttributesKey());
  public static TextAttributesKey PARAMETER = createKey(PARAMETER_ID, JavaHighlightInfoTypes.PARAMETER.getAttributesKey());
  public static TextAttributesKey ANONYMOUS_PARAMETER = createKey(ANONYMOUS_PARAMETER_ID, JavaHighlightInfoTypes.PARAMETER.getAttributesKey());
  public static TextAttributesKey METHOD_CALL = createKey(METHOD_CALL_ID, JavaHighlightInfoTypes.METHOD_CALL.getAttributesKey());
  public static TextAttributesKey OBJECT_METHOD_CALL = createKey(OBJECT_METHOD_CALL_ID, JavaHighlightInfoTypes.STATIC_METHOD.getAttributesKey());
  public static TextAttributesKey LOCAL_METHOD_CALL = createKey(LOCAL_METHOD_CALL_ID, JavaHighlightInfoTypes.METHOD_CALL.getAttributesKey());
  public static TextAttributesKey METHOD_DECLARATION = createKey(METHOD_DECLARATION_ID, JavaHighlightInfoTypes.METHOD_DECLARATION.getAttributesKey());
  public static TextAttributesKey ANNOTATION = createKey(ANNOTATION_ID, JavaHighlightInfoTypes.ANNOTATION_NAME.getAttributesKey());
  public static TextAttributesKey ANNOTATION_ATTRIBUTE = createKey(ANNOTATION_ATTRIBUTE_ID, JavaHighlightInfoTypes.ANNOTATION_ATTRIBUTE_NAME.getAttributesKey());
  public static TextAttributesKey TYPE_ALIAS = createKey(TYPE_ALIAS_ID, JavaHighlightInfoTypes.TYPE_PARAMETER_NAME.getAttributesKey());
  public static TextAttributesKey ASSIGN = createKey(ASSIGN_ID, DefaultLanguageHighlighterColors.OPERATION_SIGN);
  // TODO Inherit Java's arrow attributes when Java will support them
  public static TextAttributesKey ARROW = createKey(ARROW_ID, DefaultLanguageHighlighterColors.OPERATION_SIGN);
  public static TextAttributesKey BAD_CHARACTER = createKey(BAD_CHARACTER_ID, HighlighterColors.BAD_CHARACTER);

  public static TextAttributesKey XML_TAG = createKey(XML_TAG_ID, XmlHighlighterColors.XML_TAG);
  public static TextAttributesKey XML_TAG_NAME = createKey(XML_TAG_NAME_ID, XmlHighlighterColors.XML_TAG_NAME);
  public static TextAttributesKey XML_TAG_DATA = createKey(XML_TAG_DATA_ID, XmlHighlighterColors.XML_TAG_DATA);
  public static TextAttributesKey XML_ATTRIBUTE_NAME = createKey(XML_ATTRIBUTE_NAME_ID, XmlHighlighterColors.XML_ATTRIBUTE_NAME);
  public static TextAttributesKey XML_ATTRIBUTE_VALUE = createKey(XML_ATTRIBUTE_VALUE_ID, XmlHighlighterColors.XML_ATTRIBUTE_VALUE);
  public static TextAttributesKey XML_COMMENT = createKey(XML_COMMENT_ID, XmlHighlighterColors.XML_COMMENT);

  public static TextAttributesKey SCALATEST_KEYWORD = createKey(SCALATEST_KEYWORD_ID, DefaultLanguageHighlighterColors.KEYWORD);

  private static TextAttributesKey createKey(@NonNls @NotNull String externalName, TextAttributesKey prototype) {
    return TextAttributesKey.createTextAttributesKey(externalName, prototype);
  }
}


