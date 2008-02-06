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
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateParents
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.VariantTypeParam
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.TypeParamClause
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.Param
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.ParamClauses
import org.jetbrains.plugins.scala.lang.parser.parsing.base.ModifierWithoutImplicit
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.base.AccessModifier
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifier

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 06.02.2008
* Time: 15:10:40
* To change this template use File | Settings | File Templates.
*/

/*
 * PatVarDef ::= {Annotation} {Modifier} 'val' PatDef |
 *               {Annotation} {Modifier} 'var' VarDef
 */

object PatVarDef {
  def parse(builder: PsiBuilder):Boolean = {
    val patVarMarker = builder.mark
    //TODO: parse annotations
    //parse modifiers
    val modifierMarker = builder.mark
    while (BNF.firstModifier.contains(builder.getTokenType)) {
      Modifier.parse(builder)
    }
    modifierMarker.done(ScalaElementTypes.MODIFIERS)
    builder.getTokenType match {
      case ScalaTokenTypes.kVAL => {
        builder.advanceLexer //Ate val
        if (PatDef parse builder) {
          patVarMarker.done(ScalaElementTypes.PATTERN_DEFINITION)
          return true
        }
        else {
          patVarMarker.rollbackTo
          return false
        }
      }
      case ScalaTokenTypes.kVAR => {
        builder.advanceLexer //Ate var
        if (VarDef parse builder) {
          patVarMarker.done(ScalaElementTypes.VARIABLE_DEFINITION)
          return true
        }
        else {
          patVarMarker.rollbackTo
          return false
        }
      }
      case _ => {
        patVarMarker.rollbackTo
        return false
      }
    }
  }
}