package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package xml

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.ParserPatcher

/**
* @author Alexander Podkhalyuzin
* Date: 18.04.2008
*/

/*
 * Element::= EmptyElementTag
 *            | STag Content ETag
 */

object Element {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    if (EmptyElemTag.parse(builder) || ParserPatcher.getSuitablePatcher(builder).parse(builder)) return true

    val elemMarker = builder.mark()
    if (!STag.parse(builder)) {
      elemMarker.drop()
      return false
    }
    Content parse builder
    if (!ETag.parse(builder)) builder error ErrMsg("xml.end.tag.expected")
    elemMarker.done(ScalaElementType.XML_ELEMENT)
    return true
  }
}