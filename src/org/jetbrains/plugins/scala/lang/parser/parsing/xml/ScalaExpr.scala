package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package xml

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypesEx
import builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 18.04.2008
*/

/*
 * ScalaExpr ::= '{' Block '}'
 */

object ScalaExpr {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    builder.getTokenType match {
      case ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_START => {
        builder.advanceLexer
        builder.enableNewlines
      }
      case _ => return false
    }
    if (!Block.parse(builder, false, needNode = true, isPattern = false)) {
      builder error ErrMsg("xml.scala.expression.exected")
    }
    while (builder.getTokenType == ScalaTokenTypes.tSEMICOLON) {
      builder.advanceLexer
    }
    builder.getTokenType match {
      case ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_END => {
        builder.advanceLexer
      }
      case _ => builder error ErrMsg("xml.scala.injection.end.expected")
    }
    builder.restoreNewlinesState
    return true
  }
}