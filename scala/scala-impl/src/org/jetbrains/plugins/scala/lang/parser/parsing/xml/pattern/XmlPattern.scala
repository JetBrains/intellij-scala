package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package xml.pattern

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 18.04.2008
*/

/*
 * XmlPattern ::= EmptyElemTagP
 *              | STagP ContentP ETagP
 */

object XmlPattern {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val patternMarker = builder.mark
    builder.disableNewlines()
    if (EmptyElemTagP.parse(builder)) {
      patternMarker.done(ScalaElementType.XML_PATTERN)
      builder.restoreNewlinesState()
      return true
    }
    if (!STagP.parse(builder)) {
      patternMarker.drop()
      builder.restoreNewlinesState()
      return false
    }
    ContentP parse builder
    if (!ETagP.parse(builder)) builder error ErrMsg("xml.end.tag.expected")
    builder.restoreNewlinesState()
    patternMarker.done(ScalaElementType.XML_PATTERN)
    return true
  }
}