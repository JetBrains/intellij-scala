package org.jetbrains.plugins.scala
package lang
package parser
package util

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.{Associativity, ParsingRule}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils.{isSymbolicIdentifier, operatorAssociativity, priority}

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
      if (builder.isScala3) {
        // In scala 3 the infix operator may be on the next line
        // but only if
        // - it is a symbolic identifier,
        // - followed by at least one whitespace, and
        // - the next token is in the same line and this token can start an expression
        builder.rawLookup(1) == ScalaTokenTypes.tWHITE_SPACE_IN_LINE &&
          isSymbolicIdentifier(builder.getTokenText) && {
          val rollbackMarker = builder.mark()
          try {
            builder.advanceLexer() // ate identifier
            startsExpression(builder.getTokenType) &&
              builder.findPreviousNewLine.isEmpty
          } finally rollbackMarker.rollbackTo()
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
