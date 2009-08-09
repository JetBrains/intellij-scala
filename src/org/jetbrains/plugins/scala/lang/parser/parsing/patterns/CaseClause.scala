package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package patterns

import com.intellij.lang.PsiBuilder
import expressions.Block
import lexer.ScalaTokenTypes

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

/*
 *  CaseClause ::= 'case' Pattern [Guard] '=>' Block
 */

object CaseClause {
  def parse(builder: PsiBuilder): Boolean = {
    val caseClauseMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kCASE => {
        builder.advanceLexer
      }
      case _ => {
        caseClauseMarker.drop
        return false
      }
    }
    if (!Pattern.parse(builder)) builder error ErrMsg("pattern.expected")
    builder.getTokenType match {
      case ScalaTokenTypes.kIF => {
        Guard parse builder
      }
      case _ => {}
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tFUNTYPE => {
        builder.advanceLexer
      }
      case _ => {
        builder error ErrMsg("fun.sign.expected")
        caseClauseMarker.done(ScalaElementTypes.CASE_CLAUSE)
        return true
      }
    }
    if (!Block.parse(builder, false)) {
      builder error ErrMsg("wrong.expression")
    }
    caseClauseMarker.done(ScalaElementTypes.CASE_CLAUSE)
    return true
  }
}