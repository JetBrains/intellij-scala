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
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 28.02.2008
* Time: 16:57:32
* To change this template use File | Settings | File Templates.
*/

/*
 *  CaseClauses ::= CaseClause {CaseClause}
 */

object CaseClauses {
  def parse(builder: PsiBuilder): Boolean = {
    val caseClausesMarker = builder.mark
    if (!CaseClause.parse(builder)) {
      caseClausesMarker.drop
      return false
    }
    while (CaseClause parse builder) {}
    caseClausesMarker.done(ScalaElementTypes.CASE_CLAUSES)
    return true
  }
}