package org.jetbrains.plugins.scala.lang.parser.parsing.top

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IChameleonElementType
import com.intellij.psi.tree.TokenSet

import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types.SimpleType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateBody

import org.jetbrains.plugins.scala.lang.parser.parsing.params._


import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.base.AccessModifier

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