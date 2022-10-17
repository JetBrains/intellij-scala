package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package patterns

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/*
 * Pattern3 ::= SimplePattern
 *            | SimplePattern { id [nl] SimplePattern}
 */
object Pattern3 extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    var markerStack = List.empty[PsiBuilder.Marker]
    var opStack = List.empty[String]
    var backupMarker = builder.mark()
    var count = 0
    if (!SimplePattern()) {
      backupMarker.drop()
      return false
    }
    while (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER && builder.getTokenText != "|") {
      count = count + 1
      val s = builder.getTokenText

      var exit = false
      while (!exit) {
        if (opStack.isEmpty) {
          opStack = s :: opStack
          val newMarker = backupMarker.precede
          markerStack = newMarker :: markerStack
          exit = true
        }
        else if (!compar(s, opStack.head,builder)) {
          opStack = opStack.tail
          backupMarker.drop()
          backupMarker = markerStack.head.precede
          markerStack.head.done(ScalaElementType.INFIX_PATTERN)
          markerStack = markerStack.tail
        }
        else {
          opStack = s :: opStack
          val newMarker = backupMarker.precede
          markerStack = newMarker :: markerStack
          exit = true
        }
      }
      val idMarker = builder.mark()
      builder.advanceLexer() //Ate id
      idMarker.done(ScalaElementType.REFERENCE)
      if (builder.twoNewlinesBeforeCurrentToken) {
        builder.error(ScalaBundle.message("simple.pattern.expected"))
      }
      backupMarker.drop()
      backupMarker = builder.mark()
      if (!SimplePattern()) {
        builder error ScalaBundle.message("simple.pattern.expected")
      }
    }
    backupMarker.drop()
    if (count>0) {
      while (markerStack.nonEmpty) {
        markerStack.head.done(ScalaElementType.INFIX_PATTERN)
        markerStack = markerStack.tail
      }
    }
    else {
      while (markerStack.nonEmpty) {
        markerStack.head.drop()
        markerStack = markerStack.tail
      }
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

  //Associations of operator
  private def associate(id: String): Int = {
    id.charAt(id.length-1) match {
      case ':' => -1   // right
      case _   => +1  // left
    }
  }
}
