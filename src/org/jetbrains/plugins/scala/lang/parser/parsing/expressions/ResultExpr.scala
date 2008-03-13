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
* Date: 06.03.2008
* Time: 12:32:29
* To change this template use File | Settings | File Templates.
*/

/*
 * ResultExpr ::= Expr1
 *              | (Bindings | id ':' CompoundType) '=>' Block
 */

object ResultExpr {
  def parse(builder: PsiBuilder): Boolean = {
    val resultMarker = builder.mark
    val backupMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS => {
        if (!Bindings.parse(builder)) {
          backupMarker.drop
        }
        else {
          builder.getTokenType match {
            case ScalaTokenTypes.tFUNTYPE => {
              builder.advanceLexer //Ate =>
              Block parse (builder,false)
              backupMarker.drop
              resultMarker.done(ScalaElementTypes.RESULT_EXPR)
              return true
            }
            case _ => {
              backupMarker.rollbackTo
            }
          }
        }
      }
      case ScalaTokenTypes.tIDENTIFIER => {
        builder.advanceLexer //Ate id
        builder.getTokenType match {
          case ScalaTokenTypes.tCOLON => {
            builder.advanceLexer //Ate :
          }
          case _ => {
            builder error ScalaBundle.message("colon.expected", new Array[Object](0))
          }
        }
        if (!CompoundType.parse(builder)) builder error ScalaBundle.message("wrong.type", new Array[Object](0))
        builder.getTokenType match {
          case ScalaTokenTypes.tFUNTYPE => {
            builder.advanceLexer //Ate =>
            Block parse (builder,false)
            backupMarker.drop
            resultMarker.done(ScalaElementTypes.RESULT_EXPR)
            return true
          }
          case _ => {
            backupMarker.rollbackTo
          }
        }
      }
      case _ => {
        backupMarker.drop
      }
    }
    if (!Expr1.parse(builder)) {
      resultMarker.drop
      return false
    }
    resultMarker.done(ScalaElementTypes.RESULT_EXPR)
    return true
  }
}