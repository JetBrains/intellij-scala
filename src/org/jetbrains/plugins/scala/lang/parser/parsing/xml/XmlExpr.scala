package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package xml

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
/**
* @author Alexander Podkhalyuzin
* Date: 17.04.2008
*/

/*
 * XmlExpr ::= XmlContent {Element}
 */

object XmlExpr {
  def parse(builder: PsiBuilder): Boolean = {
    val xmlMarker = builder.mark
    if (!XmlContent.parse(builder)) {
      xmlMarker.drop
      return false
    }
    while (Element.parse(builder)) {}
    xmlMarker.done(ScalaElementTypes.XML_EXPR)
    return true
  }
}