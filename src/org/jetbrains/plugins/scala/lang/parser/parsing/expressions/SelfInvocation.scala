package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import builder.ScalaPsiBuilder

/**
 * @author Alexander Podkhalyuzin
 *  Date: 13.02.2008
 */

/*
 * SelfInvocation ::= 'this' ArgumentExprs {ArgumentExprs}
 */

object SelfInvocation {

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val selfMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kTHIS => {
        builder.advanceLexer //Ate this
      }
      case _ => {
        //todo[ilyas] provide aspect to suppress this inspection for compiled files
        //builder error ScalaBundle.message("this.expected")
        selfMarker.drop
        return true
      }
    }
    if (!ArgumentExprs.parse(builder)) {
      builder error ScalaBundle.message("arg.expr.expected")
    }
    while (!builder.newlineBeforeCurrentToken && ArgumentExprs.parse(builder)) {}
    selfMarker.done(ScalaElementTypes.SELF_INVOCATION)
    return true
  }
}