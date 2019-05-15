package org.jetbrains.plugins.scala
package lang
package lexer

import com.intellij.psi.tree.TokenSet

/**
  * User: Dmitry.Naydanov
  * Date: 15.04.15.
  */
object ScalaXmlTokenTypes {

  import ScalaXmlLexer.ScalaXmlTokenType

  val XML_EQ = ScalaXmlTokenType("XML_EQ")

  val XML_ATTRIBUTE_VALUE_START_DELIMITER = ScalaXmlTokenType("XML_ATTRIBUTE_VALUE_START_DELIMITER")

  val XML_ATTRIBUTE_VALUE_TOKEN = ScalaXmlTokenType("XML_ATTRIBUTE_VALUE_TOKEN")

  val XML_ATTRIBUTE_VALUE_END_DELIMITER = ScalaXmlTokenType("XML_ATTRIBUTE_VALUE_END_DELIMITER")

  val XML_NAME = ScalaXmlTokenType("XML_NAME")

  val XML_TAG_NAME = ScalaXmlTokenType("XML_TAG_NAME")

  val XML_ATTRIBUTE_NAME = ScalaXmlTokenType("XML_ATTRIBUTE_NAME")

  val XML_WHITE_SPACE = ScalaXmlTokenType("XML_WHITESPACE")

  val XML_TAG_END = ScalaXmlTokenType("XML_TAG_END")

  val XML_CDATA_END = ScalaXmlTokenType("XML_CDATA_END")

  val XML_PI_END = ScalaXmlTokenType("XML_PI_END")

  val XML_EMPTY_ELEMENT_END = ScalaXmlTokenType("XML_EMPTY_ELEMENT_END")

  val XML_START_TAG_START = ScalaXmlTokenType("XML_START_TAG_START")

  val XML_END_TAG_START = ScalaXmlTokenType("XML_END_TAG_START")

  val XML_CDATA_START = ScalaXmlTokenType("XML_CDATA_START")

  val XML_PI_START = ScalaXmlTokenType("XML_PI_START")

  val XML_DATA_CHARACTERS = ScalaXmlTokenType("XML_DATA_CHARACTERS")

  val XML_COMMENT_CHARACTERS = ScalaXmlTokenType("XML_COMMENT_CHARACTERS")

  val XML_COMMENT_START = ScalaXmlTokenType("XML_COMMENT_START")

  val XML_COMMENT_END = ScalaXmlTokenType("XML_COMMENT_END")

  val XML_BAD_CHARACTER = ScalaXmlTokenType("XML_BAD_CHARACTER")

  val XML_CHAR_ENTITY_REF = ScalaXmlTokenType("XML_CHAR_ENTITY_REF")

  val XML_ENTITY_REF_TOKEN = ScalaXmlTokenType("XML_ENTITY_REF_TOKEN")

  val XML_TAG_CHARACTERS = ScalaXmlTokenType("XML_TAG_CHARACTERS")

  import parser.ScalaElementType._

  val XML_ELEMENTS = TokenSet.create(
    XML_PI,
    XML_ATTRIBUTE,
    XML_CD_SECT,
    XML_COMMENT,
    XML_ELEMENT,
    XML_EMPTY_TAG,
    XML_END_TAG,
    XML_EXPR,
    XML_PATTERN,
    XML_START_TAG,
    ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_START,
    ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_END,
    XML_EQ,
    XML_ATTRIBUTE_VALUE_START_DELIMITER, XML_NAME, XML_TAG_END, XML_CDATA_END, XML_PI_END, XML_EMPTY_ELEMENT_END,
    XML_START_TAG_START, XML_END_TAG_START, XML_CDATA_START, XML_PI_START, XML_DATA_CHARACTERS, XML_COMMENT_CHARACTERS)

  val XML_COMMENTS = TokenSet.create(XML_COMMENT_START, XML_COMMENT_CHARACTERS, XML_COMMENT_END)
}
