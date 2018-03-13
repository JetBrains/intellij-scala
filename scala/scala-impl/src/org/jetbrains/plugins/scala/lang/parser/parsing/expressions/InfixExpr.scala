package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.TypeArgs


/**
 * @author AlexanderPodkhalyuzin
* Date: 03.03.2008
 */

/*
 * InfixExpr ::= PrefixExpr
 *             | InfixExpr id [TypeArgs] [nl] InfixExpr
 */
object InfixExpr extends InfixExpr {
  override protected def prefixExpr = PrefixExpr
}

trait InfixExpr {
  protected def prefixExpr: PrefixExpr

  import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils._

  def parse(builder: ScalaPsiBuilder): Boolean = {

    type MStack[X] = _root_.scala.collection.mutable.Stack[X]

    val markerStack = new MStack[PsiBuilder.Marker]
    val opStack = new MStack[String]
    val infixMarker = builder.mark
    var backupMarker = builder.mark
    var count = 0
    if (!prefixExpr.parse(builder)) {
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
          opStack push s
          val newMarker = backupMarker.precede
          markerStack push newMarker
          exit = true
        }
        else if (!compar(s, opStack.top, builder)) {
          opStack.pop()
          backupMarker.drop()
          backupMarker = markerStack.top.precede
          markerStack.pop().done(ScalaElementTypes.INFIX_EXPR)
        }
        else {
          opStack push s
          val newMarker = backupMarker.precede
          markerStack push newMarker
          exit = true
        }
      }
      val setMarker = builder.mark
      val opMarker = builder.mark
      builder.advanceLexer() //Ate id
      opMarker.done(ScalaElementTypes.REFERENCE_EXPRESSION)
      TypeArgs.parse(builder, isPattern = false)
      if (builder.twoNewlinesBeforeCurrentToken) {
        setMarker.rollbackTo()
        count = 0
        backupMarker.drop()
        exitOf = false
      } else {
        backupMarker.drop()
        backupMarker = builder.mark
        if (!prefixExpr.parse(builder)) {
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
        markerStack.pop().done(ScalaElementTypes.INFIX_EXPR)
        count -= 1
      }

    }
    infixMarker.drop()
    while (markerStack.nonEmpty) {
      markerStack.pop().drop()
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