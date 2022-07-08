package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import org.jetbrains.plugins.scala.lang.parser.parsing.base.{Extension, Import}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.statements.{Dcl, Def, EmptyDcl}
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef

import scala.annotation.tailrec

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
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
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
            EmptyDcl()
            builder error ErrMsg("wrong.declaration.in.block")
          }
        }
        true
      case IsTemplateDefinition() =>
        TmplDef()
      case _ if builder.skipExternalToken() => BlockStat.parse(builder)
      case _ =>
        if (!Def() && !TmplDef()) {
          if (Dcl()) {
            builder error ErrMsg("wrong.declaration.in.block")
            true
          } else if (EmptyDcl()) {
            builder error ErrMsg("wrong.declaration.in.block")
            true
          } else {
            // expression has to be parsed after trying def/decl because
            // in scala 3 def/decl might start with a soft modifier
            // which could also be a simple reference expression in expr1
            //
            //  def test = {
            //    inline
            //    // vs
            //    inline def test = 3
            //  }
            Expr1() || Annotation.skipUnattachedAnnotations(ErrMsg("missing.statement.for.annotation"))
          }
        } else {
          true
        }
    }
  }
}