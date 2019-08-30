package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import org.jetbrains.plugins.scala.lang.lexer.{ScalaModifierTokenType, ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifier
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Annotation
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef

/**
* @author Alexander Podkhalyuzin
* Date: 11.02.2008
*/

/*
 * Def ::= [{Annotation} {Modifier}]
 *          ('val' ValDef
 *         | 'var' VarDef
 *         | 'def' FunDef
 *         | 'def' MacroDef
 *         | 'type' {nl} TypeDef)
 */
object Def {

  def parse(builder: ScalaPsiBuilder,
            isMod: Boolean = true,
            isImplicit: Boolean = false): Boolean = {
    val defMarker = builder.mark
    defMarker.setCustomEdgeTokenBinders(ScalaTokenBinders.PRECEDING_COMMENTS_TOKEN, null)
    if (isMod || isImplicit) {
      val annotationsMarker = builder.mark
      while (Annotation.parse(builder)) {}
      annotationsMarker.done(ScalaElementType.ANNOTATIONS)
      annotationsMarker.setCustomEdgeTokenBinders(ScalaTokenBinders.DEFAULT_LEFT_EDGE_BINDER, null)
      //parse modifiers
      val modifierMarker = builder.mark
      if (isMod) {
        while (Modifier.parse(builder)) {}

        if (builder.isMetaEnabled) while (builder.getTokenText == ScalaModifierTokenType.Inline.modifier.text) {
//          val inlineMarker = builder.mark()
          builder.remapCurrentToken(ScalaModifierTokenType.Inline)
          builder.advanceLexer()
          //          inlineMarker.done(ScalaModifierTokenType.Inline)
        }
      }
      else {
        while (builder.getTokenType == ScalaTokenTypes.kIMPLICIT || builder.getTokenType == ScalaTokenTypes.kLAZY) {
          builder.advanceLexer()
        }
      }
      modifierMarker.done(ScalaElementType.MODIFIERS)
    } else {
      val annotationsMarker = builder.mark
      annotationsMarker.done(ScalaElementType.ANNOTATIONS)
      val modifierMarker = builder.mark
      modifierMarker.done(ScalaElementType.MODIFIERS)
    }
    //Look for val,var,def or type

    implicit val b: ScalaPsiBuilder = builder
    builder.getTokenType match {
      case ScalaTokenTypes.kVAL =>
        builder.advanceLexer() //Ate val
        if (PatDef.parse(builder)) {
          defMarker.done(ScalaElementType.PATTERN_DEFINITION)
          true
        }
        else {
          defMarker.rollbackTo()
          false
        }
      case ScalaTokenTypes.kVAR =>
        builder.advanceLexer() //Ate var
        if (VarDef.parse(builder)) {
          defMarker.done(ScalaElementType.VARIABLE_DEFINITION)
          true
        }
        else {
          defMarker.rollbackTo()
          false
        }
      case ScalaTokenTypes.kDEF =>
        if (MacroDef.parse(builder)) {
          defMarker.done(ScalaElementType.MACRO_DEFINITION)
          true
        } else if (FunDef.parse(builder)) {
          defMarker.done(ScalaElementType.FUNCTION_DEFINITION)
          true
        } else {
          defMarker.rollbackTo()
          false
        }
      case ScalaTokenTypes.kTYPE =>
        if (TypeDef.parse(builder)) {
          defMarker.done(ScalaElementType.TYPE_DEFINITION)
          true
        }
        else {
          defMarker.rollbackTo()
          false
        }
      case ScalaTokenTypes.kCASE | ScalaTokenTypes.kCLASS
           | ScalaTokenTypes.kOBJECT | ScalaTokenTypes.kTRAIT =>
        defMarker.rollbackTo()
        TmplDef.parse(builder)
      case ScalaTokenType.IsEnum() | ScalaTokenType.GivenKeyword() =>
        defMarker.rollbackTo()
        TmplDef.parse(builder)
      case _ =>
        defMarker.rollbackTo()
        false
    }
  }
}

