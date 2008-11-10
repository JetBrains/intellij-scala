package org.jetbrains.plugins.scala.lang.parser.parsing.patterns

import com.intellij.lang.PsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

/*
 * Patern ::= Pattern1 {'|' Pattern1}
 */

object Pattern {
  def parse(builder: PsiBuilder): Boolean = {
    val patternMarker = builder.mark
    if (!Pattern1.parse(builder)) {
      patternMarker.drop
      return false
    }
    var isComposite = false
    while (builder.getTokenText == "|") {
      isComposite = true
      builder.advanceLexer //Ate |
      if (!Pattern1.parse(builder)) {
        builder error ScalaBundle.message("wrong.pattern")
      }
    }
    if (isComposite) patternMarker.done(ScalaElementTypes.PATTERN)
    else patternMarker.drop
    return true
  }
}