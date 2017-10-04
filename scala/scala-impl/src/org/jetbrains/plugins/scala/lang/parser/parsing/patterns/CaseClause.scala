package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package patterns

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Block

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

/*
 *  CaseClause ::= 'case' Pattern [Guard] '=>' Block
 */
object CaseClause extends CaseClause {
  override protected def block = Block
  override protected def pattern = Pattern
  override protected def guard = Guard
}

trait CaseClause {
  protected def block: Block
  protected def guard: Guard
  protected def pattern: Pattern

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val caseClauseMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kCASE =>
        builder.advanceLexer()
        builder.disableNewlines
      case _ =>
        caseClauseMarker.drop()
        return false
    }
    if (!pattern.parse(builder)) builder error ErrMsg("pattern.expected")
    builder.getTokenType match {
      case ScalaTokenTypes.kIF =>
        guard parse builder
      case _ =>
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tFUNTYPE =>
        builder.advanceLexer()
        builder.restoreNewlinesState
      case _ =>
        builder.restoreNewlinesState
        builder error ErrMsg("fun.sign.expected")
        caseClauseMarker.done(ScalaElementTypes.CASE_CLAUSE)
        return true
    }
    if (!block.parse(builder, hasBrace = false, needNode = true)) {
      builder error ErrMsg("wrong.expression")
    }
    caseClauseMarker.done(ScalaElementTypes.CASE_CLAUSE)
    true
  }
}