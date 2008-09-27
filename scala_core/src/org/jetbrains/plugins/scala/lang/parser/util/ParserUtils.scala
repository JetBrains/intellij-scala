package org.jetbrains.plugins.scala.lang.parser.util

import _root_.scala.collection.mutable._

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.util.DebugPrint
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.PsiBuilder


object ParserUtils extends ParserUtilsBase {

  def lookAheadSeq(n: Int)(builder: PsiBuilder) = (1 to n).map(i => {
    val token = if (!builder.eof) builder.getTokenType else null
    builder.advanceLexer
    token
  })


  def roll(builder: PsiBuilder) {
    while (!builder.eof && !(
            ScalaTokenTypes.tLINE_TERMINATOR.eq(builder.getTokenType) ||
                    ScalaTokenTypes.tLBRACE.eq(builder.getTokenType)
            )) {
      builder.advanceLexer
    }
  }


  /* rolls forward until token from elems encountered */
  def rollPanic(builder: PsiBuilder, elems: HashSet[IElementType]) = {

    val stack = new Stack[IElementType]
    var flag = true

    while (flag && !builder.eof && !elems.contains(builder.getTokenType)) {

      if (ScalaTokenTypes.tLPARENTHESIS.equals(builder.getTokenType) ||
              ScalaTokenTypes.tLBRACE.equals(builder.getTokenType) ||
              ScalaTokenTypes.tLSQBRACKET.equals(builder.getTokenType)) {
        stack += builder.getTokenType
        builder.advanceLexer
        //eatElement(builder , builder.getTokenType)
      }
      else if (!stack.isEmpty &&
              ((ScalaTokenTypes.tRPARENTHESIS.equals(builder.getTokenType) &&
                      ScalaTokenTypes.tLPARENTHESIS.equals(stack.top)) ||
                      (ScalaTokenTypes.tRBRACE.equals(builder.getTokenType) &&
                              ScalaTokenTypes.tLBRACE.equals(stack.top)) ||
                      (ScalaTokenTypes.tRSQBRACKET.equals(builder.getTokenType) &&
                              ScalaTokenTypes.tLSQBRACKET.equals(stack.top)))) {
        stack.pop
        builder.advanceLexer
        //eatElement(builder , builder.getTokenType)
      }
           else if (stack.isEmpty &&
                   (ScalaTokenTypes.tRPARENTHESIS.equals(builder.getTokenType) ||
                           ScalaTokenTypes.tRBRACE.equals(builder.getTokenType) ||
                           ScalaTokenTypes.tRSQBRACKET.equals(builder.getTokenType))) {
             flag = false
           } else {
             builder.advanceLexer
             // eatElement(builder , builder.getTokenType)
           }
    }
    while (!stack.isEmpty) stack.pop
  }

  /* Roll forward throug line terminators*/
  def rollForward(builder: PsiBuilder): Boolean = {
    var counter = 0
    while (!builder.eof()) {
      builder.getTokenType match {
        case ScalaTokenTypes.tLINE_TERMINATOR => {
          builder.advanceLexer
          counter = counter + 1
        }
        case _ => return (counter == 0)
      }
    }
    counter == 0
  }

  //Write element node
  def eatElement(builder: PsiBuilder, elem: IElementType): Unit = {
    if (!builder.eof()) {
      builder.advanceLexer // Ate something
    }
    ()

  }

  def parseTillLast(builder: PsiBuilder, lastSet: TokenSet): Unit = {
    while (!builder.eof() && !lastSet.contains(builder.getTokenType)) {
      builder.advanceLexer
      DebugPrint println "an error"
    }

    if (builder.eof()) /*builder error "unexpected end of file"; */ return

    if (lastSet.contains(builder.getTokenType)) builder advanceLexer;
    return
  }

  def eatSeqWildcardNext(builder: PsiBuilder): Boolean = {
    val marker = builder.mark
    if (builder.getTokenType == ScalaTokenTypes.tUNDER) {
      builder.advanceLexer
      if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER &&
              builder.getTokenText == "*") {
        builder.advanceLexer
        marker.done(ScalaElementTypes.SEQ_WILDCARD)
        true
      } else {
        marker.rollbackTo
        false
      }
    } else {
      marker.drop
      false
    }
  }


  def build(t : ScalaElementType, builder : PsiBuilder)  (inner : => Boolean) : Boolean = {
    val marker = builder.mark
    val parsed = inner
    if (parsed) marker.done(t) else marker.rollbackTo
    parsed
  }
}
