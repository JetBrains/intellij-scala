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
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateParents
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.VariantTypeParam
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.TypeParamClause
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.Param
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.ParamClauses
import org.jetbrains.plugins.scala.lang.parser.parsing.base.ModifierWithoutImplicit
import org.jetbrains.plugins.scala.lang.parser.parsing.base.StatementSeparator
import org.jetbrains.plugins.scala.lang.parser.parsing.base.AttributeClause
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifier
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Import

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 05.02.2008
* Time: 17:03:33
* To change this template use File | Settings | File Templates.
*/

/*
 * TmplDef ::= {Annotation} {Modifier}
            [case] class ClassDef
 *          | [case] object ObjectDef
 *          | trait TraitDef
 *
 */

object TmplDef {
  def parse(builder: PsiBuilder): Boolean = {
    val templateMarker = builder.mark
    //TODO: parsing annotations
    //parsing modifiers
    val modifierMarker = builder.mark
    while (BNF.firstModifier.contains(builder.getTokenType)) {
      Modifier.parse(builder)
    }
    //could be case modifier
    val caseMarker = builder.mark
    if (builder.getTokenType == ScalaTokenTypes.kCASE)
      builder.advanceLexer //Ate case
    //parsing template body
    builder.getTokenType match {
      case ScalaTokenTypes.kCLASS => {
        caseMarker.drop
        modifierMarker.done(ScalaElementTypes.MODIFIERS)
        ClassDef parse builder
        templateMarker.done(ScalaElementTypes.CLASS_DEF)
        return true
      }
      case ScalaTokenTypes.kOBJECT => {
        caseMarker.drop
        modifierMarker.done(ScalaElementTypes.MODIFIERS)
        ObjectDef parse builder
        templateMarker.done(ScalaElementTypes.OBJECT_DEF)
        return true
      }
      case ScalaTokenTypes.kTRAIT => {
        caseMarker.rollbackTo
        modifierMarker.done(ScalaElementTypes.MODIFIERS)
        TraitDef.parse(builder)
        templateMarker.done(ScalaElementTypes.TRAIT_DEF)
        return true
      }
      //it's error
      case _ => {
        templateMarker.rollbackTo
        return false
      }
    }
  }
}