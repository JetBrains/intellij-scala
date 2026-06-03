package org.jetbrains.plugins.scala.highlighter;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;

@SuppressWarnings("unused")
public class ScalaHighlightInfoTypes {
    public static final HighlightInfoType IDENTIFIER = createType(DefaultLanguageHighlighterColors.IDENTIFIER);

    public static final HighlightInfoType LINE_COMMENT = createType(DefaultHighlighter.LINE_COMMENT);
    public static final HighlightInfoType BLOCK_COMMENT = createType(DefaultHighlighter.BLOCK_COMMENT);
    public static final HighlightInfoType KEYWORD = createType(DefaultHighlighter.KEYWORD);
    public static final HighlightInfoType NUMBER = createType(DefaultHighlighter.NUMBER);
    public static final HighlightInfoType STRING = createType(DefaultHighlighter.STRING);
    public static final HighlightInfoType VALID_STRING_ESCAPE = createType(DefaultHighlighter.VALID_STRING_ESCAPE);
    public static final HighlightInfoType INVALID_STRING_ESCAPE = createType(DefaultHighlighter.INVALID_STRING_ESCAPE);
    public static final HighlightInfoType BRACKETS = createType(DefaultHighlighter.BRACKETS);
    public static final HighlightInfoType BRACES = createType(DefaultHighlighter.BRACES);
    public static final HighlightInfoType PARENTHESES = createType(DefaultHighlighter.PARENTHESES);
    public static final HighlightInfoType COLON = createType(DefaultHighlighter.COLON);
    public static final HighlightInfoType SEMICOLON = createType(DefaultHighlighter.SEMICOLON);
    public static final HighlightInfoType DOT = createType(DefaultHighlighter.DOT);
    public static final HighlightInfoType COMMA = createType(DefaultHighlighter.COMMA);
    public static final HighlightInfoType INTERPOLATED_STRING_INJECTION = createType(DefaultHighlighter.INTERPOLATED_STRING_INJECTION);
    public static final HighlightInfoType MUTABLE_COLLECTION = createType(DefaultHighlighter.MUTABLE_COLLECTION);
    public static final HighlightInfoType IMMUTABLE_COLLECTION = createType(DefaultHighlighter.IMMUTABLE_COLLECTION);
    public static final HighlightInfoType JAVA_COLLECTION = createType(DefaultHighlighter.JAVA_COLLECTION);
    public static final HighlightInfoType PREDEF = createType(DefaultHighlighter.PREDEF);
    public static final HighlightInfoType TYPEPARAM = createType(DefaultHighlighter.TYPEPARAM);
    public static final HighlightInfoType OBJECT = createType(DefaultHighlighter.OBJECT);
    public static final HighlightInfoType CLASS = createType(DefaultHighlighter.CLASS);

    // ScalaDoc
    public static final HighlightInfoType DOC_COMMENT = createType(DefaultHighlighter.DOC_COMMENT);
    public static final HighlightInfoType SCALA_DOC_TAG = createType(DefaultHighlighter.SCALA_DOC_TAG);
    public static final HighlightInfoType SCALA_DOC_TAG_PARAM_VALUE = createType(DefaultHighlighter.SCALA_DOC_TAG_PARAM_VALUE);

    public static final HighlightInfoType SCALA_DOC_HTML_TAG = createType(DefaultHighlighter.SCALA_DOC_HTML_TAG);
    public static final HighlightInfoType SCALA_DOC_WIKI_SYNTAX = createType(DefaultHighlighter.SCALA_DOC_WIKI_SYNTAX);
    public static final HighlightInfoType SCALA_DOC_HTML_ESCAPE = createType(DefaultHighlighter.SCALA_DOC_HTML_ESCAPE);
    public static final HighlightInfoType SCALA_DOC_MARKUP = createType(DefaultHighlighter.SCALA_DOC_MARKUP);
    public static final HighlightInfoType SCALA_DOC_LIST_ITEM_HEAD = createType(DefaultHighlighter.SCALA_DOC_LIST_ITEM_HEAD);


    public static final HighlightInfoType IMPLICIT_CONVERSIONS = createType(DefaultHighlighter.IMPLICIT_CONVERSIONS);
    public static final HighlightInfoType ABSTRACT_CLASS = createType(DefaultHighlighter.ABSTRACT_CLASS);
    public static final HighlightInfoType TRAIT = createType(DefaultHighlighter.TRAIT);
    public static final HighlightInfoType LOCAL_VALUES = createType(DefaultHighlighter.LOCAL_VALUES);
    public static final HighlightInfoType LOCAL_VARIABLES = createType(DefaultHighlighter.LOCAL_VARIABLES);
    public static final HighlightInfoType LOCAL_LAZY = createType(DefaultHighlighter.LOCAL_LAZY);
    public static final HighlightInfoType VALUES = createType(DefaultHighlighter.VALUES);
    public static final HighlightInfoType VARIABLES = createType(DefaultHighlighter.VARIABLES);
    public static final HighlightInfoType LAZY = createType(DefaultHighlighter.LAZY);
    public static final HighlightInfoType PATTERN = createType(DefaultHighlighter.PATTERN);
    public static final HighlightInfoType GENERATOR = createType(DefaultHighlighter.GENERATOR);
    public static final HighlightInfoType PARAMETER = createType(DefaultHighlighter.PARAMETER);
    public static final HighlightInfoType NAMED_ARGUMENT = createType(DefaultHighlighter.NAMED_ARGUMENT);
    public static final HighlightInfoType GIVEN = createType(DefaultHighlighter.GIVEN);

    public static final HighlightInfoType PARAMETER_OF_ANONIMOUS_FUNCTION = createType(DefaultHighlighter.PARAMETER_OF_ANONIMOUS_FUNCTION);
    public static final HighlightInfoType METHOD_CALL = createType(DefaultHighlighter.METHOD_CALL);
    public static final HighlightInfoType OBJECT_METHOD_CALL = createType(DefaultHighlighter.OBJECT_METHOD_CALL);
    public static final HighlightInfoType LOCAL_METHOD_CALL = createType(DefaultHighlighter.LOCAL_METHOD_CALL);
    public static final HighlightInfoType METHOD_DECLARATION = createType(DefaultHighlighter.METHOD_DECLARATION);
    public static final HighlightInfoType ANNOTATION = createType(DefaultHighlighter.ANNOTATION);
    public static final HighlightInfoType ANNOTATION_ATTRIBUTE = createType(DefaultHighlighter.ANNOTATION_ATTRIBUTE);
    public static final HighlightInfoType TYPE_ALIAS = createType(DefaultHighlighter.TYPE_ALIAS);
    public static final HighlightInfoType ASSIGN = createType(DefaultHighlighter.ASSIGN);

    public static final HighlightInfoType ARROW = createType(DefaultHighlighter.ARROW);
    public static final HighlightInfoType BAD_CHARACTER = createType(DefaultHighlighter.BAD_CHARACTER);

    public static final HighlightInfoType ENUM = createType(DefaultHighlighter.ENUM);
    public static final HighlightInfoType ENUM_SINGLETON_CASE = createType(DefaultHighlighter.ENUM_SINGLETON_CASE);
    public static final HighlightInfoType ENUM_CLASS_CASE = createType(DefaultHighlighter.ENUM_CLASS_CASE);

    public static final HighlightInfoType XML_TAG = createType(DefaultHighlighter.XML_TAG);
    public static final HighlightInfoType XML_TAG_NAME = createType(DefaultHighlighter.XML_TAG_NAME);
    public static final HighlightInfoType XML_TAG_DATA = createType(DefaultHighlighter.XML_TAG_DATA);
    public static final HighlightInfoType XML_ATTRIBUTE_NAME = createType(DefaultHighlighter.XML_ATTRIBUTE_NAME);
    public static final HighlightInfoType XML_ATTRIBUTE_VALUE = createType(DefaultHighlighter.XML_ATTRIBUTE_VALUE);
    public static final HighlightInfoType XML_COMMENT = createType(DefaultHighlighter.XML_COMMENT);

    public static final HighlightInfoType SCALATEST_KEYWORD = createType(DefaultHighlighter.SCALATEST_KEYWORD);

    // Scala directives
    public static final HighlightInfoType SCALA_DIRECTIVE_PREFIX = createType(DefaultHighlighter.SCALA_DIRECTIVE_PREFIX);
    public static final HighlightInfoType SCALA_DIRECTIVE_COMMAND = createType(DefaultHighlighter.SCALA_DIRECTIVE_COMMAND);
    public static final HighlightInfoType SCALA_DIRECTIVE_KEY = createType(DefaultHighlighter.SCALA_DIRECTIVE_KEY);
    public static final HighlightInfoType SCALA_DIRECTIVE_VALUE = createType(DefaultHighlighter.SCALA_DIRECTIVE_VALUE);

    private static HighlightInfoType createType(TextAttributesKey key) {
        return new HighlightInfoType.HighlightInfoTypeImpl(HighlightInfoType.SYMBOL_TYPE_SEVERITY, key);
    }
}
