package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package base

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes

/**
* @author Alexander Podkhalyuzin
* Date: 15.02.2008
*/

/*
 *  LocalModifier ::= abstract
 *                  | final
 *                  | sealed
 *                  | implicit
 *                  | lazy
 */

object LocalModifier {
  def parse(builder: PsiBuilder): Boolean = {
    builder.getTokenType match {
      case ScalaTokenTypes.kABSTRACT | ScalaTokenTypes.kFINAL
         | ScalaTokenTypes.kSEALED | ScalaTokenTypes.kIMPLICIT
         | ScalaTokenTypes.kLAZY => {
        builder.advanceLexer //Ate modifier
        return true
      }
      case _ => return false
    }
  }
}