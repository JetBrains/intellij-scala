package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifier
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Annotation

/**
* @author Alexander Podkhalyuzin
* Date: 06.02.2008
*/

/*
 * PatVarDef ::= {Annotation} {Modifier} 'val' PatDef |
 *               {Annotation} {Modifier} 'var' VarDef
 */

object PatVarDef {
  def parse(builder: ScalaPsiBuilder):Boolean = {
    val patVarMarker = builder.mark
    val annotationsMarker = builder.mark
    while (Annotation.parse(builder)) {}
    annotationsMarker.done(ScalaElementTypes.ANNOTATIONS)
    //parse modifiers
    val modifierMarker = builder.mark
    while (Modifier.parse(builder)) {}
    modifierMarker.done(ScalaElementTypes.MODIFIERS)
    builder.getTokenType match {
      case ScalaTokenTypes.kVAL => {
        builder.advanceLexer //Ate val
        if (PatDef parse builder) {
          patVarMarker.done(ScalaElementTypes.PATTERN_DEFINITION)
          return true
        }
        else {
          patVarMarker.rollbackTo
          return false
        }
      }
      case ScalaTokenTypes.kVAR => {
        builder.advanceLexer //Ate var
        if (VarDef parse builder) {
          patVarMarker.done(ScalaElementTypes.VARIABLE_DEFINITION)
          return true
        }
        else {
          patVarMarker.rollbackTo
          return false
        }
      }
      case _ => {
        patVarMarker.rollbackTo
        return false
      }
    }
  }
}