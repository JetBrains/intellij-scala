package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package xml.pattern

import com.intellij.psi.xml.XmlTokenType
import org.jetbrains.plugins.scala.lang.lexer.ScalaXmlTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/*
 * STagP ::= < Name [S] >
 */

object STagP extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val tagMarker = builder.mark()
    builder.getTokenType match {
      case ScalaXmlTokenTypes.XML_START_TAG_START =>
        builder.advanceLexer()
      case _ =>
        tagMarker.drop()
        return false
    }
    builder.getTokenType match {
      case ScalaXmlTokenTypes.XML_NAME =>
        builder.advanceLexer()
      case _ => builder error ErrMsg("xml.name.expected")
    }
    builder.getTokenType match {
      case XmlTokenType.XML_WHITE_SPACE => builder.advanceLexer()
      case _ =>
    }
    builder.getTokenType match {
      case ScalaXmlTokenTypes.XML_TAG_END =>
        builder.advanceLexer()
        tagMarker.done(ScalaElementType.XML_START_TAG)
        true
      case _ =>
        builder error ErrMsg("xml.tag.end.expected")
        tagMarker.done(ScalaElementType.XML_START_TAG)
        true
    }
  }
}