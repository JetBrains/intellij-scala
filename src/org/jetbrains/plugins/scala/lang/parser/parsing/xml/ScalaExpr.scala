package org.jetbrains.plugins.scala.lang.parser.parsing.xml

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypesEx

/**
* @author Alexander Podkhalyuzin
* Date: 18.04.2008
*/

/*
 * ScalaExpr ::= '{' Block '}'
 */

object ScalaExpr {
  def parse(builder: PsiBuilder): Boolean = {
    builder.getTokenType match {
      case ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_START => {
        builder.advanceLexer()
      }
      case _ => return false
    }
    if (!Block.parse(builder, false, true)) {
      builder error ErrMsg("xml.scala.expression.exected")
    }
    while (builder.getTokenType == ScalaTokenTypes.tSEMICOLON ||
    builder.getTokenType == ScalaTokenTypes.tLINE_TERMINATOR) {
      builder.advanceLexer
    }
    builder.getTokenType match {
      case ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_END => {
        builder.advanceLexer()
      }
      case _ => builder error ErrMsg("xml.scala.injection.end.expected")
    }
    return true
  }
}