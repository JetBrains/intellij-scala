package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top.params

import _root_.org.jetbrains.plugins.scala.lang.parser.parsing.expressions.{Annotation, Expr}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifier
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.ParamType

/**
* @author Alexander Podkhalyuzin
* Date: 08.02.2008
*/

/*
 * ClassParam ::= {Annotation} [{Modifier} ('val' | 'var')] id ':' ParamType ['=' Expr]
 */
object ClassParam extends ClassParam {
  override protected val expr = Expr
  override protected val annotation = Annotation
  override protected val paramType = ParamType
}

trait ClassParam {
  protected val expr: Expr
  protected val annotation: Annotation
  protected val paramType: ParamType

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val classParamMarker = builder.mark
    val annotationsMarker = builder.mark
    while (annotation.parse(builder)) {}
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
           ScalaTokenTypes.kVAL =>
        builder.advanceLexer() //Let's ate this!
      case _ =>
        if (isModifier) {
          builder error ScalaBundle.message("val.var.expected")
        }
    }
    //Look for identifier
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER =>
        builder.advanceLexer() //Ate identifier
      case _ =>
        classParamMarker.rollbackTo()
        return false
    }
    //Try to parse tale
    builder.getTokenType match {
      case ScalaTokenTypes.tCOLON =>
        builder.advanceLexer() //Ate ':'
        if (!paramType.parse(builder)) {
          builder.error(ScalaBundle.message("parameter.type.expected"))
        }
      case _ =>
        builder.error(ScalaBundle.message("colon.expected"))
    }

    //default param
    builder.getTokenType match {
      case ScalaTokenTypes.tASSIGN =>
        builder.advanceLexer() //Ate '='
        if (!expr.parse(builder)) {
          builder error ScalaBundle.message("wrong.expression")
        }
      case _ =>
    }
    classParamMarker.done(ScalaElementTypes.CLASS_PARAM)
    true
  }
}