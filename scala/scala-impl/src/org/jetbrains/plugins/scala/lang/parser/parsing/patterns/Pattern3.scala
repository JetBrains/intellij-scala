package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package patterns

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

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
        else if (!ParserUtils.compareOperators(s, opStack.head)) {
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
}
