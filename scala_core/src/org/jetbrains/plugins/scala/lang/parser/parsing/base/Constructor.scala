package org.jetbrains.plugins.scala.lang.parser.parsing.base

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableId

import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Exprs
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableId
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.parsing.types.AnnotType
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.ArgumentExprs

import com.intellij.psi.tree.TokenSet
import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 15.02.2008
* Time: 11:58:40
* To change this template use File | Settings | File Templates.
*/

/*
 * Constr ::= AnnotType {ArgumentExprs}
 */

object Constructor {
  def parse(builder: PsiBuilder): Boolean ={
    val constrMarker = builder.mark
    if (!AnnotType.parse(builder)) {
      builder error ScalaBundle.message("identifier.expected", new Array[Object](0))
      constrMarker.done(ScalaElementTypes.CONSTRUCTOR)
      return true
    }
    val argExprsMarker = builder.mark

    while (builder.getTokenType == ScalaTokenTypes.tLPARENTHESIS) {
      ArgumentExprs parse builder
    }
    argExprsMarker.done(ScalaElementTypes.ARG_EXPRS_LIST)
    constrMarker.done(ScalaElementTypes.CONSTRUCTOR)
    return true
  }
}