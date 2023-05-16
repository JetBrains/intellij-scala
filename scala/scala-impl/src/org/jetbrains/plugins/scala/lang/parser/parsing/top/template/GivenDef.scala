package org.jetbrains.plugins.scala.lang.parser.parsing.top
package template

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.base.{Constructor, End}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.ExprInIndentationRegion
import org.jetbrains.plugins.scala.lang.parser.parsing.params.{ParamClauses, TypeParamClause}
import org.jetbrains.plugins.scala.lang.parser.parsing.types.AnnotType
import org.jetbrains.plugins.scala.lang.parser.{IndentationWidth, ScalaElementType}

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

    val hasSignature = GivenSig()

    if (!parseGivenAlias(hasSignature, iw, templateMarker) &&
      !parseGivenDefinition(iw, templateMarker)) {
      templateMarker.drop()
    }
  }

  private def parseGivenAlias(hasSignature: Boolean, iw: IndentationWidth, templateMarker: PsiBuilder.Marker)
                             (implicit builder: ScalaPsiBuilder): Boolean = {
    val aliasMarker = builder.mark()
    if (AnnotType(isPattern = false)) {
      val tokenType = builder.getTokenType
      val isAliasDefinition = tokenType == ScalaTokenTypes.tASSIGN
      val isAliasDeclaration = !LPAREN_WITH_TOKEN_SET.contains(tokenType)

      if (isAliasDefinition || isAliasDeclaration) {
        if (!hasSignature)
          builder.mark().done(ScalaElementType.PARAM_CLAUSES)

        val elementType = if (isAliasDefinition) {
          builder.advanceLexer() // ate =

          if (!ExprInIndentationRegion())
            builder.error(ScalaBundle.message("expression.expected"))

          End(iw)
          ScalaElementType.GIVEN_ALIAS_DEFINITION
        } else ScalaElementType.GIVEN_ALIAS_DECLARATION

        aliasMarker.drop()
        templateMarker.done(elementType)
        true
      } else {
        aliasMarker.rollbackTo()
        false
      }
    } else {
      aliasMarker.rollbackTo()
      false
    }
  }

  private val nonConstructorStartId = ScalaTokenTypes.SOFT_KEYWORDS.getTypes.map(_.toString).toSet

  private def parseGivenDefinition(iw: IndentationWidth, templateMarker: PsiBuilder.Marker)
                                  (implicit builder: ScalaPsiBuilder): Boolean = {
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

      if (!GivenTemplateBody())
        builder.error(ScalaBundle.message("lbrace.expected"))
    }

    extendsBlockMarker.done(ScalaElementType.EXTENDS_BLOCK)

    End(iw)
    templateMarker.done(ScalaElementType.GivenDefinition)
    true
  }

  private val LPAREN_WITH_TOKEN_SET = TokenSet.create(
    ScalaTokenTypes.tLPARENTHESIS,
    ScalaTokenTypes.kWITH
  )
}

object GivenSig extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val givenSigMarker = builder.mark()

    if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER) {
      builder.advanceLexer() // ate id
    }

    TypeParamClause()

    ParamClauses()

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
