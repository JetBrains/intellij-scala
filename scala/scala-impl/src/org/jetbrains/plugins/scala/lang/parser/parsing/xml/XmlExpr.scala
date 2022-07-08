package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package xml

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/*
 * XmlExpr ::= XmlContent {Element}
 */

object XmlExpr extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val xmlMarker = builder.mark()
    builder.disableNewlines()
    if (!XmlContent()) {
      xmlMarker.drop()
      builder.restoreNewlinesState()
      return false
    }
    while (Element()) {}
    xmlMarker.done(ScalaElementType.XML_EXPR)
    builder.restoreNewlinesState()
    true
  }
}