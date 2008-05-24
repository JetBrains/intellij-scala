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
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * ResultExpr ::= Expr1
 *              | (Bindings | id ':' CompoundType) '=>' Block
 */

object ResultExpr {
  def parse(builder: PsiBuilder): Boolean = {
    val resultMarker = builder.mark
    val backupMarker = builder.mark

    def parseFunctionEnd() = builder.getTokenType match {
      case ScalaTokenTypes.tFUNTYPE => {
        builder.advanceLexer //Ate =>
        Block parse (builder, false)
        backupMarker.drop
        resultMarker.done(ScalaElementTypes.FUNCTION_EXPR)
        true
      }
      case _ => {
        resultMarker.drop
        backupMarker.rollbackTo
        false
      }
    }

    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS => {
        Bindings parse builder
        return parseFunctionEnd
      }
      case ScalaTokenTypes.tIDENTIFIER => {
        val pmarker = builder.mark
        builder.advanceLexer //Ate id
        if (ScalaTokenTypes.tCOLON == builder.getTokenType) {
          builder.advanceLexer // ate ':'
          val pt = builder.mark
          CompoundType.parse(builder)
          pt.done(ScalaElementTypes.PARAM_TYPE)
        }
        builder.getTokenType match {
          case ScalaTokenTypes.tFUNTYPE => {
            val psm = pmarker.precede // 'parameter clause'
            val pssm = psm.precede // 'parameter list'
            psm.done(ScalaElementTypes.PARAM_CLAUSE)
            pssm.done(ScalaElementTypes.PARAM_CLAUSES)
            pmarker.done(ScalaElementTypes.PARAM)
            
            return parseFunctionEnd
          }
          case _ => {
            builder error ErrMsg("fun.sign.expected")
          }
        }
        return parseFunctionEnd
      }
      case _ => {
        backupMarker.drop
      }
    }
    resultMarker.drop
    return false
  }
}