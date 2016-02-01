package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top.params

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 08.02.2008
*/

/*
 * ClassParamClause ::= [nl] '(' [ClassParam {',' ClassParam}] ')'
 */
object ClassParamClause extends ClassParamClause {
  override protected val classParam = ClassParam
}

trait ClassParamClause {
  protected val classParam: ClassParam

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val classParamMarker = builder.mark
    if (builder.twoNewlinesBeforeCurrentToken) {
      classParamMarker.rollbackTo()
      return false
    }
    //Look for '('
    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS =>
        builder.advanceLexer //Ate '('
        builder.disableNewlines
        builder.getTokenType match {
          case ScalaTokenTypes.kIMPLICIT => {
            classParamMarker.rollbackTo
            builder.restoreNewlinesState
            return false
          }
          case _ => {}
        }
        //ok, let's parse parameters
        if (classParam parse builder) {
          while (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
            builder.advanceLexer //Ate ,
            if (!(classParam parse builder)) {
              builder error ErrMsg("wrong.parameter")
            }
          }
        }
      case _ =>
        classParamMarker.rollbackTo
        return false
    }
    //Look for ')'
    builder.getTokenType match {
      case ScalaTokenTypes.tRPARENTHESIS =>
        builder.advanceLexer //Ate )
        builder.restoreNewlinesState
        classParamMarker.done(ScalaElementTypes.PARAM_CLAUSE)
        return true
      case _ =>
        classParamMarker.done(ScalaElementTypes.PARAM_CLAUSE)
        builder error ErrMsg("rparenthesis.expected")
        builder.restoreNewlinesState
        return true
    }
  }
}