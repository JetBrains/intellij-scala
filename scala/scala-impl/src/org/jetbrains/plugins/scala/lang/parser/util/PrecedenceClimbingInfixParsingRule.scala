package org.jetbrains.plugins.scala
package lang
package parser
package util

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.{Associativity, ParsingRule}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils.{isAssignmentOperator, isSymbolicIdentifier, operatorAssociativity, priority}

abstract class PrecedenceClimbingInfixParsingRule extends ParsingRule {
  protected def parseFirstOperator()(implicit builder: ScalaPsiBuilder): Boolean = parseOperator()
  protected def parseOperator()(implicit builder: ScalaPsiBuilder): Boolean

  protected def referenceElementType: IElementType
  protected def infixElementType: IElementType

  protected def parseAfterOperatorId(opMarker: PsiBuilder.Marker)(implicit builder: ScalaPsiBuilder): Unit = ()

  final override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    var markerStack = List.empty[PsiBuilder.Marker]
    var opStack = List.empty[String]
    val infixMarker = builder.mark
    var backupMarker = builder.mark
    var count = 0
    if (!parseFirstOperator()) {
      backupMarker.drop()
      infixMarker.drop()
      return false
    }
    var exitOf = true
    while (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER && shouldContinue && exitOf) {
      //need to know associativity
      val s = builder.getTokenText

      var exit = false
      while (!exit) {
        if (opStack.isEmpty) {
          opStack = s :: opStack
          val newMarker = backupMarker.precede
          markerStack = newMarker :: markerStack
          exit = true
        }
        else if (!compar(s, opStack.head, builder)) {
          opStack = opStack.tail
          backupMarker.drop()
          backupMarker = markerStack.head.precede
          markerStack.head.done(infixElementType)
          markerStack = markerStack.tail
        }
        else {
          opStack = s :: opStack
          val newMarker = backupMarker.precede
          markerStack = newMarker :: markerStack
          exit = true
        }
      }
      val setMarker = builder.mark()
      val opMarker = builder.mark()
      builder.advanceLexer() //Ate id
      opMarker.done(referenceElementType)

      parseAfterOperatorId(opMarker)

      if (builder.twoNewlinesBeforeCurrentToken) {
        setMarker.rollbackTo()
        count = 0
        backupMarker.drop()
        exitOf = false
      } else {
        backupMarker.drop()
        backupMarker = builder.mark()
        if (!parseOperator()) {
          setMarker.rollbackTo()
          count = 0
          exitOf = false
        }
        else {
          setMarker.drop()
          count = count + 1
        }
      }
    }
    if (exitOf) backupMarker.drop()
    if (count > 0) {
      while (count > 0 && markerStack.nonEmpty) {
        markerStack.head.done(infixElementType)
        markerStack = markerStack.tail
        count -= 1
      }

    }
    infixMarker.drop()
    while (markerStack.nonEmpty) {
      markerStack.head.drop()
      markerStack = markerStack.tail
    }
    true
  }

  // first-set of Expr()
  private val startsExpression = {
    import ScalaTokenTypes._
    import ScalaTokenType._
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
      if (builder.isScala3orSource3) {
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
          val Some(opIndent) = builder.findPreviousIndent
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
              builder.findPreviousIndent.forall(rhsIndent => rhsIndent >= opIndent) &&
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
