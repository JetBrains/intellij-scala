package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package params

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import types.Type

/**
 * @author Alexander Podkhalyuzin
 * Date: 06.03.2008
 */

/*
 * TypeParam ::= (id | '_') [TypeParamClause] ['>:' Type] ['<:'Type] {'<%' Type} {':' Type}
 */

object TypeParam {
  def parse(builder: PsiBuilder, mayHaveVariance: Boolean): Boolean = {
    val paramMarker = builder.mark
    if (mayHaveVariance) {
      builder.getTokenText match {
        case "+" | "-" => builder.advanceLexer
        case _ =>
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.tUNDER => {
        builder.advanceLexer //Ate identifier
      }
      case _ => {
        paramMarker.rollbackTo
        return false
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tLSQBRACKET => {
        TypeParamClause parse builder
      }
      case _ =>
    }

    val boundParser = parseBound(builder) _
    boundParser(">:")
    boundParser("<:")
    while (boundParser("<%")) {}
    while (boundParser(":")) {}

    paramMarker.done(ScalaElementTypes.TYPE_PARAM)
    return true
  }

  def parseBound(builder: PsiBuilder)(bound: String): Boolean = {
    builder.getTokenText match {
      case x if x == bound => {
        builder.advanceLexer
        if (!Type.parse(builder)) builder error ErrMsg("wrong.type")
        true
      }
      case _ => false
    }
  }
}