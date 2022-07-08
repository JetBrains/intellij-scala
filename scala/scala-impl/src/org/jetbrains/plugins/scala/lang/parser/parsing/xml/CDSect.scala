package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package xml

import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenTypesEx, ScalaXmlTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

object CDSect extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val cDataMarker = builder.mark()
    builder.getTokenType match {
      case ScalaXmlTokenTypes.XML_CDATA_START => builder.advanceLexer()
      case _ =>
        cDataMarker.drop()
        return false
    }
    builder.getTokenType match {
      case ScalaXmlTokenTypes.XML_DATA_CHARACTERS => builder.advanceLexer()
      case ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_START => ScalaExpr()
      case _ =>
    }
    builder.getTokenType match {
      case ScalaXmlTokenTypes.XML_CDATA_END => builder.advanceLexer()
      case _ => builder error ErrMsg("xml.cdata.end.expected")
    }
    cDataMarker.done(ScalaElementType.XML_CD_SECT)
    true
  }
}