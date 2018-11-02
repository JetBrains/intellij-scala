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
* Date: 11.02.2008
*/

/*
 * Dcl ::= [{Annotation} {Modifier}]
 *          ('val' ValDcl
 *         | 'var' VarDcl
 *         | 'def' FunDcl
 *         | 'type' {nl} TypeDcl)
 */
object Dcl {

  def parse(builder: ScalaPsiBuilder, isMod: Boolean = true): Boolean = {
    val dclMarker = builder.mark
    dclMarker.setCustomEdgeTokenBinders(ScalaTokenBinders.PRECEEDING_COMMENTS_TOKEN, null)
    if (isMod) {
      val annotationsMarker = builder.mark
      while (Annotation.parse(builder)) {}
      annotationsMarker.done(ScalaElementType.ANNOTATIONS)
      annotationsMarker.setCustomEdgeTokenBinders(ScalaTokenBinders.DEFAULT_LEFT_EDGE_BINDER, null)
      //parse modifiers
      val modifierMarker = builder.mark
      while (Modifier.parse(builder)) {}
      modifierMarker.done(ScalaElementType.MODIFIERS)
    } else {
      //empty annotations
      val annotationsMarker = builder.mark
      annotationsMarker.done(ScalaElementType.ANNOTATIONS)
      //empty modifiers
      val modifierMarker = builder.mark
      modifierMarker.done(ScalaElementType.MODIFIERS)
    }
    //Look for val,var,def or type
    builder.getTokenType match {
      case ScalaTokenTypes.kVAL =>
        if (ValDcl parse builder) {
          dclMarker.done(ScalaElementType.VALUE_DECLARATION)
          true
        }
        else {
          dclMarker.rollbackTo()
          false
        }
      case ScalaTokenTypes.kVAR =>
        if (VarDcl parse builder) {
          dclMarker.done(ScalaElementType.VARIABLE_DECLARATION)
          true
        }
        else {
          dclMarker.drop()
          false
        }
      case ScalaTokenTypes.kDEF =>
        if (FunDcl parse builder) {
          dclMarker.done(ScalaElementType.FUNCTION_DECLARATION)
          true
        }
        else {
          dclMarker.drop()
          false
        }
      case ScalaTokenTypes.kTYPE =>
        if (TypeDcl parse builder) {
          dclMarker.done(ScalaElementType.TYPE_DECLARATION)
          true
        }
        else {
          dclMarker.drop()
          false
        }
      case _ =>
        dclMarker.rollbackTo()
        false
    }
  }
}