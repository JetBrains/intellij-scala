package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package xml

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
/**
* @author Alexander Podkhalyuzin
* Date: 18.04.2008
*/

/*
 * Element::= EmptyElementTag
 *            | STag Content ETag
 */

object Element {
  def parse(builder: PsiBuilder): Boolean = {
    if (EmptyElemTag.parse(builder)) return true
    val elemMarker = builder.mark()
    if (!STag.parse(builder)) {
      elemMarker.drop()
      return false
    }
    Content parse builder
    if (!ETag.parse(builder)) builder error ErrMsg("xml.end.tag.expected")
    elemMarker.done(ScalaElementTypes.XML_ELEMENT)
    return true
  }
}