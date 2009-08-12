package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top.params

import _root_.org.jetbrains.plugins.scala.lang.parser.parsing.expressions.{Expr, Annotation}
import base.Modifier
import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import types.ParamType

/**
* @author Alexander Podkhalyuzin
* Date: 08.02.2008
*/

/*
 * ClassParam ::= {Annotation} [{Modifier} ('val' | 'var')] id ':' ParamType ['=' Expr]
 */

object ClassParam {
  def parse(builder: PsiBuilder): Boolean = {
    val classParamMarker = builder.mark
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
    //Look for var or val
    builder.getTokenType match {
      case ScalaTokenTypes.kVAR |
           ScalaTokenTypes.kVAL => {
        builder.advanceLexer //Let's ate this!
      }
      case _ => {
        if (isModifier) {
          builder error ScalaBundle.message("val.var.expected")
        }
      }
    }
    //Look for identifier
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        builder.advanceLexer //Ate identifier
      }
      case _ => {
        classParamMarker.rollbackTo
        return false
      }
    }
    //Try to parse tale
    builder.getTokenType match {
      case ScalaTokenTypes.tCOLON => {
        builder.advanceLexer //Ate ':'
        if (!ParamType.parse(builder)) {
          builder.error(ScalaBundle.message("parameter.type.expected"))
        }
      }
      case _ => {
        builder.error(ScalaBundle.message("colon.expected"))
      }
    }

    //default param
    builder.getTokenType match {
      case ScalaTokenTypes.tASSIGN => {
        builder.advanceLexer //Ate '='
        if (!Expr.parse(builder)) {
          builder error ScalaBundle.message("wrong.expression")
        }
      }
      case _ =>
    }
    classParamMarker.done(ScalaElementTypes.CLASS_PARAM)
    return true
  }
}