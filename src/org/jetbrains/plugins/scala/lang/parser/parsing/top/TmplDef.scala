package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import base.Modifier
import com.intellij.lang.PsiBuilder
import expressions.Annotation
import lexer.ScalaTokenTypes

/**
* @author Alexander Podkhalyuzin
* Date: 05.02.2008
*/

/*
 * TmplDef ::= {Annotation} {Modifier}
            [case] class ClassDef
 *          | [case] object ObjectDef
 *          | trait TraitDef
 *
 */

object TmplDef {
  def parse(builder: PsiBuilder): Boolean = {
    val templateMarker = builder.mark
    val annotationsMarker = builder.mark
    while (Annotation.parse(builder)) {}
    annotationsMarker.done(ScalaElementTypes.ANNOTATIONS)
    //parsing modifiers
    val modifierMarker = builder.mark
    while (Modifier.parse(builder)) {}
    //could be case modifier
    val caseMarker = builder.mark
    if (builder.getTokenType == ScalaTokenTypes.kCASE)
      builder.advanceLexer //Ate case
    //parsing template body
    builder.getTokenType match {
      case ScalaTokenTypes.kCLASS => {
        caseMarker.drop
        modifierMarker.done(ScalaElementTypes.MODIFIERS)
        builder.advanceLexer //Ate class
        if (ClassDef parse builder) {
          templateMarker.done(ScalaElementTypes.CLASS_DEF)
        } else {
          templateMarker.drop
        }
        return true
      }
      case ScalaTokenTypes.kOBJECT => {
        caseMarker.drop
        modifierMarker.done(ScalaElementTypes.MODIFIERS)
        builder.advanceLexer //Ate object
        if (ObjectDef parse builder) {
          templateMarker.done(ScalaElementTypes.OBJECT_DEF)
        } else {
          templateMarker.drop
        }
        return true
      }
      case ScalaTokenTypes.kTRAIT => {
        caseMarker.rollbackTo
        modifierMarker.done(ScalaElementTypes.MODIFIERS)
        builder.getTokenType match {
          case ScalaTokenTypes.kTRAIT => {
            builder.advanceLexer //Ate trait
            if (TraitDef.parse(builder)) {
              templateMarker.done(ScalaElementTypes.TRAIT_DEF)
            } else {
              templateMarker.drop
            }
            return true
          }
          // In this way wrong case modifier
          case _ => {
            builder error ErrMsg("wrong.case.modifier")
            builder.advanceLexer //Ate case
            builder.getTokenText
            builder.advanceLexer //Ate trait
            TraitDef.parse(builder)
            templateMarker.done(ScalaElementTypes.TRAIT_DEF)
            return true
          }
        }
      }
      //it's error
      case _ => {
        templateMarker.rollbackTo
        //builder.advanceLexer //Ate something
        return false
      }
    }
  }
}

