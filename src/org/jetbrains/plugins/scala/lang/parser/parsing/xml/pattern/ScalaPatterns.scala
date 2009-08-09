package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package xml.pattern

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypesEx
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns._

/**
* @author Alexander Podkhalyuzin
* Date: 21.04.2008
*/

object ScalaPatterns {
  def parse(builder: PsiBuilder): Boolean = {
    builder.getTokenType match {
      case ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_START => {
        builder.advanceLexer()
      }
      case _ => return false
    }
    if (!XmlPatterns.parse(builder)) builder error ErrMsg("xml.scala.patterns.exected")
    builder.getTokenType match {
      case ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_END => {
        builder.advanceLexer()
      }
      case _ => builder error ErrMsg("xml.scala.injection.end.expected")
    }
    return true
  }
}