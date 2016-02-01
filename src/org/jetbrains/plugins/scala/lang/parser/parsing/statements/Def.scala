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
object Def extends Def {
  override protected val funDef = FunDef
  override protected val patDef = PatDef
  override protected val varDef = VarDef
  override protected val tmplDef = TmplDef
  override protected val macroDef = MacroDef
  override protected val typeDef = TypeDef
  override protected val annotation = Annotation
}

trait Def {
  protected val funDef: FunDef
  protected val macroDef: MacroDef
  protected val tmplDef: TmplDef
  protected val patDef: PatDef
  protected val varDef: VarDef
  protected val typeDef: TypeDef
  protected val annotation: Annotation

  def parse(builder: ScalaPsiBuilder): Boolean = parse(builder, isMod = true)
  def parse(builder: ScalaPsiBuilder, isMod: Boolean): Boolean = parse(builder, isMod, isImplicit = false)
  def parse(builder: ScalaPsiBuilder, isMod: Boolean, isImplicit: Boolean): Boolean = {
    val defMarker = builder.mark
    defMarker.setCustomEdgeTokenBinders(ScalaTokenBinders.PRECEEDING_COMMENTS_TOKEN, null)
    if (isMod || isImplicit) {
      val annotationsMarker = builder.mark
      while (annotation.parse(builder)) {}
      annotationsMarker.done(ScalaElementTypes.ANNOTATIONS)
      annotationsMarker.setCustomEdgeTokenBinders(ScalaTokenBinders.DEFAULT_LEFT_EDGE_BINDER, null)
      //parse modifiers
      val modifierMarker = builder.mark
      if (isMod) {
        while (Modifier.parse(builder)) {}
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
        if (patDef parse builder) {
          defMarker.done(ScalaElementTypes.PATTERN_DEFINITION)
          true
        }
        else {
          defMarker.rollbackTo()
          false
        }
      case ScalaTokenTypes.kVAR =>
        builder.advanceLexer() //Ate var
        if (varDef parse builder) {
          defMarker.done(ScalaElementTypes.VARIABLE_DEFINITION)
          true
        }
        else {
          defMarker.rollbackTo()
          false
        }
      case ScalaTokenTypes.kDEF =>
        if (macroDef parse builder) {
          defMarker.done(ScalaElementTypes.MACRO_DEFINITION)
          true
        } else if (funDef parse builder) {
          defMarker.done(ScalaElementTypes.FUNCTION_DEFINITION)
          true
        } else {
          defMarker.rollbackTo()
          false
        }
      case ScalaTokenTypes.kTYPE =>
        if (typeDef parse builder) {
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
        tmplDef parse builder
      case _ =>
        defMarker.rollbackTo()
        false
    }
  }
}

