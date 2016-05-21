package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import _root_.org.jetbrains.plugins.scala.lang.parser.parsing.params.TypeParamClause
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 06.02.2008
*/

/*
 * TraitDef ::= id [TypeParamClause] TraitTemplateOpt
 */
object TraitDef extends TraitDef {
  override protected val templateOpt = TraitTemplateOpt
}

trait TraitDef {
  protected val templateOpt: TraitTemplateOpt

  def parse(builder: ScalaPsiBuilder): Boolean = builder.getTokenType match {
    case ScalaTokenTypes.tIDENTIFIER =>
      builder.advanceLexer() //Ate identifier
      TypeParamClause.parse(builder)
      templateOpt.parse(builder)
      true
      case _ =>
        builder.error(ErrMsg("identifier.expected"))
        false
    }
}