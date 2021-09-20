package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package xml

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 17.04.2008
*/

/*
 * XmlExpr ::= XmlContent {Element}
 */

object XmlExpr extends ParsingRule {
  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
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