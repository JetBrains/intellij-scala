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

import com.intellij.codeInsight.daemon.impl.JavaHighlightInfoTypes;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.XmlHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.jetbrains.annotations.NonNls;

/**
 * @author ilyas
 *         Date: 24.09.2006
 */
public class DefaultHighlighter {
  @NonNls
  static final String LINE_COMMENT_NAME = "Line comment";
  @NonNls
  static final String BLOCK_COMMENT_NAME = "Block comment";
  @NonNls
  static final String KEYWORD_NAME = "Keyword";
  @NonNls
  static final String NUMBER_NAME = "Number";
  @NonNls
  static final String STRING_NAME = "String";
  @NonNls
  static final String VALID_STRING_ESCAPE_NAME = "Valid escape in string";
  @NonNls
  static final String INVALID_STRING_ESCAPE_NAME = "Invalid escape in string";
  @NonNls
  static final String BRACKETS_NAME = "Brackets";
  @NonNls
  static final String BRACES_NAME = "Braces";
  @NonNls
  static final String COLON_NAME = "Colon";
  @NonNls
  static final String PARENTHESES_NAME = "Parentheses";
  @NonNls
  static final String ASSIGN_NAME = "Assign";
  @NonNls
  static final String ARROW_NAME = "Arrow";
  @NonNls
  static final String SEMICOLON_NAME = "Semicolon";
  @NonNls
  static final String DOT_NAME = "Dot";
  @NonNls
  static final String COMMA_NAME = "Comma";
  @NonNls
  static final String INTERPOLATED_STRING_INJECTION_NAME = "Interpolated String Injection";
  @NonNls
  static final String MUTABLE_COLLECTION_NAME = "Mutable Collection";
  @NonNls
  static final String IMMUTABLE_COLLECTION_NAME = "Immutable Collection";
  @NonNls
  static final String JAVA_COLLECTION_NAME = "Standard Java Collection";
  @NonNls
  static final String TYPEPARAM_NAME = "Type parameter";
  @NonNls
  static final String PREDEF_NAME = "Predefined types";
  @NonNls
  static final String OBJECT_NAME = "Object";
  @NonNls
  static final String CLASS_NAME = "Class";
  @NonNls
  static final String BAD_CHARACTER_NAME = "Bad character";
  @NonNls
  static final String DOC_COMMENT_NAME = "ScalaDoc comment";
  @NonNls
  static final String SCALA_DOC_TAG_NAME = "ScalaDoc comment tag";
  @NonNls
  static final String SCALA_DOC_HTML_TAG_NAME = "ScalaDoc html tag";
  @NonNls
  static final String SCALA_DOC_WIKI_SYNTAX_NAME = "ScalaDoc wiki syntax elements";
  @NonNls
  static final String SCALA_DOC_HTML_ESCAPE_NAME = "ScalaDoc html escape sequences";
  @NonNls
  static final String SCALA_DOC_TAG_PARAM_VALUE_NAME = "ScalaDoc @param value";
  @NonNls
  static final String IMPLICIT_CONVERSIONS_NAME = "Implicit conversion";
  @NonNls
  static final String ABSTRACT_CLASS_NAME = "Abstract class";
  @NonNls
  static final String TRAIT_NAME = "Trait";
  @NonNls
  static final String LOCAL_VALUES_NAME = "Local value";
  @NonNls
  static final String LOCAL_VARIABLES_NAME = "Local variable";
  @NonNls
  static final String LOCAL_LAZY_NAME = "Local lazy val/var";
  @NonNls
  static final String VALUES_NAME = "Template val";
  @NonNls
  static final String VARIABLES_NAME = "Template var";
  @NonNls
  static final String LAZY_NAME = "Template lazy val/var";
  @NonNls
  static final String PARAMETER_NAME = "Parameter";
  @NonNls
  static final String ANONYMOUS_PARAMETER_NAME = "Anonymous Parameter";
  @NonNls
  static final String METHOD_CALL_NAME = "Class method call";
  @NonNls
  static final String OBJECT_METHOD_CALL_NAME = "Object method call";
  @NonNls
  static final String LOCAL_METHOD_CALL_NAME = "Local method call";
  @NonNls
  static final String METHOD_DECLARATION_NAME = "Method declaration";
  @NonNls
  static final String ANNOTATION_NAME = "Annotation name";
  @NonNls
  static final String ANNOTATION_ATTRIBUTE_NAME = "Annotation attribute name";
  @NonNls
  static final String PATTERN_NAME = "Pattern value";
  @NonNls
  static final String GENERATOR_NAME = "For statement value";
  @NonNls
  static final String TYPE_ALIAS_NAME = "Type Alias";
  @NonNls
  static final String IMPLICIT_FIRST_PART_NAME = "Implicit conversion first part";
  @NonNls
  static final String IMPLICIT_SECOND_PART_NAME = "Implicit conversion second part";
  
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
  static final String ARROW_ID = "Scala Arrow";
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
  static final String JAVA_COLLECTION_ID = "StandardF Java Collection";
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
  static final String IMPLICIT_CONVERSIONS_ID = "Implicit conversion";
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
  static final String ANONYMOUS_PARAMETER_ID = "Scala Anonymous Parameter";
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
  static final String GENERATOR_ID = "Scala For statement value";
  @NonNls
  static final String TYPE_ALIAS_ID = "Scala Type Alias";
  @NonNls
  static final String XML_TAG_ID = "Scala XML tag";
  @NonNls
  static final String XML_TAG_NAME_ID = "Scala XML tag name";
  @NonNls
  static final String XML_TAG_DATA_ID = "Scala XML tag data";
  @NonNls
  static final String XML_ATTRIBUTE_NAME_ID = "Scala XML attribute name";
  @NonNls
  static final String XML_ATTRIBUTE_VALUE_ID = "Scala XML attribute value";
  @NonNls
  static final String XML_COMMENT_ID = "Scala XML comment";
  @NonNls
  static final String IMPLICIT_FIRST_PART_ID = "Implicit conversion first part";
  @NonNls
  static final String IMPLICIT_SECOND_PART_ID = "Implicit conversion second part";
  @NonNls
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
  public static TextAttributesKey SCALA_DOC_TAG = createKey(SCALA_DOC_TAG_ID, DefaultLanguageHighlighterColors.DOC_COMMENT_TAG);
  public static TextAttributesKey SCALA_DOC_HTML_TAG = createKey(SCALA_DOC_HTML_TAG_ID, DefaultLanguageHighlighterColors.DOC_COMMENT_MARKUP);
  public static TextAttributesKey SCALA_DOC_WIKI_SYNTAX = createKey(SCALA_DOC_WIKI_SYNTAX_ID,DefaultLanguageHighlighterColors.DOC_COMMENT_MARKUP);
  public static TextAttributesKey SCALA_DOC_HTML_ESCAPE = createKey(SCALA_DOC_HTML_ESCAPE_ID, DefaultLanguageHighlighterColors.DOC_COMMENT_MARKUP);
  public static TextAttributesKey SCALA_DOC_MARKUP = createKey(SCALA_DOC_MARKUP_ID, DefaultLanguageHighlighterColors.DOC_COMMENT_MARKUP);
  public static TextAttributesKey SCALA_DOC_TAG_PARAM_VALUE = createKey(SCALA_DOC_TAG_PARAM_VALUE_ID, DefaultLanguageHighlighterColors.DOC_COMMENT_TAG);
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
  public static TextAttributesKey DOC_COMMENT = createKey(DOC_COMMENT_ID, DefaultLanguageHighlighterColors.DOC_COMMENT);

  public static TextAttributesKey XML_TAG = createKey(XML_TAG_ID, XmlHighlighterColors.XML_TAG);
  public static TextAttributesKey XML_TAG_NAME = createKey(XML_TAG_NAME_ID, XmlHighlighterColors.XML_TAG_NAME);
  public static TextAttributesKey XML_TAG_DATA = createKey(XML_TAG_DATA_ID, XmlHighlighterColors.XML_TAG_DATA);
  public static TextAttributesKey XML_ATTRIBUTE_NAME = createKey(XML_ATTRIBUTE_NAME_ID, XmlHighlighterColors.XML_ATTRIBUTE_NAME);
  public static TextAttributesKey XML_ATTRIBUTE_VALUE = createKey(XML_ATTRIBUTE_VALUE_ID, XmlHighlighterColors.XML_ATTRIBUTE_VALUE);
  public static TextAttributesKey XML_COMMENT = createKey(XML_COMMENT_ID, XmlHighlighterColors.XML_COMMENT);

  public static TextAttributesKey SCALATEST_KEYWORD = createKey(SCALATEST_KEYWORD_ID, DefaultLanguageHighlighterColors.KEYWORD);

  private static TextAttributesKey createKey(String externalName, TextAttributesKey prototype) {
    return TextAttributesKey.createTextAttributesKey(externalName, prototype);
  }
}


