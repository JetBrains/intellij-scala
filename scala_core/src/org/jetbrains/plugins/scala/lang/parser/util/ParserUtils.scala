package org.jetbrains.plugins.scala.lang.parser.util

import _root_.scala.collection.mutable._

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.util.DebugPrint
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.PsiBuilder


object ParserUtils {

  // Do not use it! (Very slow). Use Java version instead
  def lookAhead(builder: PsiBuilder, elems: IElementType*): Boolean = {
    val rb = builder.mark
    for (val elem <- elems) {
      if (!builder.eof && elem == builder.getTokenType) {
        builder.advanceLexer
      } else {
        rb.rollbackTo()
        return false
      }
    }
    rb.rollbackTo()
    true
  }


  /* rolls forward until token from elems encountered */
  def rollPanic(builder: PsiBuilder, elems: HashSet[IElementType]) = {

    val stack = new Stack[IElementType]
    var flag = true

    while (flag && ! builder.eof && ! elems.contains(builder.getTokenType)){

      if (ScalaTokenTypes.tLPARENTHESIS.equals(builder.getTokenType) ||
      ScalaTokenTypes.tLBRACE.equals(builder.getTokenType) ||
      ScalaTokenTypes.tLSQBRACKET.equals(builder.getTokenType)) {
        stack += builder.getTokenType
        builder.advanceLexer
        //eatElement(builder , builder.getTokenType)
      }
      else if (! stack.isEmpty &&
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
    while (! stack.isEmpty) stack.pop
  }

  /* Roll forward throug line terminators*/
  def rollForward(builder: PsiBuilder): Boolean = {
    var counter = 0
    while (! builder.eof()){
      builder.getTokenType match {
        case ScalaTokenTypes.tLINE_TERMINATOR => {
          builder.advanceLexer
          counter =counter + 1
        }
        case _ => return (counter == 0)
      }
    }
    counter == 0
  }

  //Write element node
  def eatElement(builder: PsiBuilder, elem: IElementType): Unit = {
    if (! builder.eof()) {
      builder.advanceLexer // Ate something
    }
      ()

  }

  def parseTillLast(builder: PsiBuilder, lastSet: TokenSet): Unit = {
    while (! builder.eof() && ! lastSet.contains(builder.getTokenType)) {
      builder.advanceLexer
      DebugPrint println "an error"
    }

    if (builder.eof()) /*builder error "unexpected end of file"; */ return

    if (lastSet.contains(builder.getTokenType)) builder advanceLexer; return
  }

  //Write element node
  def errorToken(builder: PsiBuilder,
      marker: PsiBuilder.Marker,
      msg: String,
      elem: ScalaElementType): ScalaElementType = {
    builder.error(msg)
    //marker.done(elem)
    marker.rollbackTo()
    ScalaElementTypes.WRONGWAY
  }
}
