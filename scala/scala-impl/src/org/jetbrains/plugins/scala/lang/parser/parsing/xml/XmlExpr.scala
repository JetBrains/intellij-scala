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

object XmlExpr {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val xmlMarker = builder.mark
    builder.disableNewlines()
    if (!XmlContent.parse(builder)) {
      xmlMarker.drop()
      builder.restoreNewlinesState()
      return false
    }
    while (Element.parse(builder)) {}
    xmlMarker.done(ScalaElementType.XML_EXPR)
    builder.restoreNewlinesState()
    return true
  }
}