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
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * Generator ::= Pattern1 '<-' Expr [Guard]
 */

object Generator {
  def parse(builder: PsiBuilder): Boolean = {
    val genMarker = builder.mark
    if (builder.getTokenType == ScalaTokenTypes.kVAL) builder.advanceLexer
    if (!Pattern1.parse(builder)) {
      genMarker.drop
      return false
    }
    builder.getTokenText match {
      case "<-" => {
        builder.advanceLexer
      }
      case _ => {
        builder error ScalaBundle.message("choose.expected", new Array[Object](0))
      }
    }
    if (!Expr.parse(builder)) builder error ScalaBundle.message("wrong.expression", new Array[Object](0))
    builder.getTokenType match {
      case ScalaTokenTypes.kIF => Guard parse builder
      case _ => {}
    }
    genMarker.done(ScalaElementTypes.GENERATOR)
    return true
  }
}