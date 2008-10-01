package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import base.Import
import com.intellij.lang.PsiBuilder
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
    builder.getTokenType match {
      case ScalaTokenTypes.kIMPORT => {
        Import parse builder
        return true
      }
      case ScalaTokenTypes.tLINE_TERMINATOR | ScalaTokenTypes.tSEMICOLON => {
        builder.advanceLexer
        return true
      }
      case _ => {}
    }
    if (!Def.parse(builder, false, true)) {
      if (!TmplDef.parse(builder)) {
        if (!Expr1.parse(builder)) {
          if (Dcl.parse(builder)) {
            builder error ErrMsg("wrong.declaration.in.block")
          }
          else return false
        }
      }
    }
    return true
  }
}