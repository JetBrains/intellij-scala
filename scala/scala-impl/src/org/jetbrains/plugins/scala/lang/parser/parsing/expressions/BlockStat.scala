package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import org.jetbrains.plugins.scala.lang.parser.parsing.base.{Extension, Import}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.statements.{Dcl, Def, EmptyDcl}
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef

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
object BlockStat extends ParsingRule {

  import lexer.ScalaTokenType._
  import lexer.ScalaTokenTypes

  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    builder.getTokenType match {
      case ScalaTokenTypes.kIMPORT =>
        Import()
        return true
      case _ if Extension() =>
        return true
      case ScalaTokenTypes.tSEMICOLON =>
        builder.advanceLexer()
        return true
      case ScalaTokenTypes.kDEF | ScalaTokenTypes.kVAL | ScalaTokenTypes.kVAR | ScalaTokenTypes.kTYPE =>
        if (!Def()) {
          if (Dcl()) {
            builder error ErrMsg("wrong.declaration.in.block")
            return true
          } else {
            EmptyDcl.parse(builder)
            builder error ErrMsg("wrong.declaration.in.block")
            return true
          }
        }
      case IsTemplateDefinition() =>
        return TmplDef()
      case _ if builder.skipExternalToken() => BlockStat()
      case _ =>
        if (!Expr1()) {
          if (!Def()) {
            if (!TmplDef()) {
              if (Dcl()) {
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
    true
  }
}