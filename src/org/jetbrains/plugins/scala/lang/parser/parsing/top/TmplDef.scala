package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifier
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Annotation

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
object TmplDef extends TmplDef {
  override protected val classDef = ClassDef
  override protected val objectDef = ObjectDef
  override protected val traitDef = TraitDef
  override protected val annotation = Annotation
}

trait TmplDef {
  protected val classDef: ClassDef
  protected val objectDef: ObjectDef
  protected val traitDef: TraitDef
  protected val annotation: Annotation

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val templateMarker = builder.mark
    templateMarker.setCustomEdgeTokenBinders(ScalaTokenBinders.PRECEEDING_COMMENTS_TOKEN, null)
    val annotationsMarker = builder.mark
    while (annotation.parse(builder)) {}
    annotationsMarker.done(ScalaElementTypes.ANNOTATIONS)
    annotationsMarker.setCustomEdgeTokenBinders(ScalaTokenBinders.DEFAULT_LEFT_EDGE_BINDER, null)
    //parsing modifiers
    val modifierMarker = builder.mark
    while (Modifier.parse(builder)) {}
    //could be case modifier
    val caseMarker = builder.mark
    if (builder.getTokenType == ScalaTokenTypes.kCASE)
      builder.advanceLexer() //Ate case
    //parsing template body
    builder.getTokenType match {
      case ScalaTokenTypes.kCLASS =>
        caseMarker.drop()
        modifierMarker.done(ScalaElementTypes.MODIFIERS)
        builder.advanceLexer() //Ate class
        if (classDef parse builder) {
          templateMarker.done(ScalaElementTypes.CLASS_DEF)
        } else {
          templateMarker.drop()
        }
        true
      case ScalaTokenTypes.kOBJECT =>
        caseMarker.drop()
        modifierMarker.done(ScalaElementTypes.MODIFIERS)
        builder.advanceLexer() //Ate object
        if (objectDef parse builder) {
          templateMarker.done(ScalaElementTypes.OBJECT_DEF)
        } else {
          templateMarker.drop()
        }
        true
      case ScalaTokenTypes.kTRAIT =>
        caseMarker.rollbackTo()
        modifierMarker.done(ScalaElementTypes.MODIFIERS)
        builder.getTokenType match {
          case ScalaTokenTypes.kTRAIT => {
            builder.advanceLexer() //Ate trait
            if (traitDef.parse(builder)) {
              templateMarker.done(ScalaElementTypes.TRAIT_DEF)
            } else {
              templateMarker.drop()
            }
            true
          }
          // In this way wrong case modifier
          case _ => {
            builder error ErrMsg("wrong.case.modifier")
            builder.advanceLexer() //Ate case
            builder.getTokenText
            builder.advanceLexer() //Ate trait
            traitDef.parse(builder)
            templateMarker.done(ScalaElementTypes.TRAIT_DEF)
            true
          }
        }
      //it's error
      case _ =>
        templateMarker.rollbackTo()
        //builder.advanceLexer //Ate something
        false
    }
  }
}

