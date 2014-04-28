package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import base.Import
import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.TokenSet
import lexer.ScalaTokenTypes
import top.TmplDef
import com.intellij.lang.PsiBuilder.Marker
import statements.{EmptyDcl, Dcl, Def}
import builder.ScalaPsiBuilder
import parser.util.ParserPatcher

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
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val tokenType = builder.getTokenType
    
    val patcher = ParserPatcher.getSuitablePatcher(builder)
    
    tokenType match {
      case ScalaTokenTypes.kIMPORT => {
        Import parse builder
        return true
      }
      case ScalaTokenTypes.tSEMICOLON => {
        builder.advanceLexer()
        return true
      }
      case ScalaTokenTypes.kDEF | ScalaTokenTypes.kVAL | ScalaTokenTypes.kVAR | ScalaTokenTypes.kTYPE => {
        if (!Def.parse(builder, false, true)) {
          if (Dcl.parse(builder)) {
            builder error ErrMsg("wrong.declaration.in.block")
            return true
          } else {
            EmptyDcl.parse(builder)
            builder error ErrMsg("wrong.declaration.in.block")
            return true
          }
        }
      }
      case ScalaTokenTypes.kCLASS | ScalaTokenTypes.kTRAIT | ScalaTokenTypes.kOBJECT => {
        return TmplDef.parse(builder)
      }
      case _ if patcher.parse(builder) => parse(builder)
      case _ => {
        if (!Expr1.parse(builder)) {
          if (!Def.parse(builder, false, true)) {
            if (!TmplDef.parse(builder)) {
              if (Dcl.parse(builder)) {
                builder error ErrMsg("wrong.declaration.in.block")
                return true
              }
              else {
                if (EmptyDcl.parse(builder)) {
                  builder error ErrMsg("wrong.declaration.in.block")
                  return true
                } else
                  return false
              }
            }
          }
        }
      }
    }
    true
  }
}