package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package base

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 08.02.2008
*/

/*
 *  Import ::= import ImportExpr { ,  ImportExpr}
 */

object Import {
  def parse(builder: ScalaPsiBuilder) {
    val importMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kIMPORT =>
        builder.advanceLexer //Ate import
      case _ => builder error ErrMsg("unreachable.error")
    }
    ImportExpr parse builder
    while (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
      builder.advanceLexer() //Ate ,
      ImportExpr parse builder
    }
    importMarker.done(ScalaElementType.IMPORT_STMT)
  }
}