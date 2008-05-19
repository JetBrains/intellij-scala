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




import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifier
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Import
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions._

/** 
* @author Alexander Podkhalyuzin
* Date: 05.02.2008
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
    val annotationsMarker = builder.mark
    while (Annotation.parse(builder)) {}
    annotationsMarker.done(ScalaElementTypes.ANNOTATIONS)
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
        builder.advanceLexer //Ate class
        if (ClassDef parse builder) {
          templateMarker.done(ScalaElementTypes.CLASS_DEF)
        } else {
          templateMarker.drop
        }
        return true
      }
      case ScalaTokenTypes.kOBJECT => {
        caseMarker.drop
        modifierMarker.done(ScalaElementTypes.MODIFIERS)
        builder.advanceLexer //Ate object
        if (ObjectDef parse builder) {
          templateMarker.done(ScalaElementTypes.OBJECT_DEF)
        } else {
          templateMarker.drop
        }
        return true
      }
      case ScalaTokenTypes.kTRAIT => {
        caseMarker.rollbackTo
        modifierMarker.done(ScalaElementTypes.MODIFIERS)
        builder.getTokenType match {
          case ScalaTokenTypes.kTRAIT => {
            builder.advanceLexer //Ate trait
            if (TraitDef.parse(builder)) {
              templateMarker.done(ScalaElementTypes.TRAIT_DEF)
            } else {
              templateMarker.drop
            }
            return true
          }
          // In this way wrong case modifier
          case _ => {
            builder error ScalaBundle.message("wrong.case.modifier", new Array[Object](0))
            builder.advanceLexer //Ate case
            builder.advanceLexer //Ate trait
            TraitDef.parse(builder)
            templateMarker.done(ScalaElementTypes.TRAIT_DEF)
            return true
          }
        }
      }
      //it's error
      case _ => {
        templateMarker.rollbackTo
        //builder.advanceLexer //Ate something
        return false
      }
    }
  }
}

