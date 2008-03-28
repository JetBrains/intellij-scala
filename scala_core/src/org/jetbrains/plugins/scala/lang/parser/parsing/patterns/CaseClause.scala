package org.jetbrains.plugins.scala.lang.parser.parsing.patterns

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types._
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template._
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions._
import org.jetbrains.plugins.scala.lang.parser.bnf._

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
    if (!Block.parse(builder,false)) {
      builder error ErrMsg("wrong.expression")
    }
    caseClauseMarker.done(ScalaElementTypes.CASE_CLAUSE)
    return true
  }
}