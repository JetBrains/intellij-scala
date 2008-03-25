package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import _root_.scala.collection.mutable._

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types._
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns._

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 06.03.2008
* Time: 13:04:44
* To change this template use File | Settings | File Templates.
*/

/*
 * Enumerator ::= Generator
 *              | Guard
 *              | 'val' Pattern1 '=' Expr
 */

object Enumerator {
  def parse(builder: PsiBuilder): Boolean = {
    val enumMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kIF => {
        Guard parse builder
        enumMarker.drop
        return true
      }
      case ScalaTokenTypes.kVAL => {
        builder.advanceLexer //Ate val
        if (!Pattern1.parse(builder)) {
          builder error ScalaBundle.message("wrong.pattern", new Array[Object](0))
          enumMarker.done(ScalaElementTypes.ENUMERATOR)
          return true
        }
        builder.getTokenType match {
          case ScalaTokenTypes.tASSIGN => {
            builder.advanceLexer //Ate =
          }
          case _ => {
            if (builder.getTokenText == "<-") {
              enumMarker.rollbackTo
              return Generator parse builder
            }
            builder error ScalaBundle.message("assign.expected", new Array[Object](0))
          }
        }
        if (!Expr.parse(builder)) builder error ScalaBundle.message("wrong.expression", new Array[Object](0))
        enumMarker.done(ScalaElementTypes.ENUMERATOR)
        return true
      }
      case _ => {
        enumMarker.drop
        return Generator.parse(builder)
      }
    }
  }
}