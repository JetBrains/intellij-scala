package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package xml

import com.intellij.psi.xml.XmlTokenType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypesEx
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 18.04.2008
*/

object CDSect {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val cDataMarker = builder.mark()
    builder.getTokenType match {
      case XmlTokenType.XML_CDATA_START => builder.advanceLexer()
      case _ => {
        cDataMarker.drop()
        return false
      }
    }
    builder.getTokenType match {
      case XmlTokenType.XML_DATA_CHARACTERS => builder.advanceLexer()
      case ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_START => ScalaExpr.parse(builder)
      case _ =>
    }
    builder.getTokenType match {
      case XmlTokenType.XML_CDATA_END => builder.advanceLexer()
      case _ => builder error ErrMsg("xml.cdata.end.expected")
    }
    cDataMarker.done(ScalaElementTypes.XML_CD_SECT)
    true
  }
}