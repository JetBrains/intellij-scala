package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.TypeArgs
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils.priority


/**
 * @author AlexanderPodkhalyuzin
* Date: 03.03.2008
 */

/*
 * InfixExpr ::= PrefixExpr
 *             | InfixExpr id [TypeArgs] [nl] InfixExpr
 */
object InfixExpr extends ParsingRule {
  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {

    var markerStack = List.empty[PsiBuilder.Marker]
    var opStack = List.empty[String]
    val infixMarker = builder.mark
    var backupMarker = builder.mark
    var count = 0
    if (!PrefixExpr()) {
      backupMarker.drop()
      infixMarker.drop()
      return false
    }
    var exitOf = true
    while (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER && !builder.newlineBeforeCurrentToken && exitOf) {
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
          markerStack.head.done(ScalaElementType.INFIX_EXPR)
          markerStack = markerStack.tail
        }
        else {
          opStack = s :: opStack
          val newMarker = backupMarker.precede
          markerStack = newMarker :: markerStack
          exit = true
        }
      }
      val setMarker = builder.mark
      val opMarker = builder.mark
      builder.advanceLexer() //Ate id
      opMarker.done(ScalaElementType.REFERENCE_EXPRESSION)
      TypeArgs.parse(builder, isPattern = false)
      if (builder.twoNewlinesBeforeCurrentToken) {
        setMarker.rollbackTo()
        count = 0
        backupMarker.drop()
        exitOf = false
      } else {
        backupMarker.drop()
        backupMarker = builder.mark
        if (!PrefixExpr()) {
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
        markerStack.head.done(ScalaElementType.INFIX_EXPR)
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
  //private var assoc: Int = 0  //this mark associativity: left - 1, right - -1

  //compares two operators a id2 b id1 c
  private def compar(id1: String, id2: String, builder: PsiBuilder): Boolean = {
    if (priority(id1, assignments = true) < priority(id2, assignments = true)) true //  a * b + c  =((a * b) + c)
    else if (priority(id1, assignments = true) > priority(id2, assignments = true)) false //  a + b * c = (a + (b * c))
    else if (associate(id1) == associate(id2))
      if (associate(id1) == -1) true
      else false
    else {
      builder error ErrMsg("wrong.type.associativity")
      false
    }
  }

  //Associations of operator
  def associate(id: String): Int = {
    id.charAt(id.length - 1) match {
      case ':' => -1 // right
      case _ => +1 // left
    }
  }
}