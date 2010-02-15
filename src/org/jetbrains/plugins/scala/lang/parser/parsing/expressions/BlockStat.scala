package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import base.Import
import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.TokenSet
import lexer.ScalaTokenTypes
import statements.{Dcl, Def}
import top.TmplDef
import com.intellij.lang.PsiBuilder.Marker

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * BlockStat ::= Import
 *             | ['implicit'] Def
 *
 *             | implicit Id => Expr  # Not in Scala Specification yet!
 *
 *             | {LocalModifier} TmplDef
 *             | Expr1
 */

object BlockStat {
  def parse(builder: PsiBuilder): Boolean = {
    val tokenType = builder.getTokenType
    tokenType match {
      case ScalaTokenTypes.kIMPORT => {
        Import parse builder
        return true
      }
      case ScalaTokenTypes.tLINE_TERMINATOR | ScalaTokenTypes.tSEMICOLON => {
        builder.advanceLexer
        return true
      }
      case ScalaTokenTypes.kDEF | ScalaTokenTypes.kVAL | ScalaTokenTypes.kTYPE => {
        if (!Def.parse(builder, false, true)) {
          if (Dcl.parse(builder)) {
            builder error ErrMsg("wrong.declaration.in.block")
            return true
          } else return false
        }
      }
      case ScalaTokenTypes.kCLASS | ScalaTokenTypes.kTRAIT | ScalaTokenTypes.kOBJECT => {
        return TmplDef.parse(builder)
      }
      case _ if TokenSets.MODIFIERS.contains(tokenType)=> {
        if (tokenType == ScalaTokenTypes.kIMPLICIT) {
          val implicitMarker: Marker = builder.mark
          builder.advanceLexer //Ate implicit
          if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER) {
            val ipmarker = builder.mark
            builder.getTokenType match {
              case ScalaTokenTypes.tIDENTIFIER => {
                val pmarker = builder.mark
                builder.advanceLexer //Ate id
                builder.getTokenType match {
                  case ScalaTokenTypes.tFUNTYPE => {
                    pmarker.done(ScalaElementTypes.PARAM)
                    ipmarker.done(ScalaElementTypes.PARAM_CLAUSE)
                    ipmarker.precede.done(ScalaElementTypes.PARAM_CLAUSES)

                    builder.advanceLexer //Ate =>
                    if (!Expr.parse(builder)) builder error ErrMsg("wrong.expression")
                    implicitMarker.done(ScalaElementTypes.FUNCTION_EXPR)
                    return true
                  }
                  case _ => {
                    pmarker.drop
                    ipmarker.drop
                    implicitMarker.rollbackTo
                  }
                }
              }
              case _ => {
                ipmarker.drop
                implicitMarker.rollbackTo
              }
            }
          } else {
            implicitMarker.rollbackTo
            if (!Def.parse(builder, false, true)) {
              if (!TmplDef.parse(builder)) {
                if (Dcl.parse(builder)) {
                  builder error ErrMsg("wrong.declaration.in.block")
                  return true
                } else return false
              }
            }
          }
        } else {
          if (!Def.parse(builder, false, true)) {
            if (!TmplDef.parse(builder)) {
              if (Dcl.parse(builder)) {
                builder error ErrMsg("wrong.declaration.in.block")
                return true
              } else return false
            }
          }
        }
      }
      case _ => {
        if (!Expr1.parse(builder)) {
          if (!Def.parse(builder, false, true)) {
            if (!TmplDef.parse(builder)) {
              if (Dcl.parse(builder)) {
                builder error ErrMsg("wrong.declaration.in.block")
                return true
              }
              else return false
            }
          }
        }
      }
    }
    return true
  }
}