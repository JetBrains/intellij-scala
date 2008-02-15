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
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.VariantTypeParam
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.TypeParamClause
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.Param
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.ParamClauses
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.base.AccessModifier
import org.jetbrains.plugins.scala.lang.parser.parsing.statements.PatVarDef

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 06.02.2008
* Time: 14:33:51
* To change this template use File | Settings | File Templates.
*/

/*
 * EarlyDef ::= '{' [PatVarDef {semi PatVarDef}] '}' 'with'
 */

object EarlyDef {
  def parse(builder: PsiBuilder): Boolean = {
    val earlyMarker = builder.mark
    //Look for {
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE => builder.advanceLexer //Ate {
      case _ => {
        builder error ScalaBundle.message("unreachable.error", new Array[Object](0))
        earlyMarker.drop
        return false
      }
    }
    //this metod parse recursively PatVarDef {semi PatVarDef}
    def subparse():Boolean = {
      builder.getTokenType match {
        case ScalaTokenTypes.tRBRACE => {
          builder.advanceLexer //Ate }
          return true
        }
        case _ => {
          if (PatVarDef parse builder) {
            builder.getTokenType match {
              case ScalaTokenTypes.tRBRACE => {
                builder.advanceLexer //Ate }
                return true
              }
              case ScalaTokenTypes.tSEMICOLON => {
                builder.advanceLexer //Ate semicolon
                return subparse
              }
              case ScalaTokenTypes.tLINE_TERMINATOR => {
                builder.advanceLexer //Ate new-line token
                return subparse
              }
              case _ => return false
            }
          }
          else {
            return false
          }
        }
      }
    }
    if (!subparse) {
      builder error ScalaBundle.message("unreachable.error", new Array[Object](0))
      earlyMarker.rollbackTo
      return false
    }
    //finally look for 'with' keyword
    builder.getTokenType match {
      case ScalaTokenTypes.kWITH => {
        earlyMarker.done(ScalaElementTypes.EARLY_DEFINITION)
        builder.advanceLexer //Ate with
        return true
      }
      case _ => {
        builder error ScalaBundle.message("unreachable.error", new Array[Object](0))
        earlyMarker.rollbackTo
        return false
      }
    }
  }
}