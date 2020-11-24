package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import org.jetbrains.plugins.scala.lang.parser.parsing.base.{End, Extension, Import}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.statements.{Dcl, Def, EmptyDcl}
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef

import scala.annotation.tailrec

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

  @tailrec
  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    builder.getTokenType match {
      case ScalaTokenTypes.kIMPORT =>
        Import()
        true
      case _ if Extension() => true
      case ScalaTokenTypes.tSEMICOLON =>
        builder.advanceLexer()
        true
      case ScalaTokenTypes.kDEF | ScalaTokenTypes.kVAL | ScalaTokenTypes.kVAR | ScalaTokenTypes.kTYPE =>
        if (!Def()) {
          if (Dcl()) {
            builder error ErrMsg("wrong.declaration.in.block")
          } else {
            EmptyDcl.parse(builder)
            builder error ErrMsg("wrong.declaration.in.block")
          }
        }
        true
      case IsTemplateDefinition() =>
        TmplDef()
      case _ if End() => true
      case _ if builder.skipExternalToken() => BlockStat()
      case _ =>
        if (!Expr1() && !Def() && !TmplDef()) {
          if (Dcl()) {
            builder error ErrMsg("wrong.declaration.in.block")
            true
          } else if (EmptyDcl.parse(builder)) {
            builder error ErrMsg("wrong.declaration.in.block")
            true
          } else {
            false
          }
        } else {
          true
        }
    }
  }
}