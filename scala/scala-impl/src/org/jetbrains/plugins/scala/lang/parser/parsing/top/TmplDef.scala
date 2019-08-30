package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifier
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Annotation
import org.jetbrains.plugins.scala.lang.parser.parsing.statements.GivenDef

/**
 * [[TmplDef]] ::= { [[Annotation]] } { [[Modifier]] }
 * [`case`] `class` [[ClassDef]]
 * | [`case`] `object` [[ObjectDef]]
 * | `trait` [[TraitDef]]
 * | `enum` [[EnumDef]]
 *
 * @author Alexander Podkhalyuzin
 *         Date: 05.02.2008
 **/
object TmplDef extends ParsingRule {

  import ScalaElementType._
  import lexer.ScalaTokenTypes._

  override def parse()(implicit builder: ScalaPsiBuilder): Boolean = {
    val templateMarker = builder.mark()
    templateMarker.setCustomEdgeTokenBinders(ScalaTokenBinders.PRECEDING_COMMENTS_TOKEN, null)

    val annotationsMarker = builder.mark()
    while (Annotation.parse(builder)) {
    }
    annotationsMarker.done(ANNOTATIONS)
    annotationsMarker.setCustomEdgeTokenBinders(ScalaTokenBinders.DEFAULT_LEFT_EDGE_BINDER, null)

    val modifierMarker = builder.mark()
    while (Modifier.parse(builder)) {
    }
    val caseState = isCaseState
    modifierMarker.done(MODIFIERS)

    templateParser(caseState) match {
      case Some((parse, elementType)) =>
        builder.advanceLexer()
        if (parse()) {
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

  private def isCaseState(implicit builder: ScalaPsiBuilder) = {
    val caseMarker = builder.mark()
    builder.getTokenType match {
      case `kCASE` =>
        builder.advanceLexer() // Ate case

        builder.getTokenType match {
          case `kTRAIT` =>
            caseMarker.rollbackTo()
            builder.error(ErrMsg("wrong.case.modifier"))
            builder.advanceLexer() // Ate case
          case _ =>
            caseMarker.drop()
        }

        true
      case _ =>
        caseMarker.drop()
        false
    }
  }

  private def templateParser(caseState: Boolean)
                            (implicit builder: ScalaPsiBuilder): Option[(() => Boolean, IElementType)] =
    builder.getTokenType match {
      case `kCLASS` => Some(() => ClassDef(), CLASS_DEFINITION)
      case `kOBJECT` => Some(() => ObjectDef.parse(builder), OBJECT_DEFINITION)
      case `kTRAIT` => Some(
        () => if (caseState) true else TraitDef.parse(builder),
        TRAIT_DEFINITION
      )
      case lexer.ScalaTokenType.IsEnum() => Some(() => EnumDef(), ENUM_DEFINITION)
      case lexer.ScalaTokenType.GivenKeyword() => Some(() => GivenDef.parse, ???)
      case _ => None
    }
}

