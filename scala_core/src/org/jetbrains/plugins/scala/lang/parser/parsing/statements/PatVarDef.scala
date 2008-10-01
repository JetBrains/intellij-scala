package org.jetbrains.plugins.scala.lang.parser.parsing.statements

import base.Modifier
import bnf.BNF
import com.intellij.lang.PsiBuilder
import expressions.Annotation
import lexer.ScalaTokenTypes

/**
* @author Alexander Podkhalyuzin
* Date: 06.02.2008
*/

/*
 * PatVarDef ::= {Annotation} {Modifier} 'val' PatDef |
 *               {Annotation} {Modifier} 'var' VarDef
 */

object PatVarDef {
  def parse(builder: PsiBuilder):Boolean = {
    val patVarMarker = builder.mark
    val annotationsMarker = builder.mark
    while (Annotation.parse(builder)) {}
    annotationsMarker.done(ScalaElementTypes.ANNOTATIONS)
    //parse modifiers
    val modifierMarker = builder.mark
    while (BNF.firstModifier.contains(builder.getTokenType)) {
      Modifier.parse(builder)
    }
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