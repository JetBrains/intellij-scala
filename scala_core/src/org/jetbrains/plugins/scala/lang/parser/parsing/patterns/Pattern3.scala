package org.jetbrains.plugins.scala.lang.parser.parsing.patterns

import _root_.scala.collection.mutable.Stack
import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import nl.LineTerminator

/**
* @author Alexander Podkhalyuzin
* Date: 29.02.2008
*/

/*
 * Pattern3 ::= SimplePattern
 *            | SimplePattern { id [nl] SimplePattern}
 */

object Pattern3 {
  def parse(builder: PsiBuilder): Boolean = {
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
      val s = builder.getTokenText

      var exit = false
      while (!exit) {
        if (opStack.isEmpty) {
          opStack+=s
          val newMarker = backupMarker.precede
          markerStack += newMarker
          exit = true
        }
        else if (!compar(s, opStack.top,builder)) {
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
  //compares two operators a id2 b id1 c
  private def compar(id1: String, id2: String, builder: PsiBuilder): Boolean = {
    if (priority(id1) < priority(id2)) return true        //  a * b + c  =((a * b) + c)
    else if (priority(id1) > priority(id2)) return false  //  a + b * c = (a + (b * c))
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
    id.charAt(id.length-1) match {
      case ':' => return -1   // right
      case _   => return +1  // left
    }
  }
}