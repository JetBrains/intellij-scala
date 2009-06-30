package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import base.Import
import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.TokenSet
import lexer.ScalaTokenTypes
import statements.{Dcl, Def}
import top.TmplDef

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * BlockStat ::= Import
 *             | ['implicit'] Def
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
        if (!Def.parse(builder, false, true)) {
          if (!TmplDef.parse(builder)) {
            if (Dcl.parse(builder)) {
              builder error ErrMsg("wrong.declaration.in.block")
              return true
            } else return false
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