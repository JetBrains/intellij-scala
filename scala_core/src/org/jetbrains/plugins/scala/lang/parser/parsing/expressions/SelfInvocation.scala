package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import bnf.BNF
import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes

/**
 * @author Alexander Podkhalyuzin
 *  Date: 13.02.2008
 */

/*
 * SelfInvocation ::= 'this' ArgumentExprs {ArgumentExprs}
 */

object SelfInvocation {

  def parse(builder: PsiBuilder): Boolean = {
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
    val argExprsMarker = builder.mark
    var numberOfArgExprs = 0;

    if (BNF.firstArgumentExprs.contains(builder.getTokenType)) {
      ArgumentExprs parse builder
      numberOfArgExprs = 1
    }
    else {
      builder error ScalaBundle.message("arg.expr.expected")
    }

    while (builder.getTokenType == ScalaTokenTypes.tLPARENTHESIS) {
      ArgumentExprs parse builder
      numberOfArgExprs = numberOfArgExprs + 1
    }

    argExprsMarker.drop
    selfMarker.done(ScalaElementTypes.SELF_INVOCATION)
    return true
  }
}