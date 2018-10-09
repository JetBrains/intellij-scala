package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
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
    defMarker.setCustomEdgeTokenBinders(ScalaTokenBinders.PRECEEDING_COMMENTS_TOKEN, null)
    if (isMod || isImplicit) {
      val annotationsMarker = builder.mark
      while (Annotation.parse(builder)) {}
      annotationsMarker.done(ScalaElementTypes.ANNOTATIONS)
      annotationsMarker.setCustomEdgeTokenBinders(ScalaTokenBinders.DEFAULT_LEFT_EDGE_BINDER, null)
      //parse modifiers
      val modifierMarker = builder.mark
      if (isMod) {
        while (Modifier.parse(builder)) {}
        
        if (builder.isMetaEnabled) while (builder.getTokenText == ScalaTokenTypes.kINLINE.toString) {
//          val inlineMarker = builder.mark()
          builder.remapCurrentToken(ScalaTokenTypes.kINLINE)
          builder.advanceLexer()
//          inlineMarker.done(ScalaTokenTypes.kINLINE)
        }
      }
      else {
        while (builder.getTokenType == ScalaTokenTypes.kIMPLICIT || builder.getTokenType == ScalaTokenTypes.kLAZY) {
          builder.advanceLexer()
        }
      }
      modifierMarker.done(ScalaElementTypes.MODIFIERS)
    } else {
      val annotationsMarker = builder.mark
      annotationsMarker.done(ScalaElementTypes.ANNOTATIONS)
      val modifierMarker = builder.mark
      modifierMarker.done(ScalaElementTypes.MODIFIERS)
    }
    //Look for val,var,def or type
    builder.getTokenType match {
      case ScalaTokenTypes.kVAL =>
        builder.advanceLexer() //Ate val
        if (PatDef.parse(builder)) {
          defMarker.done(ScalaElementTypes.PATTERN_DEFINITION)
          true
        }
        else {
          defMarker.rollbackTo()
          false
        }
      case ScalaTokenTypes.kVAR =>
        builder.advanceLexer() //Ate var
        if (VarDef.parse(builder)) {
          defMarker.done(ScalaElementTypes.VARIABLE_DEFINITION)
          true
        }
        else {
          defMarker.rollbackTo()
          false
        }
      case ScalaTokenTypes.kDEF =>
        if (MacroDef.parse(builder)) {
          defMarker.done(ScalaElementTypes.MACRO_DEFINITION)
          true
        } else if (FunDef.parse(builder)) {
          defMarker.done(ScalaElementTypes.FUNCTION_DEFINITION)
          true
        } else {
          defMarker.rollbackTo()
          false
        }
      case ScalaTokenTypes.kTYPE =>
        if (TypeDef.parse(builder)) {
          defMarker.done(ScalaElementTypes.TYPE_DEFINITION)
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
      case _ =>
        defMarker.rollbackTo()
        false
    }
  }
}

