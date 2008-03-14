package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

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
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._
import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl

import com.intellij.openapi.util.TextRange

import com.intellij.lang.ASTNode
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.util.CharTable
import com.intellij.lexer.Lexer
import com.intellij.lang.impl.PsiBuilderImpl
//import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import com.intellij.psi._
import com.intellij.psi.impl.source.CharTableImpl

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 06.03.2008
* Time: 10:53:41
* To change this template use File | Settings | File Templates.
*/

/*
 * Exprs ::= Expr ',' {Expr ','} [Expr]
 */

object Exprs {
  def parse(builder: PsiBuilder): Boolean = {
    val exprsMarker = builder.mark
    if (!Expr.parse(builder)) {
      exprsMarker.drop
      return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tCOMMA => {
        builder.advanceLexer //Ate ,
      }
      case _ => {
        exprsMarker.rollbackTo
        return false
      }
    }
    while (Expr.parse(builder)) {
      builder.getTokenType match {
        case ScalaTokenTypes.tCOMMA => {
          builder.advanceLexer //Ate ,
        }
        case _ => {
          exprsMarker.done(ScalaElementTypes.EXPRS)
          return true
        }
      }
    }
    exprsMarker.done(ScalaElementTypes.EXPRS)
    return true
  }
}