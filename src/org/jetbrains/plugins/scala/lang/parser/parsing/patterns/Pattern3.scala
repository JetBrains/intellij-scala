package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package patterns

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 29.02.2008
*/

/*
 * Pattern3 ::= SimplePattern
 *            | SimplePattern { id [nl] SimplePattern}
 */
object Pattern3 extends Pattern3 {
  override protected val simplePattern = SimplePattern
}

trait Pattern3 {
  protected val simplePattern: SimplePattern

  def parse(builder: ScalaPsiBuilder): Boolean = {
    type Stack[X] = _root_.scala.collection.mutable.Stack[X]
    val markerStack = new Stack[PsiBuilder.Marker]
    val opStack = new Stack[String]
    //val infixMarker = builder.mark
    var backupMarker = builder.mark
    var count = 0
    if (!simplePattern.parse(builder)) {
      //infixMarker.drop
      backupMarker.drop()
      return false
    }
    while (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER && builder.getTokenText != "|") {
      count = count + 1
      val s = builder.getTokenText

      var exit = false
      while (!exit) {
        if (opStack.isEmpty) {
          opStack push s
          val newMarker = backupMarker.precede
          markerStack push newMarker
          exit = true
        }
        else if (!compar(s, opStack.top,builder)) {
          opStack.pop()
          backupMarker.drop()
          backupMarker = markerStack.top.precede
          markerStack.pop().done(ScalaElementTypes.INFIX_PATTERN)
        }
        else {
          opStack push s
          val newMarker = backupMarker.precede
          markerStack push newMarker
          exit = true
        }
      }
      val idMarker = builder.mark
      builder.advanceLexer() //Ate id
      idMarker.done(ScalaElementTypes.REFERENCE)
      if (builder.twoNewlinesBeforeCurrentToken) {
        builder.error(ScalaBundle.message("simple.pattern.expected"))
      }
      backupMarker.drop()
      backupMarker = builder.mark
      if (!simplePattern.parse(builder)) {
        builder error ScalaBundle.message("simple.pattern.expected")
      }
    }
    backupMarker.drop()
    if (count>0) {
      while (markerStack.nonEmpty) {
        markerStack.pop().done(ScalaElementTypes.INFIX_PATTERN)
      }
      //infixMarker.done(ScalaElementTypes.INFIX_PATTERN)
    }
    else {
      while (markerStack.nonEmpty) {
        markerStack.pop().drop()
      }
      //infixMarker.drop
    }
    true
  }

  import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils.priority

  //compares two operators a id2 b id1 c
  private def compar(id1: String, id2: String, builder: PsiBuilder): Boolean = {
    if (priority(id1) < priority(id2)) true        //  a * b + c  =((a * b) + c)
    else if (priority(id1) > priority(id2)) false  //  a + b * c = (a + (b * c))
    else if (associate(id1) == associate(id2))
      if (associate(id1) == -1) true
      else false
    else {
      builder error ErrMsg("wrong.type.associativity")
      false
    }
  }
  private def opeq(id1: String, id2: String): Boolean = priority(id1) == priority(id2)
  //Associations of operator
  private def associate(id: String): Int = {
    id.charAt(id.length-1) match {
      case ':' => -1   // right
      case _   => +1  // left
    }
  }
}