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
 * ClassParamClause ::= [nl] '(' 'implicit' ClassParam {',' ClassParam} ')'
 */
object ImplicitClassParamClause extends ImplicitClassParamClause {
  override protected val classParam = ClassParam
}

trait ImplicitClassParamClause {
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
        //Look for implicit
        builder.getTokenType match {
          case ScalaTokenTypes.kIMPLICIT => {
            //It's ok
            builder.advanceLexer //Ate implicit
          }
          case _ => {
            builder error ErrMsg("wrong.parameter")
          }
        }
        //ok, let's parse parameters
        if (!(classParam parse builder)) {
          classParamMarker.rollbackTo
          builder.restoreNewlinesState
          return false
        }
        while (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
          builder.advanceLexer //Ate ,
          if (!(classParam parse builder)) {
            classParamMarker.rollbackTo
            builder.restoreNewlinesState
            return false
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
        builder.restoreNewlinesState
        classParamMarker.rollbackTo
        return false
    }
  }
}