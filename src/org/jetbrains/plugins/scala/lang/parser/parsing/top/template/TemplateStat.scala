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

object TemplateStat {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    builder.getTokenType match {
      case ScalaTokenTypes.kIMPORT => {
        Import parse builder
        return true
      }
      case _ => {
        if (Def parse builder) {
          return true
        } else if (Dcl parse builder) {
          return true
        } else if (EmptyDcl parse builder) {
          return true
        } else if (Expr.parse (builder)) {
          return true
        } else {
          return false
        }
      }
    }
  }
}