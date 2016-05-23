package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.params.TypeParamClause

/**
* @author Alexander Podkhalyuzin
* Date: 06.02.2008
*/

/*
 * TraitDef ::= id [TypeParamClause] TraitTemplateOpt
 */
object TraitDef extends TraitDef {
  override protected val templateOpt = TraitTemplateOpt
  override protected val typeParamClause = TypeParamClause
}

trait TraitDef {
  protected val templateOpt: TraitTemplateOpt
  protected val typeParamClause: TypeParamClause

  def parse(builder: ScalaPsiBuilder): Boolean = builder.getTokenType match {
    case ScalaTokenTypes.tIDENTIFIER =>
      builder.advanceLexer() //Ate identifier
      typeParamClause.parse(builder)
      templateOpt.parse(builder)
      true
      case _ =>
        builder.error(ErrMsg("identifier.expected"))
        false
    }
}