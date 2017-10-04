package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top.template

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Import
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Expr
import org.jetbrains.plugins.scala.lang.parser.parsing.statements._

/** 
* @author Alexander Podkhalyuzin
* Date: 13.02.2008
*/

/*
 *  TemplateStat ::= Import
 *              | {AttributeClause} {Modifier} Def
 *              | {AttributeClause} {Modifier} Dcl
 *              | Expr
 */
object TemplateStat extends TemplateStat {
  override protected def `def` = Def
  override protected def dcl = Dcl
  override protected def expr = Expr
  override protected def emptyDcl = EmptyDcl
}

trait TemplateStat {
  protected def `def`: Def
  protected def dcl: Dcl
  protected def expr: Expr
  protected def emptyDcl: EmptyDcl

  def parse(builder: ScalaPsiBuilder): Boolean = {
    builder.getTokenType match {
      case ScalaTokenTypes.kIMPORT =>
        Import parse builder
        return true
      case _ =>
        if (`def` parse builder) {
          return true
        } else if (dcl parse builder) {
          return true
        } else if (emptyDcl parse builder) {
          return true
        } else if (expr.parse(builder)) {
          return true
        } else {
          return false
        }
    }
  }
}