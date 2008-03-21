package org.jetbrains.plugins.scala.lang.parser.parsing.patterns

import _root_.scala.collection.mutable.Stack

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
import org.jetbrains.plugins.scala.lang.parser.parsing.nl.LineTerminator


/**
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 29.02.2008
* Time: 15:18:39
* To change this template use File | Settings | File Templates.
*/

/*
 * Pattern3 ::= SimplePattern
 *            | SimplePattern { id [nl] SimplePattern}
 */

object Pattern3 {
  def parse(builder: PsiBuilder): Boolean = {
    var assoc = 0
    val markerStack = new Stack[PsiBuilder.Marker]
    val opStack = new Stack[String]
    val infixMarker = builder.mark
    var backupMarker = builder.mark
    var count = 0
    if (!SimplePattern.parse(builder)) {
      infixMarker.drop
      backupMarker.drop
      return false
    }
    while (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER && builder.getTokenText != "|") {
      count = count + 1
      //need to know associativity
      val s = builder.getTokenText
      s.charAt(s.length-1) match {
        case ':' => {
          assoc match {
            case 0 => assoc = -1
            case 1 => {
              builder error ScalaBundle.message("wrong.type.associativity", new Array[Object](0))
            }
            case -1 => {}
          }
        }
        case _ => {
          assoc match {
            case 0 => assoc = 1
            case 1 => {}
            case -1 => {
              builder error ScalaBundle.message("wrong.type.associativity", new Array[Object](0))
            }
          }
        }
      }

      var exit = false
      while (!exit) {
        if (opStack.isEmpty) {
          opStack+=s
          val newMarker = backupMarker.precede
          markerStack += newMarker
          exit = true
        }
        else if (!compar(s, opStack.top,assoc)) {
          opStack.pop
          backupMarker.drop
          backupMarker = markerStack.top.precede
          markerStack.pop.done(ScalaElementTypes.INFIX_PATTERN)
        }
        else {
          opStack+=s
          val newMarker = backupMarker.precede
          markerStack += newMarker
          exit = true
        }
      }
      builder.advanceLexer //Ate id
      builder.getTokenType match {
        case ScalaTokenTypes.tLINE_TERMINATOR => {
          if (!LineTerminator(builder.getTokenText)) {
            builder error ScalaBundle.message("simple.pattern.expected", new Array[Object](0))
          }
          else {
            builder.advanceLexer //Ale nl
          }
        }
        case _ => {}
      }
      backupMarker.drop
      backupMarker = builder.mark
      if (!SimplePattern.parse(builder)) {
        builder error ScalaBundle.message("simple.pattern.expected", new Array[Object](0))
      }
    }
    backupMarker.drop
    if (count>0) {
      while (!markerStack.isEmpty) {
        markerStack.pop.done(ScalaElementTypes.INFIX_PATTERN)
      }
      infixMarker.done(ScalaElementTypes.INFIX_PATTERN)
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
  private def priority(id: String) : Int = {
    id.charAt(0) match {
      case '~' | '#' | '@' | '$' | '?' | '\\'     => 0
      case '*' | '/' | '%'                        => 1
      case '+' | '-'                              => 2
      case ':'                                    => 3
      case '=' | '!'                              => 4
      case '<' | '>'                              => 5
      case '&'                                    => 6
      case '^'                                    => 7
      case '|'                                    => 8
      case _                                      => 9
    }
  }
  //compares two operators
  private def compar(id1: String, id2: String, assoc: Int): Boolean = {
    if (priority(id1) < priority(id2)) return true        //  a * b + c  =((a * b) + c)
    else if (priority(id1) > priority(id2)) return false  //  a + b * c = (a + (b * c))
    else if (assoc == -1) return true
    else return false
  }
  //Associations of operator
  private def associate(id: String): Int = {
    id.charAt(id.length-1) match {
      case ':' => return -1   // right
      case _   => return +1  // left
    }
  }
}