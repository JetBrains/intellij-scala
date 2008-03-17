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
* Time: 16:24:18
* To change this template use File | Settings | File Templates.
*/

/*
 * ParamClauses ::= {ParamClause} [ImplicitParamClause]
 */

object ParamClauses {
  def parse(builder: PsiBuilder): Boolean = parse(builder,false)
  def parse(builder: PsiBuilder,flag: Boolean): Boolean = {
    val paramMarker = builder.mark
    if (flag) {
      if (!ParamClause.parse(builder)) {
        builder error ScalaBundle.message("param.clause.expected", new Array[Object](0))
      }
    }
    while (ParamClause.parse(builder)) {}
    ImplicitParamClause parse builder
    paramMarker.done(ScalaElementTypes.PARAM_CLAUSES)
    return true
  }
}