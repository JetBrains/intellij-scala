package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import _root_.scala.collection.mutable.Stack

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType

import com.intellij.psi.PsiFile
import com.intellij.lang.ParserDefinition

import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types._
import org.jetbrains.plugins.scala.lang.parser.parsing.nl.LineTerminator
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns._
import org.jetbrains.plugins.scala.ScalaFileType

import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl

import com.intellij.openapi.util.TextRange

import com.intellij.lang.ASTNode
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.util.CharTable
import com.intellij.lexer.Lexer
import com.intellij.lang.impl.PsiBuilderImpl
import com.intellij.psi._
import com.intellij.psi.impl.source.CharTableImpl


/** 
* @author Alexander Podkhalyuzin
* Date: 03.03.2008
*/

/*
 * InfixExpr ::= PrefixExpr
 *             | InfixExpr id [nl] InfixExpr
 */

object InfixExpr {
  def parse(builder: PsiBuilder): Boolean = {
    val markerStack = new Stack[PsiBuilder.Marker]
    val opStack = new Stack[String]
    val infixMarker = builder.mark
    var backupMarker = builder.mark
    var count = 0
    if (!PrefixExpr.parse(builder)) {
      backupMarker.drop
      infixMarker.drop
      return false
    }
    var exitOf = true
    while (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER && exitOf) {
      //need to know associativity
      val s = builder.getTokenText

      var exit = false
      while (!exit) {
        if (opStack.isEmpty) {
          opStack += s
          val newMarker = backupMarker.precede
          markerStack += newMarker
          exit = true
        }
        else if (!compar(s, opStack.top, builder)) {
          opStack.pop
          backupMarker.drop
          backupMarker = markerStack.top.precede
          markerStack.pop.done(ScalaElementTypes.INFIX_EXPR)
        }
        else {
          opStack += s
          val newMarker = backupMarker.precede
          markerStack += newMarker
          exit = true
        }
      }
      val setMarker = builder.mark
      builder.advanceLexer //Ate id
      builder.getTokenType match {
        case ScalaTokenTypes.tLINE_TERMINATOR => {
          if (!LineTerminator(builder.getTokenText)) {
            setMarker.rollbackTo
            exitOf = false
          }
          else {
            builder.advanceLexer //Ale nl
            backupMarker.drop
            backupMarker = builder.mark
            if (!PrefixExpr.parse(builder)) {
              setMarker.rollbackTo
              exitOf = false
            }
            else {
              count = count + 1
              setMarker.drop
            }
          }
        }
        case _ => {
          backupMarker.drop
          backupMarker = builder.mark
          if (!PrefixExpr.parse(builder)) {
            setMarker.rollbackTo
            exitOf = false
          }
          else {
            setMarker.drop
            count = count + 1
          }
        }
      }
    }
    if (exitOf) backupMarker.drop
    if (count > 0) {
      while (!markerStack.isEmpty) {
        markerStack.pop.done(ScalaElementTypes.INFIX_EXPR)
      }
      infixMarker.drop
    }
    else {
      while (!markerStack.isEmpty) {
        markerStack.pop.drop
      }
      infixMarker.drop
    }
    return true
  }
  //private var assoc: Int = 0  //this mark associativity: left - 1, right - -1
  //Defines priority
  private def priority(id: String): Int = {
    id.charAt(0) match {
      case '~' | '#' | '@' | '$' | '?' | '\\' => 0
      case '*' | '/' | '%' => 1
      case '+' | '-' => 2
      case ':' => 3
      case '=' | '!' => 4
      case '<' | '>' => 5
      case '&' => 6
      case '^' => 7
      case '|' => 8
      case _ => 9
    }
  }
  //compares two operators a id2 b id1 c
  private def compar(id1: String, id2: String, builder: PsiBuilder): Boolean = {
    if (priority(id1) < priority(id2)) return true //  a * b + c  =((a * b) + c)
    else if (priority(id1) > priority(id2)) return false //  a + b * c = (a + (b * c))
    else if (associate(id1) == associate(id2))
      if (associate(id1) == -1) return true
      else return false
    else {
      builder error ErrMsg("wrong.type.associativity")
      return false
    }
  }
  private def opeq(id1: String, id2: String): Boolean = priority(id1) == priority(id2)
  //Associations of operator
  private def associate(id: String): Int = {
    id.charAt(id.length - 1) match {
      case ':' => return -1 // right
      case _ => return +1 // left
    }
  }
}