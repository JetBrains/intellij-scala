package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package patterns

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.{Block, BlockInIndentationRegion, Expr, ExprInIndentationRegion}

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

/*
 *  CaseClause ::= 'case' Pattern [Guard] '=>' Block
 */
abstract class CaseClause extends ParsingRule {
  protected def parseBody(builder: ScalaPsiBuilder): Unit

  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    val caseClauseMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kCASE =>
        builder.advanceLexer()
        builder.disableNewlines()
      case _ =>
        caseClauseMarker.drop()
        return false
    }
    if (!Pattern.parse(builder)) builder error ErrMsg("pattern.expected")
    builder.getTokenType match {
      case ScalaTokenTypes.kIF =>
        Guard parse builder
      case _ =>
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tFUNTYPE =>
        builder.advanceLexer()
        builder.restoreNewlinesState()
      case _ =>
        builder.restoreNewlinesState()
        builder error ErrMsg("fun.sign.expected")
        caseClauseMarker.done(ScalaElementType.CASE_CLAUSE)
        return true
    }
    parseBody(builder)
    caseClauseMarker.done(ScalaElementType.CASE_CLAUSE)
    true
  }
}

object CaseClause extends CaseClause {
  override protected def parseBody(builder: ScalaPsiBuilder): Unit = {
    if (!Block.parse(builder, hasBrace = false, needNode = true)) {
      builder error ErrMsg("wrong.expression")
    }
  }
}

/**
 * This is used in Scala 3 braceless syntax in match and catch clauses like
 *
 * x match
 * case a =>
 *   stmt1
 *   stmt2
 * case b =>
 * stmtAfterMatch
 */

object CaseClauseInBracelessCaseClauses extends CaseClause {
  override protected def parseBody(builder: ScalaPsiBuilder): Unit = {
    BlockInIndentationRegion.parse(builder)
  }
}

/**
 * This is a special case in Scala 3's catch syntax.
 * You can have the case directly behind the catch in the same line and then
 * one expression in the case's body. Which of course can also be an
 * expression in an indentation region
 *
 * try ??? catch case _ => expr
 *
 * or
 *
 * try ??? catch case _ =>
 *   stmt1
 *   stmt2
 *
 * stmtAfterTry
 */
object ExprCaseClause extends CaseClause {
  override protected def parseBody(builder: ScalaPsiBuilder): Unit = {
    if (!ExprInIndentationRegion.parse(builder)) {
      builder error ErrMsg("expression.expected")
    }
  }
}