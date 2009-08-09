package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import base.Modifier
import com.intellij.lang.PsiBuilder
import expressions.Annotation
import lexer.ScalaTokenTypes

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
  def parse(builder: PsiBuilder): Boolean = parse(builder,true)
  def parse(builder: PsiBuilder, isMod: Boolean): Boolean = {
    val dclMarker = builder.mark
    if (isMod) {
      val annotationsMarker = builder.mark
      while (Annotation.parse(builder)) {}
      annotationsMarker.done(ScalaElementTypes.ANNOTATIONS)
      //parse modifiers
      val modifierMarker = builder.mark
      var isModifier = false
      while (Modifier.parse(builder)) {
        isModifier = true
      }
      modifierMarker.done(ScalaElementTypes.MODIFIERS)
    }
    //Look for val,var,def or type
    builder.getTokenType match {
      case ScalaTokenTypes.kVAL => {
        if (ValDcl parse builder) {
          dclMarker.done(ScalaElementTypes.VALUE_DECLARATION)
          return true
        }
        else {
          dclMarker.rollbackTo
          return false
        }
      }
      case ScalaTokenTypes.kVAR => {
        if (VarDcl parse builder) {
          dclMarker.done(ScalaElementTypes.VARIABLE_DECLARATION)
          return true
        }
        else {
          dclMarker.drop
          return false
        }
      }
      case ScalaTokenTypes.kDEF => {
        if (FunDcl parse builder) {
          dclMarker.done(ScalaElementTypes.FUNCTION_DECLARATION)
          return true
        }
        else {
          dclMarker.drop
          return false
        }
      }
      case ScalaTokenTypes.kTYPE => {
        if (TypeDcl parse builder) {
          dclMarker.done(ScalaElementTypes.TYPE_DECLARATION)
          return true
        }
        else {
          dclMarker.drop
          return false
        }
      }
      case _ => {
        dclMarker.rollbackTo
        return false
      }
    }
  }
}