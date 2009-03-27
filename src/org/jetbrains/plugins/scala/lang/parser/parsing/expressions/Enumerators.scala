package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import patterns.Guard

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * Enumerators ::= Generator {semi Enumerator}
 */

object Enumerators {
  def parse(builder:PsiBuilder): Boolean = {
    val enumsMarker = builder.mark
    if (!Generator.parse(builder)) {
      enumsMarker.drop
      return false
    }
    var exit = true
    while (exit) {
      val guard = builder.getTokenType match {
        case ScalaTokenTypes.tSEMICOLON | ScalaTokenTypes.tLINE_TERMINATOR =>
          builder.advanceLexer
          false
        case _ if Guard.parse(builder) => true
        case _ => exit = false; true
      }
      if (!guard && !Enumerator.parse(builder)) exit = false
    }
    enumsMarker.done(ScalaElementTypes.ENUMERATORS)
    return true
  }
}