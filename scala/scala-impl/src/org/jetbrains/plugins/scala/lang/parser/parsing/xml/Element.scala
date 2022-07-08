package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package xml

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/*
 * Element::= EmptyElementTag
 *            | STag Content ETag
 */

object Element extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    if (EmptyElemTag() || builder.skipExternalToken()) return true

    val elemMarker = builder.mark()
    if (!STag()) {
      elemMarker.drop()
      return false
    }
    Content()
    if (!ETag()) {
      builder error ErrMsg("xml.end.tag.expected")
    }
    elemMarker.done(ScalaElementType.XML_ELEMENT)
    true
  }
}