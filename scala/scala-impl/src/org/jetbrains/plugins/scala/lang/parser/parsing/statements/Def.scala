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
  override protected def funDef = FunDef
  override protected def patDef = PatDef
  override protected def varDef = VarDef
  override protected def tmplDef = TmplDef
  override protected def macroDef = MacroDef
  override protected def typeDef = TypeDef
  override protected def annotation = Annotation
}

trait Def {
  protected def funDef: FunDef
  protected def macroDef: MacroDef
  protected def tmplDef: TmplDef
  protected def patDef: PatDef
  protected def varDef: VarDef
  protected def typeDef: TypeDef
  protected def annotation: Annotation

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

