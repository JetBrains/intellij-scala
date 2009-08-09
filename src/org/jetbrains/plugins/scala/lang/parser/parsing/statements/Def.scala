package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import base.Modifier
import com.intellij.lang.PsiBuilder
import expressions.Annotation
import lexer.ScalaTokenTypes
import top.TmplDef

/**
* @author Alexander Podkhalyuzin
* Date: 11.02.2008
*/

/*
 * Def ::= [{Annotation} {Modifier}]
 *          ('val' ValDef
 *         | 'var' VarDef
 *         | 'def' FunDef
 *         | 'type' {nl} TypeDef)
 */

object Def {
  def parse(builder: PsiBuilder): Boolean = parse(builder,true)
  def parse(builder: PsiBuilder, isMod: Boolean): Boolean = parse(builder,isMod,false)
  def parse(builder: PsiBuilder, isMod: Boolean,isImplicit: Boolean): Boolean = {
    val defMarker = builder.mark
    if (isMod || isImplicit) {
      val annotationsMarker = builder.mark
      while (Annotation.parse(builder)) {}
      annotationsMarker.done(ScalaElementTypes.ANNOTATIONS)
      //parse modifiers
      val modifierMarker = builder.mark
      if (isMod) {
        while (Modifier.parse(builder)) {}
      }
      else {
        def getAll() {
          builder.getTokenType match {
            case ScalaTokenTypes.kIMPLICIT | ScalaTokenTypes.kLAZY => {
              builder.advanceLexer //Ate implicit
              getAll()
            }
            case _ => return
          }
        }
        getAll()
      }
      modifierMarker.done(ScalaElementTypes.MODIFIERS)
    } else {
      val modifierMarker = builder.mark
      modifierMarker.done(ScalaElementTypes.MODIFIERS)
    }
    //Look for val,var,def or type
    builder.getTokenType match {
      case ScalaTokenTypes.kVAL => {
        builder.advanceLexer //Ate val
        if (PatDef parse builder) {
          defMarker.done(ScalaElementTypes.PATTERN_DEFINITION)
          return true
        }
        else {
          defMarker.rollbackTo
          return false
        }
      }
      case ScalaTokenTypes.kVAR => {
        builder.advanceLexer //Ate var
        if (VarDef parse builder) {
          defMarker.done(ScalaElementTypes.VARIABLE_DEFINITION)
          return true
        }
        else {
          defMarker.rollbackTo
          return false
        }
      }
      case ScalaTokenTypes.kDEF => {
        if (FunDef parse builder) {
          defMarker.done(ScalaElementTypes.FUNCTION_DEFINITION)
          return true
        }
        else {
          defMarker.rollbackTo
          return false
        }
      }
      case ScalaTokenTypes.kTYPE => {
        if (TypeDef parse builder) {
          defMarker.done(ScalaElementTypes.TYPE_DEFINITION)
          return true
        }
        else {
          defMarker.rollbackTo
          return false
        }
      }
      case ScalaTokenTypes.kCASE | ScalaTokenTypes.kCLASS
      | ScalaTokenTypes.kOBJECT | ScalaTokenTypes.kTRAIT => {
        defMarker.rollbackTo
        if (TmplDef parse builder) {
          return true
        }
        else {
          return false
        }
      }
      case _ => {
        defMarker.rollbackTo
        return false
      }
    }
  }
}

