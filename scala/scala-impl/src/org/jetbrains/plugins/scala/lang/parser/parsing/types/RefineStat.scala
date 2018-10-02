package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.statements.{Dcl, Def, EmptyDcl}

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

/*
 * RefineStat ::= Dcl
 *              | 'type' TypeDef
 */
object RefineStat extends RefineStat {
  override protected def `def` = Def
  override protected def dcl = Dcl
}

trait RefineStat {
  protected def `def`: Def
  protected def dcl: Dcl

  def parse(builder: ScalaPsiBuilder): Boolean = {
    builder.getTokenType match {
      case ScalaTokenTypes.kTYPE =>
        if (!`def`.parse(builder, isMod = false)) {
          if (!dcl.parse(builder, isMod = false)) {
            EmptyDcl.parse(builder, isMod = false)
          }
        }
        true
      case ScalaTokenTypes.kVAR | ScalaTokenTypes.kVAL
           | ScalaTokenTypes.kDEF =>
        if (dcl.parse(builder, isMod = false)) {
          true
        }
        else {
          EmptyDcl.parse(builder, isMod = false)
          true
        }
      case _ =>
        false
    }
  }
}