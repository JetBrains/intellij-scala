package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package xml.pattern

import org.jetbrains.plugins.scala.lang.lexer.ScalaXmlTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.xml._

import scala.annotation.tailrec

/*
 *  ContentP ::= [CharData] {ContentP1 [CharData]}
 *
 *  ContentP1 ::= XmlPattern
 *              | CDSect
 *              | Comment
 *              | PI
 *              | Reference
 *              | ScalaPatterns
 */

object ContentP extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val contentMarker = builder.mark()
    builder.getTokenType match {
      case ScalaXmlTokenTypes.XML_DATA_CHARACTERS =>
        builder.advanceLexer()
      case _ =>
    }
    @tailrec
    def subparse(): Unit = {
      var isReturn = false
      if (!CDSect() &&
        !Comment() &&
        !PI() &&
        !ScalaPatterns() &&
        !XmlPattern()) isReturn = true
      builder.getTokenType match {
        case ScalaXmlTokenTypes.XML_DATA_CHARACTERS =>
          builder.advanceLexer()
        case _ =>
          if (isReturn) return
      }
      subparse()
    }
    subparse()
    contentMarker.drop()
    true
  }
}