package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package patterns

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

/*
 * Patern ::= Pattern1 {'|' Pattern1}
 */

object Pattern {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val patternMarker = builder.mark
    if (!Pattern1.parse(builder)) {
      patternMarker.drop()
      return false
    }
    var isComposite = false
    while (builder.getTokenText == "|") {
      isComposite = true
      builder.advanceLexer() //Ate |
      if (!Pattern1.parse(builder)) {
        builder error ScalaBundle.message("wrong.pattern")
      }
    }
    if (isComposite) patternMarker.done(ScalaElementTypes.PATTERN)
    else patternMarker.drop()
    true
  }
}