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
* Date: 03.03.2008
* Time: 15:53:49
* To change this template use File | Settings | File Templates.
*/

/*
 * Expr ::= (Bindings | id) '=>' Expr
 *        | Expr1
 */

object Expr {
  def parse(builder: PsiBuilder): Boolean = {
    val exprMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        builder.advanceLexer //Ate id
        builder.getTokenType match {
          case ScalaTokenTypes.tFUNTYPE => {
            builder.advanceLexer //Ate =>
            if (!Expr.parse(builder)) builder error ScalaBundle.message("wrong.expression",new Array[Object](0))
            exprMarker.done(ScalaElementTypes.EXPR)
            return true
          }
          case _ => {
            exprMarker.rollbackTo
          }
        }
      }
      case ScalaTokenTypes.tLPARENTHESIS => {
        if (Bindings.parse(builder)) {
          builder.getTokenType match {
            case ScalaTokenTypes.tFUNTYPE => {
              builder.advanceLexer //Ate =>
              if (!Expr.parse(builder)) builder error ScalaBundle.message("wrong.expression",new Array[Object](0))
              exprMarker.done(ScalaElementTypes.EXPR)
              return true
            }
            case _ => exprMarker.rollbackTo
          }
        }
        else {
          exprMarker.drop
        }
      }
      case _ => exprMarker.drop
    }
    return Expr1.parse(builder)
  }
}