package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifier
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Annotation

/**
* @author Alexander Podkhalyuzin
* Date: 11.02.2008
*/

/*
 * Dcl ::= [{Annotation} {Modifier}]
 *          ('val' ValDcl
 *         | 'var' VarDcl
 *         | 'def' FunDcl
 *         | 'type' {nl} TypeDcl)
 */
object Dcl extends Dcl {
  override protected val funDcl = FunDcl
  override protected val annotation = Annotation
  override protected val typeDcl = TypeDcl
  override protected val varDcl = VarDcl
  override protected val valDcl = ValDcl
}

trait Dcl {
  protected val funDcl: FunDcl
  protected val annotation: Annotation
  protected val valDcl: ValDcl
  protected val varDcl: VarDcl
  protected val typeDcl: TypeDcl

  def parse(builder: ScalaPsiBuilder): Boolean = parse(builder,isMod = true)
  def parse(builder: ScalaPsiBuilder, isMod: Boolean): Boolean = {
    val dclMarker = builder.mark
    dclMarker.setCustomEdgeTokenBinders(ScalaTokenBinders.PRECEEDING_COMMENTS_TOKEN, null)
    if (isMod) {
      val annotationsMarker = builder.mark
      while (annotation.parse(builder)) {}
      annotationsMarker.done(ScalaElementTypes.ANNOTATIONS)
      annotationsMarker.setCustomEdgeTokenBinders(ScalaTokenBinders.DEFAULT_LEFT_EDGE_BINDER, null)
      //parse modifiers
      val modifierMarker = builder.mark
      while (Modifier.parse(builder)) {}
      modifierMarker.done(ScalaElementTypes.MODIFIERS)
    } else {
      //empty annotations
      val annotationsMarker = builder.mark
      annotationsMarker.done(ScalaElementTypes.ANNOTATIONS)
      //empty modifiers
      val modifierMarker = builder.mark
      modifierMarker.done(ScalaElementTypes.MODIFIERS)
    }
    //Look for val,var,def or type
    builder.getTokenType match {
      case ScalaTokenTypes.kVAL =>
        if (valDcl parse builder) {
          dclMarker.done(ScalaElementTypes.VALUE_DECLARATION)
          true
        }
        else {
          dclMarker.rollbackTo()
          false
        }
      case ScalaTokenTypes.kVAR =>
        if (varDcl parse builder) {
          dclMarker.done(ScalaElementTypes.VARIABLE_DECLARATION)
          true
        }
        else {
          dclMarker.drop()
          false
        }
      case ScalaTokenTypes.kDEF =>
        if (funDcl parse builder) {
          dclMarker.done(ScalaElementTypes.FUNCTION_DECLARATION)
          true
        }
        else {
          dclMarker.drop()
          false
        }
      case ScalaTokenTypes.kTYPE =>
        if (typeDcl parse builder) {
          dclMarker.done(ScalaElementTypes.TYPE_DECLARATION)
          true
        }
        else {
          dclMarker.drop()
          false
        }
      case _ =>
        dclMarker.rollbackTo()
        false
    }
  }
}