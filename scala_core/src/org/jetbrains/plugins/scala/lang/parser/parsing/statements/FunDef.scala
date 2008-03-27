package org.jetbrains.plugins.scala.lang.parser.parsing.statements

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.PsiBuilder

import com.intellij.psi._
import com.intellij.lang.ParserDefinition
import org.jetbrains.plugins.scala.ScalaFileType
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiManager

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Ids
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.ArgumentExprs
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.BlockStat
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.Pattern2
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions._
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifier
import org.jetbrains.plugins.scala.lang.parser.parsing.nl._
import org.jetbrains.plugins.scala.lang.parser.parsing.params._
import org.jetbrains.plugins.scala.lang.parser.parsing.statements._

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 13.02.2008
* Time: 16:14:20
* To change this template use File | Settings | File Templates.
*/

/*
 * FunDef ::= FunSig [':' Type] '=' Expr
 *          | FunSig [nl] '{' Block '}'
 *          | 'this' ParamClause ParamClauses
 *            ('=' ConstrExpr | [nl] ConstrBlock)
 */


object FunDef {
  def parse(builder: PsiBuilder): Boolean = {
    val faultMarker = builder.mark;
    builder.getTokenType match {
      case ScalaTokenTypes.kDEF => builder.advanceLexer
      case _ => {
        faultMarker.drop
        return false
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        FunSig parse builder
        builder.getTokenType match {
          case ScalaTokenTypes.tCOLON => {
            builder.advanceLexer //Ate :
            if (Type.parse(builder)) {
              builder.getTokenType match {
                case ScalaTokenTypes.tASSIGN => {
                  builder.advanceLexer //Ate =
                  if (Expr.parse(builder)) {
                    faultMarker.drop
                    return true
                  }
                  else {
                    builder error ScalaBundle.message("wrong.expression", new Array[Object](0))
                    faultMarker.drop
                    return true
                  }
                }
                case _ => {
                  faultMarker.rollbackTo
                  return false
                }
              }
            }
            else {
              faultMarker.rollbackTo
              return false
            }
          }
          case ScalaTokenTypes.tASSIGN => {
            builder.advanceLexer //Ate =
            if (Expr.parse(builder)) {
              faultMarker.drop
              return true
            }
            else {
              builder error ScalaBundle.message("wrong.expression", new Array[Object](0))
              faultMarker.drop
              return true
            }
          }
          case ScalaTokenTypes.tLBRACE => {
            Block.parse(builder,true)
            faultMarker.drop
            return true
          }
          case ScalaTokenTypes.tLINE_TERMINATOR => {
            if (!LineTerminator(builder.getTokenText)) {
              faultMarker.rollbackTo
              return false
            }
            else {
              builder.advanceLexer //Ate nl
              builder.getTokenType match {
                case ScalaTokenTypes.tLBRACE => {
                  Block.parse(builder,true)
                  faultMarker.drop
                  return true
                }
                case _ => {
                  faultMarker.rollbackTo
                  return false
                }
              }
            }
          }
          case _ => {
            faultMarker.rollbackTo
            return false
          }
        }
      }
      case ScalaTokenTypes.kTHIS => {
        builder.advanceLexer //Ate this
        ParamClauses parse (builder, true)
        builder.getTokenType match {
          case ScalaTokenTypes.tASSIGN => {
            builder.advanceLexer //Ate =
            if (!ConstrExpr.parse(builder)) {
              builder error ScalaBundle.message("wrong.constr.expression", new Array[Object](0))
            }
            faultMarker.drop
            return true
          }
          case ScalaTokenTypes.tLINE_TERMINATOR => {
            if (LineTerminator(builder.getTokenText)) {
              builder.advanceLexer //Ate nl
            }
            else {
              builder error ScalaBundle.message("constr.block.expected", new Array[Object](0))
              faultMarker.drop
              return true
            }
            if (!ConstrBlock.parse(builder)) {
              builder error ScalaBundle.message("constr.block.expected", new Array[Object](0))
            }
            faultMarker.drop
            return true
          }
          case _ => {
            if (!ConstrBlock.parse(builder)) {
              builder error ScalaBundle.message("constr.block.expected", new Array[Object](0))
            }
            faultMarker.drop
            return true
          }
        }
      }
      case _ => {
        faultMarker.rollbackTo
        return false
      }
    }
  }
}