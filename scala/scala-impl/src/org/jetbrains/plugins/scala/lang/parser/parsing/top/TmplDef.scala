package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.base.{End, Modifier}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Annotations
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.GivenDef

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

    builder.getTokenType match {
      case ClassKeyword =>
        parseTmplRest(ClassDef, templateMarker, ClassDefinition)
        true
      case ObjectKeyword =>
        parseTmplRest(ObjectDef, templateMarker, ObjectDefinition)
        true
      case TraitKeyword if caseState && !builder.isScala3 =>
        templateMarker.drop()
        true
      case TraitKeyword =>
        parseTmplRest(TraitDef, templateMarker, TraitDefinition)
        true
      case EnumKeyword =>
        parseTmplRest(EnumDef, templateMarker, EnumDefinition)
        true

        // Try not to parse a given definition if case was used,
        // because "case given" can be the start of a new case clause
        // Example:
        //  x match {
        //    case 3 => /* template definitions can occur here */
        //    case given X =>
        //  }
      case GivenKeyword if !caseState =>
        GivenDef.parse(templateMarker)
        true
      case _ =>
        templateMarker.rollbackTo()
        false
    }
  }

  private def parseTmplRest(rule: ParsingRule, templateMarker: PsiBuilder.Marker, elementType: IElementType)(implicit builder: ScalaPsiBuilder): Unit = {
    val iw = builder.currentIndentationWidth
    builder.advanceLexer() // ate class
    if (rule()) {
     /**
      * Note: end marker is already parsed in TemplateBody,
      * but there is one edge case when there is no any template body: {{{
      *  class A
      *  end A
      * }}}
      * (notice no colon `:` after class name)
      */
      End(iw)
      templateMarker.done(elementType)
    } else {
      templateMarker.drop()
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
}

