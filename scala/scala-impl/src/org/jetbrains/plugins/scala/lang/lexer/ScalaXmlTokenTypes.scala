package org.jetbrains.plugins.scala
package lang
package lexer

import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType

/**
 * NOTE: tokens duplicates of [[com.intellij.psi.xml.XmlTokenType]].
 * Tokens obtained from IDEA XmlLexer are converted to their Scala versions in
 * [[org.jetbrains.plugins.scala.lang.lexer.ScalaXmlLexer.ScalaXmlTokenType.apply]]
 */
object ScalaXmlTokenTypes {

  import ScalaXmlLexer.ScalaXmlTokenType

  val XML_EQ                             : ScalaXmlTokenType = ScalaXmlTokenType("XML_EQ")
  val XML_ATTRIBUTE_VALUE_START_DELIMITER: ScalaXmlTokenType = ScalaXmlTokenType("XML_ATTRIBUTE_VALUE_START_DELIMITER")
  val XML_ATTRIBUTE_VALUE_TOKEN          : ScalaXmlTokenType = ScalaXmlTokenType("XML_ATTRIBUTE_VALUE_TOKEN")
  val XML_ATTRIBUTE_VALUE_END_DELIMITER  : ScalaXmlTokenType = ScalaXmlTokenType("XML_ATTRIBUTE_VALUE_END_DELIMITER")
  val XML_NAME                           : ScalaXmlTokenType = ScalaXmlTokenType("XML_NAME")
  val XML_TAG_NAME                       : ScalaXmlTokenType = ScalaXmlTokenType("XML_TAG_NAME")
  val XML_ATTRIBUTE_NAME                 : ScalaXmlTokenType = ScalaXmlTokenType("XML_ATTRIBUTE_NAME")
  val XML_WHITE_SPACE                    : ScalaXmlTokenType = ScalaXmlTokenType("XML_WHITESPACE")
  val XML_TAG_END                        : ScalaXmlTokenType = ScalaXmlTokenType("XML_TAG_END")
  val XML_CDATA_END                      : ScalaXmlTokenType = ScalaXmlTokenType("XML_CDATA_END")
  val XML_PI_END                         : ScalaXmlTokenType = ScalaXmlTokenType("XML_PI_END")
  val XML_EMPTY_ELEMENT_END              : ScalaXmlTokenType = ScalaXmlTokenType("XML_EMPTY_ELEMENT_END")
  val XML_START_TAG_START                : ScalaXmlTokenType = ScalaXmlTokenType("XML_START_TAG_START")
  val XML_END_TAG_START                  : ScalaXmlTokenType = ScalaXmlTokenType("XML_END_TAG_START")
  val XML_CDATA_START                    : ScalaXmlTokenType = ScalaXmlTokenType("XML_CDATA_START")
  val XML_PI_START                       : ScalaXmlTokenType = ScalaXmlTokenType("XML_PI_START")
  val XML_DATA_CHARACTERS                : ScalaXmlTokenType = ScalaXmlTokenType("XML_DATA_CHARACTERS")
  val XML_COMMENT_CHARACTERS             : ScalaXmlTokenType = ScalaXmlTokenType("XML_COMMENT_CHARACTERS")
  val XML_COMMENT_START                  : ScalaXmlTokenType = ScalaXmlTokenType("XML_COMMENT_START")
  val XML_COMMENT_END                    : ScalaXmlTokenType = ScalaXmlTokenType("XML_COMMENT_END")
  val XML_BAD_CHARACTER                  : ScalaXmlTokenType = ScalaXmlTokenType("XML_BAD_CHARACTER")
  val XML_CHAR_ENTITY_REF                : ScalaXmlTokenType = ScalaXmlTokenType("XML_CHAR_ENTITY_REF")
  val XML_ENTITY_REF_TOKEN               : ScalaXmlTokenType = ScalaXmlTokenType("XML_ENTITY_REF_TOKEN")
  val XML_TAG_CHARACTERS                 : ScalaXmlTokenType = ScalaXmlTokenType("XML_TAG_CHARACTERS")

  val XML_ELEMENTS: TokenSet = TokenSet.create(
    ScalaElementType.XML_PI,
    ScalaElementType.XML_ATTRIBUTE,
    ScalaElementType.XML_CD_SECT,
    ScalaElementType.XML_COMMENT,
    ScalaElementType.XML_ELEMENT,
    ScalaElementType.XML_EMPTY_TAG,
    ScalaElementType.XML_END_TAG,
    ScalaElementType.XML_EXPR,
    ScalaElementType.XML_PATTERN,
    ScalaElementType.XML_START_TAG,
    ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_START,
    ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_END,
    this.XML_EQ,
    this.XML_ATTRIBUTE_VALUE_START_DELIMITER,
    this.XML_NAME,
    this.XML_TAG_END,
    this.XML_CDATA_END,
    this.XML_PI_END,
    this.XML_EMPTY_ELEMENT_END,
    this.XML_START_TAG_START,
    this.XML_END_TAG_START,
    this.XML_CDATA_START,
    this.XML_PI_START,
    this.XML_DATA_CHARACTERS,
    this.XML_COMMENT_CHARACTERS
  )
  val XML_COMMENTS: TokenSet = TokenSet.create(
    XML_COMMENT_START,
    XML_COMMENT_CHARACTERS,
    XML_COMMENT_END
  )
}
