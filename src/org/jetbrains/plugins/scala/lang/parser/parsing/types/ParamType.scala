package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import util.ParserUtils._
/**
* @author Alexander Podkhalyuzin
* Date: 08.02.2008
*/

/*
 * ParamType ::= Type |
 *               '=>' Type |
 *               Type '*'
 */

object ParamType {
  def parseInner(builder: PsiBuilder): Boolean = {
    builder.getTokenType match {
      case ScalaTokenTypes.tFUNTYPE => {
        builder.advanceLexer //Ate '=>'
          Type.parse(builder)
      }
      case _ => {
        if (!Type.parse(builder,true)) false else {
          builder.getTokenText match {
            case "*" => builder.advanceLexer // Ate '*'
            case _ => {/* nothing needs to be done */}
          }
          true
        }
      }
    }
  }

  def parse(builder : PsiBuilder) = build(ScalaElementTypes.PARAM_TYPE, builder) { parseInner(builder) }
}