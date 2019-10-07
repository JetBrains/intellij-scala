package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifier
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Annotations

/**
 * [[TmplDef]] ::= [[Annotations]] { [[Modifier]] }
 * ['case'] 'class' [[ClassDef]]
 * | ['case'] 'object' [[ObjectDef]]
 * | 'trait' [[TraitDef]]
 * | 'enum' [[EnumDef]]
 * | 'given' [[GivenDef]]
 *
 * @author Alexander Podkhalyuzin
 *         Date: 05.02.2008
 **/
object TmplDef extends ParsingRule {

  import ScalaElementType._
  import lexer.ScalaTokenType._
  import lexer.ScalaTokenTypes.kCASE

  override final def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    val templateMarker = builder.mark()
    templateMarker.setCustomEdgeTokenBinders(ScalaTokenBinders.PRECEDING_COMMENTS_TOKEN, null)

    Annotations.parseAndBindToLeft()

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
      case _ =>
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
          case TraitKeyword =>
            caseMarker.rollbackTo()
            builder.error(ScalaBundle.message("wrong.case.modifier"))
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
                            (implicit builder: ScalaPsiBuilder): Option[(ParsingRule, IElementType)] =
    builder.getTokenType match {
      case ClassKeyword => Some(ClassDef, ClassDefinition)
      case ObjectKeyword => Some(ObjectDef, ObjectDefinition)
      case TraitKeyword => Some(
        if (caseState) ParsingRule.AlwaysTrue else TraitDef,
        TraitDefinition
      )
      case EnumKeyword => Some(EnumDef, EnumDefinition)
      case GivenKeyword => None // TODO
      case _ => None
    }
}

