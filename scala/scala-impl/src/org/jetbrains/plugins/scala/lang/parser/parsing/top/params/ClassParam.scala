package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top
package params

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifier
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.{Annotations, Expr}
import org.jetbrains.plugins.scala.lang.parser.parsing.types.ParamType

/**
 * [[ClassParam]] ::= [[Annotations]] [{Modifier} ('val' | 'var')] id ':' ParamType ['=' Expr]
 *
* @author Alexander Podkhalyuzin
* Date: 08.02.2008
*/
object ClassParam {

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val classParamMarker = builder.mark

    Annotations()(builder)

    //parse modifiers
    val modifierMarker = builder.mark
    var isModifier = false
    while (Modifier.parse(builder)) {
      isModifier = true
    }
    modifierMarker.done(ScalaElementType.MODIFIERS)
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
        if (!ParamType.parse(builder)) {
          builder.error(ScalaBundle.message("parameter.type.expected"))
        }
      case _ =>
        builder.error(ScalaBundle.message("colon.expected"))
    }

    //default param
    builder.getTokenType match {
      case ScalaTokenTypes.tASSIGN =>
        builder.advanceLexer() //Ate '='
        if (!Expr.parse(builder)) {
          builder error ScalaBundle.message("wrong.expression")
        }
      case _ =>
    }
    classParamMarker.done(ScalaElementType.CLASS_PARAM)
    true
  }
}