package org.jetbrains.plugins.scala.lang.parser.parsing.patterns

import com.intellij.lang.WhitespacesAndCommentsBinder
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType.{ClassKeyword, ObjectKeyword}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.{Block, BlockInIndentationRegion, ExprInIndentationRegion}
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.CaseClause.RightCommentBinder
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, ScalaElementType}

import java.{util => ju}
import scala.jdk.CollectionConverters.ListHasAsScala

/*
 *  CaseClause ::= 'case' Pattern [Guard] '=>' Block
 */
abstract class CaseClause extends ParsingRule {
  protected def parseBody()(implicit builder: ScalaPsiBuilder): Unit

  protected def isCaseKeywordAcceptable(implicit builder: ScalaPsiBuilder): Boolean =
    true
  /**
   * Detects Scala 3 nested partial-function result after `case ... =>` {{{
   *   case "foo" =>
   *     case 1 => true
   * }}}
   *
   * The inner `case` should be treated as the result expression of the outer case
   * only when it starts a new braceless indentation region.
   */
  protected final def startsNestedPartialFunctionResult(builder: ScalaPsiBuilder): Boolean =
    builder.isScala3IndentationBasedSyntaxEnabled &&
      builder.getTokenType == ScalaTokenTypes.kCASE &&
      !builder.lookAhead(1, ClassKeyword) &&
      !builder.lookAhead(1, ObjectKeyword) &&
      builder.newBracelessIndentationRegionHere.nonEmpty

  /**
   * Parses nested case clauses as a single expression in the indentation region.<br>
   * Used when [[startsNestedPartialFunctionResult]] is true.
   */
  protected final def parseNestedPartialFunctionResult()(implicit builder: ScalaPsiBuilder): Unit = {
    if (!ExprInIndentationRegion()) {
      builder.wrongExpressionError()
    }
  }

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val caseClauseMarker = builder.mark()
    if (!builder.isScala3IndentationBasedSyntaxEnabled) {
      // This will bind all comments and whitespaces right of the clause.
      caseClauseMarker.setCustomEdgeTokenBinders(null, RightCommentBinder)
    }
    builder.getTokenType match {
      case ScalaTokenTypes.kCASE if isCaseKeywordAcceptable =>
        builder.advanceLexer()
        builder.disableNewlines()
      case _ =>
        caseClauseMarker.drop()
        return false
    }
    if (!Pattern())
      builder.error(ErrMsg("pattern.expected"))
    builder.getTokenType match {
      case ScalaTokenTypes.kIF =>
        Guard()
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
    parseBody()
    caseClauseMarker.done(ScalaElementType.CASE_CLAUSE)
    true
  }
}

/**
 * Generic case-clause parser used in regular (mostly braced) case-clauses contexts.
 *
 * Example:
 * {{{
 * x match {
 *   case a =>
 *     expr
 *   case b =>
 *     expr2
 * }
 * }}}
 */
object CaseClause extends CaseClause {
  private val RightCommentBinder: WhitespacesAndCommentsBinder =
    (tokens: ju.List[_ <: IElementType], _: Boolean, _: WhitespacesAndCommentsBinder.TokenTextGetter) => {
      tokens.size() - tokens.asScala.reverseIterator.takeWhile(token => !ScalaTokenTypes.COMMENTS_TOKEN_SET.contains(token)).length
    }

  override protected def parseBody()(implicit builder: ScalaPsiBuilder): Unit = {
    val startsNestedPartialFunction = startsNestedPartialFunctionResult(builder)
    if (startsNestedPartialFunction) {
      // In Scala 3, an extra-indented `case` after `=>` can start a nested partial function.
      parseNestedPartialFunctionResult()
    } else {
      builder.withIndentationRegion(builder.newBracelessIndentationRegionHere) {
        if (!Block.Braceless(stopOnOutdent = false, needNode = true)) {
          builder.wrongExpressionError()
        }
      }
    }
  }
}

/**
 * This is used in Scala 3 braceless syntax in match and catch clauses like
 *
 * {{{
 * x match
 * case a =>
 *   stmt1
 *   stmt2
 * case b =>
 * stmtAfterMatch
 * }}}
 */
object CaseClauseInBracelessCaseClauses extends CaseClause {
  override protected def parseBody()(implicit builder: ScalaPsiBuilder): Unit =
    builder.withEnabledNewlines {
      val startsNestedPartialFunction = startsNestedPartialFunctionResult(builder)
      if (startsNestedPartialFunction) {
        // Nested partial-function branch (see startsNestedPartialFunctionResult).
        parseNestedPartialFunctionResult()
      } else {
        // Regular braceless case body branch.
        builder.withIndentationRegion(builder.newBracelessIndentationRegionHere) {
          BlockInIndentationRegion()
        }
      }
    }

  override protected def isCaseKeywordAcceptable(implicit builder: ScalaPsiBuilder): Boolean = {
    // using `forall`, not `exists` because if there is no new line before case, it still can be allowed, e.g. here:
    // 1 match
    //   case 1 => 11 case 2 => 22
    !builder.isOutdentForCaseKeywordInCaseClause
  }
}

/**
 * This is a special case in Scala 3's catch syntax.
 * You can have the case directly behind the catch in the same line and then
 * one expression in the case's body. Which of course can also be an
 * expression in an indentation region
 *
 * {{{
 * try ??? catch case _ => expr
 * }}}
 * OR
 *
 * {{{
 * try ??? catch case _ =>
 *   stmt1
 *   stmt2
 * stmtAfterTry
 * }}}
 */
object ExprCaseClause extends CaseClause {
  override protected def parseBody()(implicit builder: ScalaPsiBuilder): Unit = {
    if (!ExprInIndentationRegion()) {
      builder error ErrMsg("expression.expected")
    }
  }
}
