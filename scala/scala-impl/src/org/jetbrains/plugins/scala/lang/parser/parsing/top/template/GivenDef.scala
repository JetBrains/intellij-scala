package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top
package template

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.base.{Constructor, End}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.ExprInIndentationRegion
import org.jetbrains.plugins.scala.lang.parser.parsing.params.{ParamClauses, TypeParamClause}
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type

import scala.annotation.tailrec

/**
 * {{{
 * TmplDef           ::=  given’ GivenDef
 *                     |  ...
 * GivenDef          ::=  [GivenSig] (AnnotType [‘=’ Expr] | StructuralInstance)
 * GivenSig          ::=  [id] [DefTypeParamClause] {UsingParamClause} ‘:’         -- one of `id`, `DefParamClause`, `UsingParamClause` must be present
 * }}}
 */
object GivenDef {
  def parse(templateMarker: PsiBuilder.Marker)(implicit builder: ScalaPsiBuilder): Unit = {
    assert(builder.isScala3)
    assert(builder.getTokenType == ScalaTokenType.GivenKeyword)

    val iw = builder.currentIndentationWidth
    builder.advanceLexer() // ate given

    GivenSig()

    if(parseGivenAlias()) {
      End(iw)
      templateMarker.done(ScalaElementType.GIVEN_ALIAS)
    } else if (parseGivenDefinition()) {
      End(iw)
      templateMarker.done(ScalaElementType.GivenDefinition)
    } else {
      templateMarker.drop()
    }
  }

  private def parseGivenAlias()(implicit builder: ScalaPsiBuilder): Boolean = {
    val aliasMarker = builder.mark()
    if (Type.parse(builder) && builder.getTokenType == ScalaTokenTypes.tASSIGN) {
      // given alias
      aliasMarker.drop()
      builder.advanceLexer() // ate =

      if (!ExprInIndentationRegion()) {
        builder.error(ScalaBundle.message("expression.expected"))
      }

      true
    } else {
      aliasMarker.rollbackTo()
      false
    }
  }

  private val nonConstructorStartId = ScalaTokenTypes.SOFT_KEYWORDS.getTypes.map(_.toString).toSet
  private def parseGivenDefinition()(implicit builder: ScalaPsiBuilder): Boolean = {
    val extendsBlockMarker = builder.mark()
    val templateParents = builder.mark()

    if (!Constructor()) {
      templateParents.drop()
      extendsBlockMarker.rollbackTo()
      return false
    }

    @tailrec
    def parseConstructors(): Unit = {
      if (builder.getTokenType == ScalaTokenTypes.kWITH) {
        val fallbackMarker = builder.mark()
        builder.advanceLexer() // ate with

        val proceedWithConstructorInvocation =
          builder.getTokenType match {
            case ScalaTokenTypes.tLBRACE => false
            case ScalaTokenTypes.tIDENTIFIER if nonConstructorStartId(builder.getTokenText) => false
            case _ => !builder.newlineBeforeCurrentToken // new line after width is not supported
          }

        if (proceedWithConstructorInvocation && Constructor()) {
          fallbackMarker.drop()
          parseConstructors()
        } else {
          fallbackMarker.rollbackTo()
        }
      }
    }
    parseConstructors()

    templateParents.done(ScalaElementType.TEMPLATE_PARENTS)

    if (builder.getTokenType == ScalaTokenTypes.kWITH) {
      builder.advanceLexer()
    } else {
      builder.error(ScalaBundle.message("expected.with"))
    }
    GivenTemplateBody()
    extendsBlockMarker.done(ScalaElementType.EXTENDS_BLOCK)
    true
  }
}

object GivenSig extends ParsingRule {
  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    val givenSigMarker = builder.mark()

    if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER) {
      builder.advanceLexer() // ate id
    }

    TypeParamClause.parse(builder)

    ParamClauses.parse(builder)

    if (builder.getTokenType == ScalaTokenTypes.tCOLON) {
      builder.advanceLexer() // ate :
      givenSigMarker.drop()
      true
    } else {
      givenSigMarker.rollbackTo()
      false
    }
  }
}