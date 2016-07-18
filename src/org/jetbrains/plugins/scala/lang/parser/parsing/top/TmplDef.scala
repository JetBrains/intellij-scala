package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import com.intellij.psi.tree.IElementType
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

  override protected val elementTypes = ScalaElementTypes
}

trait TmplDef {
  protected val classDef: ClassDef
  protected val objectDef: ObjectDef
  protected val traitDef: TraitDef
  protected val annotation: Annotation

  protected val elementTypes: ElementTypes

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val templateMarker = builder.mark()
    templateMarker.setCustomEdgeTokenBinders(ScalaTokenBinders.PRECEEDING_COMMENTS_TOKEN, null)

    val annotationsMarker = builder.mark()
    while (annotation.parse(builder)) {
    }
    annotationsMarker.done(ScalaElementTypes.ANNOTATIONS)
    annotationsMarker.setCustomEdgeTokenBinders(ScalaTokenBinders.DEFAULT_LEFT_EDGE_BINDER, null)

    val modifierMarker = builder.mark()
    while (Modifier.parse(builder)) {
    }
    val caseState = isCaseState(builder)
    modifierMarker.done(ScalaElementTypes.MODIFIERS)

    templateParser(builder.getTokenType, caseState) match {
      case Some((parse, elementType)) =>
        builder.advanceLexer()
        if (parse(builder)) {
          templateMarker.done(elementType)
        } else {
          templateMarker.drop()
        }

        true
      case None =>
        templateMarker.rollbackTo()
        false
    }
  }

  private def isCaseState(builder: ScalaPsiBuilder) = {
    val caseMarker = builder.mark()
    val result = builder.getTokenType match {
      case ScalaTokenTypes.kCASE =>
        builder.advanceLexer() // Ate case
        true
      case _ => false
    }

    builder.getTokenType match {
      case ScalaTokenTypes.kTRAIT if result =>
        caseMarker.rollbackTo()
        builder.error(ErrMsg("wrong.case.modifier"))
        builder.advanceLexer() // Ate case
      case _ => caseMarker.drop()
    }
    result
  }

  private def templateParser(tokenType: IElementType, caseState: Boolean) = tokenType match {
    case ScalaTokenTypes.kCLASS => Some(classDef.parse _, elementTypes.classDefinition)
    case ScalaTokenTypes.kOBJECT => Some(objectDef.parse _, elementTypes.objectDefinition)
    case ScalaTokenTypes.kTRAIT =>
      def parse(builder: ScalaPsiBuilder): Boolean = {
        val result = traitDef.parse(builder)
        if (caseState) true else result
      }
      Some(parse _, elementTypes.traitDefinition)
    case _ => None
  }
}

