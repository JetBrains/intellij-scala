package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import base.Modifier
import expressions.Annotation
import lexer.ScalaTokenTypes
import builder.ScalaPsiBuilder

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
  def parse(builder: ScalaPsiBuilder): Boolean = parse(builder,true)
  def parse(builder: ScalaPsiBuilder, isMod: Boolean): Boolean = {
    val dclMarker = builder.mark
    if (isMod) {
      val annotationsMarker = builder.mark
      while (Annotation.parse(builder)) {}
      annotationsMarker.done(ScalaElementTypes.ANNOTATIONS)
      //parse modifiers
      val modifierMarker = builder.mark
      while (Modifier.parse(builder)) {}
      modifierMarker.done(ScalaElementTypes.MODIFIERS)
    } else {
      //empty annotations
      val annotationsMarker = builder.mark
      annotationsMarker.done(ScalaElementTypes.ANNOTATIONS)
      //empty modifiers
      val modifierMarker = builder.mark
      modifierMarker.done(ScalaElementTypes.MODIFIERS)
    }
    //Look for val,var,def or type
    builder.getTokenType match {
      case ScalaTokenTypes.kVAL => {
        if (ValDcl parse builder) {
          dclMarker.done(ScalaElementTypes.VALUE_DECLARATION)
          true
        }
        else {
          dclMarker.rollbackTo()
          false
        }
      }
      case ScalaTokenTypes.kVAR => {
        if (VarDcl parse builder) {
          dclMarker.done(ScalaElementTypes.VARIABLE_DECLARATION)
          true
        }
        else {
          dclMarker.drop()
          false
        }
      }
      case ScalaTokenTypes.kDEF => {
        if (FunDcl parse builder) {
          dclMarker.done(ScalaElementTypes.FUNCTION_DECLARATION)
          true
        }
        else {
          dclMarker.drop()
          false
        }
      }
      case ScalaTokenTypes.kTYPE => {
        if (TypeDcl parse builder) {
          dclMarker.done(ScalaElementTypes.TYPE_DECLARATION)
          true
        }
        else {
          dclMarker.drop()
          false
        }
      }
      case _ => {
        dclMarker.rollbackTo()
        false
      }
    }
  }
}