package org.jetbrains.plugins.scala.lang.parser.parsing.statements

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
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Expr
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.Pattern2
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Ids

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 06.02.2008
* Time: 15:40:40
* To change this template use File | Settings | File Templates.
*/

/*
 *  ValDef ::= PatDef |
 *             ids ':' Type '=' '_'
 */
//TODO: rewrite this
object VarDef {
  def parse(builder: PsiBuilder): Boolean = {
    if (PatDef parse builder) {
      return true
    }
    val valDefMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        Ids parse builder
        var hasTypeDcl = false

        if (ScalaTokenTypes.tCOLON.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)

          if (BNF.firstType.contains(builder.getTokenType)) {
            Type parse builder
          } else {
            builder error "type declaration expected"
          }

          hasTypeDcl = true
        }
        else {
          valDefMarker.rollbackTo
          return false
        }
       if (! ScalaTokenTypes.tASSIGN.equals(builder.getTokenType)) {
          valDefMarker.rollbackTo
          return false
        } else {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tASSIGN)

          builder.getTokenType match {
            case ScalaTokenTypes.tUNDER => builder.advanceLexer //Ate _
            case _ => {
              valDefMarker.rollbackTo
              return false
            }
          }
          valDefMarker.drop
          return true
        }
      }
      case _ => {
        valDefMarker.rollbackTo
        return false
      }
    }
  }
}