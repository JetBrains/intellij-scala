package org.jetbrains.plugins.scala.lang.parser.parsing.top

import _root_.org.jetbrains.plugins.scala.lang.parser.parsing.params.TypeParamClause
import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes

/**
* @author Alexander Podkhalyuzin
* Date: 06.02.2008
*/

/*
 * TraitDef ::= id [TypeParamClause] TraitTemplateOpt
 */

object TraitDef {
  def parse(builder: PsiBuilder): Boolean = {
    builder.getTokenType match {
        case ScalaTokenTypes.tIDENTIFIER => builder.advanceLexer //Ate identifier
        case _ => {
          builder error ScalaBundle.message("identifier.expected", new Array[Object](0))
          return false
        }
      }
    //parsing type parameters
    builder.getTokenType match {
      case ScalaTokenTypes.tLSQBRACKET => TypeParamClause parse builder
      case _ => {/*it could be without type parameters*/}
    }
    TraitTemplateOpt parse builder
    return true
  }
}