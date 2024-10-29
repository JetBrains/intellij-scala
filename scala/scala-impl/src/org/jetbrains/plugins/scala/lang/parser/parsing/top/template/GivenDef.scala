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
import org.jetbrains.plugins.scala.lang.parser.parsing.params.{ParamClause, ParamClauses, TypeParamClause}
import org.jetbrains.plugins.scala.lang.parser.parsing.types.AnnotType
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, ScalaElementType}

import scala.annotation.tailrec

/**
 * {{{
 * TmplDef            ::= given (OldGivenDef | NewGivenDef) |  ...
 * OldGivenDef        ::= [OldGivenSig] (AnnotType [‘=’ Expr] | StructuralInstance)
 * StructuralInstance ::= Constructor {'with' Constructor} ['with' GivenTemplateBody]
 * OldGivenSig        ::= [id] [DefTypeParamClause] {UsingParamClause} ‘:’         -- one of `id`, `DefParamClause`, `UsingParamClause` must be present
 * NewGivenDef        ::= [id `:`] NewGivenSig
 * NewGivenSig        ::= {GivenConditional '=>'} GivenImpl
 * GivenImpl          ::= AnnotType '=' Expr
 *                      | TypeDefinitionParents [GivenTemplateBody]
 * GivenConditional   ::= TypeParamClause
 *                      | ParamClause
 *                      | AnnotType
 * }}}
 */
object GivenDef {
  def parse(templateMarker: PsiBuilder.Marker)(implicit builder: ScalaPsiBuilder): Boolean = {
    assert(builder.isScala3)
    assert(builder.getTokenType == ScalaTokenType.GivenKeyword)

    builder.advanceLexer() // ate given

    OldGivenDef.parse(templateMarker) || {
      if (builder.features.`new context bounds and givens`) NewGivenDef.parse(templateMarker)
      else {
        templateMarker.drop()
        false
      }
    }
  }
}

object NewGivenDef {
  def parse(templateMarker: PsiBuilder.Marker)(implicit builder: ScalaPsiBuilder): Boolean = {
    val nameIdMarker = builder.mark()

    if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER) {
      builder.advanceLexer() // ate id

      if (builder.getTokenType == ScalaTokenTypes.tCOLON) {
        builder.advanceLexer() // ate `:`
        nameIdMarker.drop()
      } else nameIdMarker.rollbackTo()
    } else nameIdMarker.rollbackTo()

    NewGivenSig.parse(templateMarker)
  }
}

object NewGivenSig {
  @tailrec
  def parse(
    marker:             PsiBuilder.Marker,
    allowTypeParams:    Boolean                   = true,
    paramClausesMarker: Option[PsiBuilder.Marker] = None
  )(implicit
    builder: ScalaPsiBuilder
  ): Boolean = {
    val typeParamClauseFollows = builder.getTokenType == ScalaTokenTypes.tLSQBRACKET

    val clausesMarker =
      paramClausesMarker.orElse(
        Option.when(!typeParamClauseFollows)(builder.mark())
      )

    if (GivenConditional.parse(allowTypeParams)) {
      val hasTypeParams = builder.lookBack(ScalaTokenTypes.tRSQBRACKET)
      NewGivenSig.parse(marker, allowTypeParams = allowTypeParams && !hasTypeParams, clausesMarker)
    } else {
      clausesMarker.foreach(_.done(ScalaElementType.PARAM_CLAUSES))
      GivenImpl.parse(marker)
    }
  }
}

object GivenConditional {
  def parse(allowTypeParams: Boolean)(implicit builder: ScalaPsiBuilder): Boolean = {
    val fallbackMarker = builder.mark()

    val parsed =
      (allowTypeParams && TypeParamClause()) ||
        ParamClause() ||
    {
      val paramClauseMarker = builder.mark()
      val paramMarker       = builder.mark()
      val paramTypeMarker       = builder.mark()

      val parsedParamType = AnnotType(isPattern = false)
      if (!parsedParamType) {
        paramMarker.drop()
        paramClauseMarker.drop()
      } else {
        paramTypeMarker.done(ScalaElementType.PARAM_TYPE)
        paramMarker.done(ScalaElementType.PARAM)
        paramClauseMarker.done(ScalaElementType.PARAM_CLAUSE)
      }

      parsedParamType
    }

    if (!parsed) {
      fallbackMarker.drop()
      false
    } else {
      val hasFunSign = builder.getTokenType == ScalaTokenTypes.tFUNTYPE

      if (hasFunSign) {
        fallbackMarker.drop()
        builder.advanceLexer() // ate `=>`
      } else fallbackMarker.rollbackTo()

      hasFunSign
    }
  }
}

object GivenImpl {
  private def parseStructuralCase(
    templateMarker: PsiBuilder.Marker
  )(implicit builder: ScalaPsiBuilder): Boolean = {
    val extendsBlockMarker = builder.mark()

    if (TypeDefinitionParents()) {
      TemplateBody()
      extendsBlockMarker.done(ScalaElementType.EXTENDS_BLOCK)
      End()
      templateMarker.done(ScalaElementType.GivenDefinition)
      true
    } else {
      extendsBlockMarker.drop()
      templateMarker.drop()
      false
    }
  }

  def parse(templateMarker: PsiBuilder.Marker)(implicit builder: ScalaPsiBuilder): Boolean = {
    val fallbackMarker = builder.mark()

    if (AnnotType(isPattern = false)) {
      if (builder.getTokenType == ScalaTokenTypes.tASSIGN) {
        builder.advanceLexer() // ate `=`
        fallbackMarker.drop()
        if (!ExprInIndentationRegion()) builder.error(ErrMsg("expression.expected"))
        templateMarker.done(ScalaElementType.GIVEN_ALIAS_DEFINITION)
        true
      } else {
        fallbackMarker.rollbackTo()
        parseStructuralCase(templateMarker)
      }
    } else {
      fallbackMarker.drop()
      parseStructuralCase(templateMarker)
    }
  }
}

object OldGivenDef {
  def parse(templateMarker: PsiBuilder.Marker)(implicit builder: ScalaPsiBuilder): Boolean = {
    val sigMarker = {
      val marker = builder.mark()

      if (OldGivenSig()) Option(marker)
      else {
        marker.drop()
        None
      }
    }

    parseGivenAlias(templateMarker, sigMarker) ||
      parseGivenDefinition(templateMarker, sigMarker)
  }

  private def parseGivenAlias(
    templateMarker: PsiBuilder.Marker,
    sigMarker:      Option[PsiBuilder.Marker]
  )(implicit
    builder: ScalaPsiBuilder
  ): Boolean = {
    val aliasMarker  = builder.mark()
    val hasSignature = sigMarker.nonEmpty

    //NOTE: using AnnotType instead of Type, because
    //Type would parse `given Foo with { ... }` as a CompoundType instead of ExtendsBlock
    val typeAnnotationParsed = AnnotType(isPattern = false)

    val tokenType = builder.getTokenType

    val isAliasDefinition = tokenType == ScalaTokenTypes.tASSIGN

    if (!isAliasDefinition && !hasSignature && builder.features.`new context bounds and givens`) {
      //3.6+ unnamed abstract givens are parsed as empty ScGivenDefinitions instead
      aliasMarker.rollbackTo()
      return false
    }

    val isAliasDeclaration = !LPAREN_WITH_TOKEN_SET.contains(tokenType)
    val isGivenAlias = isAliasDefinition || isAliasDeclaration

    if (!typeAnnotationParsed) {
      if (hasSignature && isGivenAlias) {
        //parse incomplete given alias definition during typing:
        //given value: <Caret> =
        builder.error(ScalaBundle.message("wrong.type"))
      } else {
        aliasMarker.rollbackTo()
        return false
      }
    }

    if (isGivenAlias) {
      if (!hasSignature)
        builder.mark().done(ScalaElementType.PARAM_CLAUSES)

      val elementType = if (isAliasDefinition) {
        builder.advanceLexer() // ate =

        if (!ExprInIndentationRegion())
          builder.error(ScalaBundle.message("expression.expected"))

        End()
        ScalaElementType.GIVEN_ALIAS_DEFINITION
      } else ScalaElementType.GIVEN_ALIAS_DECLARATION

      aliasMarker.drop()
      sigMarker.foreach(_.drop())
      templateMarker.done(elementType)
      true
    } else {
      aliasMarker.rollbackTo()
      false
    }
  }

  private val nonConstructorStartId = ScalaTokenTypes.SOFT_KEYWORDS.getTypes.map(_.toString).toSet

  private def parseGivenDefinition(
    templateMarker: PsiBuilder.Marker,
    sigMarker:      Option[PsiBuilder.Marker]
  )(implicit
    builder: ScalaPsiBuilder
  ): Boolean = {
    val extendsBlockMarker = builder.mark()
    val templateParents = builder.mark()

    if (!Constructor()) {
      if (builder.getTokenType == ScalaTokenTypes.kWITH) {
        //parse incomplete given structural instance during typing:
        //given value: <CARET> with MyTrait with {}
        builder.error(ScalaBundle.message("wrong.type"))
      }
      else {
        templateParents.drop()
        extendsBlockMarker.drop()
        sigMarker.foreach(_.rollbackTo())
        return false
      }
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

    if (builder.features.`new context bounds and givens` &&
      builder.getTokenType == ScalaTokenTypes.tFUNTYPE) {
      // this is a GivenConditional, new style given should be parsed instead
      templateParents.drop()
      extendsBlockMarker.drop()
      sigMarker.foreach(_.rollbackTo())
      return false
    }

    parseConstructors()

    templateParents.done(ScalaElementType.TEMPLATE_PARENTS)

    val canParseBody = {
      val parsedWith = builder.getTokenType == ScalaTokenTypes.kWITH
      if (parsedWith) builder.advanceLexer() // ate `with`
      parsedWith
    } || builder.features.`new context bounds and givens`


    if (canParseBody) {
      if (!GivenTemplateBody()) builder.error(ScalaBundle.message("lbrace.expected"))
    }

    extendsBlockMarker.done(ScalaElementType.EXTENDS_BLOCK)

    End()
    sigMarker.foreach(_.drop())
    templateMarker.done(ScalaElementType.GivenDefinition)
    true
  }

  private val LPAREN_WITH_TOKEN_SET = TokenSet.create(
    ScalaTokenTypes.tLPARENTHESIS,
    ScalaTokenTypes.tLSQBRACKET,
    ScalaTokenTypes.tLBRACE,
    ScalaTokenTypes.tFUNTYPE,
    ScalaTokenTypes.kWITH,
    ScalaTokenTypes.tCOLON,
  )
}

object OldGivenSig extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val givenSigMarker = builder.mark()

    // let's try to find out if we are parsing a signature, for more graceful error reporting
    var isSignatureForSure = false

    val hasIdentifier = builder.getTokenType == ScalaTokenTypes.tIDENTIFIER
    if (hasIdentifier) {
      builder.advanceLexer() // ate id
    }

    if (TypeParamClause()) {
      // Cannot be `given [T] =>> T = ???;` because given only allows AnnotType
      // But if an identifier was parsed, it could be `given Type[T] = ???;`
      isSignatureForSure ||= !hasIdentifier
    }

    if (builder.getTokenType == ScalaTokenTypes.tLPARENTHESIS) {
      // parsing just a `(` it could be a paranthesised type
      // but if there is a `using` it cannot be a type so we can assume that this is a parameter clause
      // except if there was an identifier, then it could be a constructor invocation `given Foo(using 3);`
      isSignatureForSure ||= !hasIdentifier && builder.predict(_.getTokenText == "using")
    }

    ParamClauses()

    if (builder.getTokenType == ScalaTokenTypes.tCOLON) {
      builder.advanceLexer() // ate :
      givenSigMarker.drop()
      true
    } else if (isSignatureForSure) {
      builder.error(ScalaBundle.message("colon.expected"))
      givenSigMarker.drop()
      true
    } else {
      givenSigMarker.rollbackTo()
      false
    }
  }
}
