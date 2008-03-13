package org.jetbrains.plugins.scala.lang.parser.parsing.params

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.PsiBuilder

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type
import org.jetbrains.plugins.scala.lang.parser.parsing.Constr
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.util.DebugPrint


/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 06.03.2008
* Time: 16:24:10
* To change this template use File | Settings | File Templates.
*/

/*
 * TypeParam ::= (id | '_') [TypeParamClause] ['>:' Type] ['<:' Type] ['<%' Type]
 */

object TypeParam {
  def parse(builder: PsiBuilder): Boolean = {
    val paramMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.tUNDER => {
        builder.advanceLexer //Ate identifier
      }
      case _ => {
        paramMarker.drop
        return false
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tLSQBRACKET => {
        TypeParamClause parse builder
      }
      case _ => {}
    }
    builder.getTokenText match {
      case ">:" => {
        builder.advanceLexer
        if (!Type.parse(builder)) builder error ScalaBundle.message("wrong.type", new Array[Object](0))
      }
      case _ => {}
    }
    builder.getTokenText match {
      case "<:" => {
        builder.advanceLexer
        if (!Type.parse(builder)) builder error ScalaBundle.message("wrong.type", new Array[Object](0))
      }
      case _ => {}
    }
    builder.getTokenText match {
      case "<%" => {
        builder.advanceLexer
        if (!Type.parse(builder)) builder error ScalaBundle.message("wrong.type", new Array[Object](0))
      }
      case _ => {}
    }
    paramMarker.done(ScalaElementTypes.TYPE_PARAM)
    return true
  }
}