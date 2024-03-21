package org.jetbrains.plugins.scala.lang.parser.util

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ErrMsg
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Expr1
import org.jetbrains.plugins.scala.lang.parser.parsing.{Associativity, ParsingRule}
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils.{isAssignmentOperator, isSymbolicIdentifier, operatorAssociativity, priority}
import org.jetbrains.plugins.scala.lang.parser.util.PrecedenceClimbingInfixParsingRule.InfixStackElement

import scala.annotation.tailrec

abstract class PrecedenceClimbingInfixParsingRule extends ParsingRule {
  protected def parseFirstOperand()(implicit builder: ScalaPsiBuilder): Boolean

  protected def parseOperand()(implicit builder: ScalaPsiBuilder): Boolean

  protected def referenceElementType: IElementType

  protected def infixElementType: IElementType

  protected def isMatchConsideredInfix: Boolean

  protected def parseAfterOperatorId(opMarker: PsiBuilder.Marker)(implicit builder: ScalaPsiBuilder): Unit = ()

  final override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val isInlineMatch = builder.rawLookup(-2) == ScalaTokenType.InlineKeyword
    val matchStartMarker = builder.mark()
    val beforeFirstOperandMarker = builder.mark()

    if (!parseFirstOperand()) {
      beforeFirstOperandMarker.drop()
      matchStartMarker.drop()
      return false
    }

    def finishOpStack(opStack: List[InfixStackElement]): Unit =
      opStack.foreach(_.marker.done(infixElementType))

    @tailrec
    def continueWithOperatorAndOperand(beforePrevOperandMarker: PsiBuilder.Marker, opStack: List[InfixStackElement]): Unit = {
      // we are before an operator
      if (builder.getTokenType != ScalaTokenTypes.tIDENTIFIER || !shouldContinue) {
        beforePrevOperandMarker.drop()
        finishOpStack(opStack)
        return
      }

      //get operator because we need to know the precedence
      val opText = builder.getTokenText

      // pop operators with higher or equal precedence or different associativity
      var infixMarker = beforePrevOperandMarker
      val restOpStack = opStack.dropWhile {
        case InfixStackElement(op, marker) if !compar(opText, op, builder) =>
          infixMarker.drop()
          marker.done(infixElementType)
          infixMarker = marker.precede()
          true
        case _ =>
          false
      }

      // parse the operator but make sure we can rollback if the operand fails to parse
      val beforeOpMarker = builder.mark()
      val opMarker = builder.mark()
      builder.advanceLexer() //Ate id
      opMarker.done(referenceElementType)

      parseAfterOperatorId(opMarker)

      val beforeOperandMarker = builder.mark()

      // try to parse the operand
      if (builder.twoNewlinesBeforeCurrentToken || !parseOperand()) {
        beforeOpMarker.rollbackTo()
        infixMarker.drop()
        finishOpStack(restOpStack)
      } else {
        beforeOpMarker.drop()
        continueWithOperatorAndOperand(beforeOperandMarker, InfixStackElement(opText, infixMarker) :: restOpStack)
      }
    }

    continueWithOperatorAndOperand(beforeFirstOperandMarker, Nil)

    if (
      !isInlineMatch &&
        isMatchConsideredInfix &&
        builder.isScala3 && builder.getTokenType == ScalaTokenTypes.kMATCH &&
        !builder.isOutdentHere
    ) {
      Expr1.parseMatch(matchStartMarker)
    } else {
      matchStartMarker.drop()
    }
    true
  }

  // first-set of Expr()
  private val startsExpression = {
    import ScalaTokenType._
    import ScalaTokenTypes._
    Set(
      tLBRACE, tLPARENTHESIS,
      tIDENTIFIER, tUNDER,
      tCHAR, tSYMBOL,
      tSTRING, tWRONG_STRING, tMULTILINE_STRING, tINTERPOLATED_STRING,
      kDO, kFOR, kWHILE, kIF, kTRY,
      kNULL, kTRUE, kFALSE, kTHROW, kRETURN, kSUPER,
      Long, Integer, Double, Float,
      NewKeyword,
      InlineKeyword, SpliceStart, QuoteStart
    )
  }

  protected def shouldContinue(implicit builder: ScalaPsiBuilder): Boolean =
    !builder.newlineBeforeCurrentToken || {
      if (builder.features.`leading infix operator`) {
        // In scala 3 the infix operator may be on the next line
        // but only if it is a leading infix operator. (https://dotty.epfl.ch/docs/reference/changed-features/operators.html)
        // A leading infix operator is
        // - a symbolic identifier such as +, or approx_==, or an identifier in backticks that
        //   starts a new line, and
        // - is not following a blank line, and
        // - is followed by at least one whitespace character and a token that can start an expression.
        // - Furthermore, if the operator appears on its own line, the next line must have at least the same indentation width as the operator.
        val opText = builder.getTokenText
        builder.rawLookup(1) == ScalaTokenTypes.tWHITE_SPACE_IN_LINE &&
          isSymbolicIdentifier(opText) && {
            val region = builder.currentIndentationRegion
            val opIndent = builder.findPrecedingIndentation
            // Actually the operator is allowed to be under the current indent, but not so much that it is on the previous indent
            //      |return
            //      |  a
            //      | + b
            // -> return (a + b)
            //
            //      |return
            //      |  a
            //      |+ b
            // -> return a; +b
            opIndent.forall(indent => !region.isOutdentForLeadingInfixOperator(indent)) &&
              builder.predict { builder =>
                // A leading infix operator must be followed by a lexically suitable expression.
                // Usually any simple expr will do. However, a backquoted identifier may serve as
                // either an op or a reference. So the additional constraint is that the following
                // token can't be an assignment operator.
                // (https://github.com/scala/scala/pull/9567/files#diff-1bfb209890ef057b7b17d9124b3a5c518e22acd4d554a6b561598af8087f0fd5R455)
                // Example:
                //   test += (a + b)
                //   `test` += 12 // `test` should not be a leading infix operator
                def opIsBacktickIdFollowedByAssignment: Boolean =
                  opText.startsWith("`") && isAssignmentOperator(builder.getTokenText)

                startsExpression(builder.getTokenType) &&
                  builder.findPrecedingIndentation.forall(rhsIndent => opIndent.exists(rhsIndent >= _)) &&
                  !opIsBacktickIdFollowedByAssignment
              }
          }

      } else false
    }

  //compares two operators a id2 b id1 c
  private def compar(id1: String, id2: String, builder: PsiBuilder): Boolean = {
    if (priority(id1, assignments = true) < priority(id2, assignments = true)) true //  a * b + c  =((a * b) + c)
    else if (priority(id1, assignments = true) > priority(id2, assignments = true)) false //  a + b * c = (a + (b * c))
    else if (operatorAssociativity(id1) == operatorAssociativity(id2))
      if (operatorAssociativity(id1) == Associativity.Right) true
      else false
    else {
      builder error ErrMsg("wrong.type.associativity")
      false
    }
  }
}

private object PrecedenceClimbingInfixParsingRule {
  case class InfixStackElement(op: String, marker: PsiBuilder.Marker)
}