package org.jetbrains.plugins.scala.lang.parser.parsing.top
package template

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.{IElementType, TokenSet}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.base.{Constructor, End}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.ExprInIndentationRegion
import org.jetbrains.plugins.scala.lang.parser.parsing.params.{ParamClause, ParamClauses, TypeParamClause, TypesAsParams}
import org.jetbrains.plugins.scala.lang.parser.parsing.types.{AnnotType, InfixType, Type}
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, ScalaElementType}

import scala.annotation.tailrec

/**
 * {{{
 * TmplDef            ::= given (OldGivenDef | NewGivenDef) |  ...
 *
 * OldGivenDef        ::= [OldGivenSig] (AnnotType [‘=’ Expr] | StructuralInstance)
 * StructuralInstance ::= Constructor {'with' Constructor} ['with' GivenTemplateBody]
 * OldGivenSig        ::= [id] [DefTypeParamClause] {UsingParamClause} ‘:’         -- one of `id`, `DefParamClause`, `UsingParamClause` must be present
 *
 * NewGivenDef        ::= [id `:`] NewGivenSig
 * NewGivenSig        ::=  GivenImpl
 *                      |  '(' ')' '=>' GivenImpl
 *                      |  GivenConditional '=>' GivenSig
 * GivenImpl          ::=  GivenType ([‘=’ Expr] | TemplateBody)
 *                      |  ConstrApps TemplateBody
 * GivenConditional   ::=  DefTypeParamClause
 *                      |  DefTermParamClause
 *                      |  '(' FunArgTypes ')'
 *                      |  GivenType
 * GivenType         ::=  AnnotType {id [nl] AnnotType}
 * }}}
 */
object GivenDef {
  def parse(templateMarker: PsiBuilder.Marker)(implicit builder: ScalaPsiBuilder): Boolean = {
    assert(builder.isScala3)
    assert(builder.getTokenType == ScalaTokenType.GivenKeyword)

    builder.advanceLexer() // ate given

    val elementType =
      if (builder.features.`new context bounds and givens`) {
        val firstTryMarker = builder.mark()

        def isEndOfStatementNow(x: Any): Boolean =
          builder.eof() ||
            builder.newlineBeforeCurrentToken ||
            builder.getTokenType == ScalaTokenTypes.tSEMICOLON ||
            builder.getTokenType == ScalaTokenTypes.tRBRACE

        // try parsing the new given def first, but if that encounters any error, rollback
        // then try the old given def and if that fails rollback again
        // then parse the new given def again, but this time eat what you can and don't rollback
        val (newDefErrors, elementType) = builder.countDoneErrorsIn {
          Some(NewGivenDef.parse())
            .filter(isEndOfStatementNow)
        }

        if (elementType.nonEmpty && newDefErrors == 0) {
          firstTryMarker.drop()
          elementType
        } else {
          firstTryMarker.rollbackTo()
          val secondTryMarker = builder.mark()
          val (secondOldErrors, elementType) = builder.countDoneErrorsIn {
            OldGivenDef.parse()
              .filter(isEndOfStatementNow)
          }

          if (elementType.nonEmpty && secondOldErrors == 0) {
            secondTryMarker.drop()
            elementType
          } else {
            secondTryMarker.rollbackTo()
            Some(NewGivenDef.parse())
          }
        }
      } else {
        OldGivenDef.parse()
      }

    elementType match {
      case Some(elementType) =>
        templateMarker.done(elementType)
        true
      case None =>
        templateMarker.drop()
        false
    }
  }
}

object NewGivenDef {
  def parse()(implicit builder: ScalaPsiBuilder): IElementType = {
    val nameIdMarker = builder.mark()

    if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER) {
      builder.advanceLexer() // ate id

      if (builder.getTokenType == ScalaTokenTypes.tCOLON) {
        builder.advanceLexer() // ate `:`
        if (builder.newlineBeforeCurrentToken) {
          // if there is a newline after the colon we have to parse a given definition alá
          // given Test:
          //   def test() = 3
          nameIdMarker.rollbackTo()
        } else {
          nameIdMarker.drop()
        }
      } else nameIdMarker.rollbackTo()
    } else nameIdMarker.rollbackTo()

    NewGivenSig()
  }
}

object NewGivenSig {
  def apply()(implicit builder: ScalaPsiBuilder): IElementType = {
    // first try to parse one type param clause alá `[T]`
    if (TypeParamClause()) {
      if (builder.getTokenType == ScalaTokenTypes.tFUNTYPE) {
        builder.advanceLexer() // ate =>
      } else {
        builder.error(ScalaBundle.message("fun.sign.expected"))
      }
    }

    val clausesMarker = builder.mark()

    while ({
      val rollbackMarker = builder.mark()

      val isParamClause =
        builder.getTokenType == ScalaTokenTypes.tLPARENTHESIS &&
        builder.predict { builder =>
          if (builder.getTokenText == "using") {
            builder.advanceLexer()
          }

          builder.getTokenType == ScalaTokenTypes.tIDENTIFIER && {
            builder.advanceLexer()
            builder.getTokenType == ScalaTokenTypes.tCOLON
          }
        }

      val parsed =
        if (isParamClause) {
          //   (x: Int, y: Int) =>
          ParamClause()
        } else {
          //   (Int, Int) =>
          // or
          //   Int =>
          parseFunParams() ||
            parseSingleGivenType()
        }

      if (parsed) {
        if (builder.getTokenType == ScalaTokenTypes.tFUNTYPE) {
          builder.advanceLexer() // ate =>
          rollbackMarker.drop()
          true
        } else if (isParamClause) {
          // Parsing `()` or `(x: Int)` etc cannot be the beginning of a GivenImpl,
          // so we know it was supposed to be a GivenConditional
          rollbackMarker.drop()
          builder.error(ScalaBundle.message("fun.sign.expected"))
          true
        } else {
          // after parsing `(Int, Int)` (or more importantly `(Int)`) or `Int`, we don't know whether that was supposed to
          // be a GivenConditional or part of the GivenImpl, so we rollback and try to parse there
          rollbackMarker.rollbackTo()
          false
        }
      } else {
        rollbackMarker.rollbackTo()
        false
      }
    }) ()

    clausesMarker.done(ScalaElementType.PARAM_CLAUSES)

    NewGivenImpl()
  }

  private def parseFunParams()(implicit builder: ScalaPsiBuilder): Boolean = {
    if (builder.getTokenType != ScalaTokenTypes.tLPARENTHESIS) {
      return false
    }

    val parameterClause = builder.mark()
    builder.advanceLexer() // eat (

    val parsedUsing = builder.tryParseSoftKeyword(ScalaTokenType.UsingKeyword)

    if (builder.getTokenType != ScalaTokenTypes.tRPARENTHESIS && !TypesAsParams() && !parsedUsing) {
      parameterClause.rollbackTo()
      return false
    }

    builder.getTokenType match {
      case ScalaTokenTypes.tRPARENTHESIS =>
        builder.advanceLexer() //Ate )
      case _ =>
        builder error ScalaBundle.message("rparenthesis.expected")
    }

    parameterClause.done(ScalaElementType.PARAM_CLAUSE)
    true
  }

  private def parseSingleGivenType()(implicit builder: ScalaPsiBuilder): Boolean = {
    val paramClauseMarker = builder.mark()
    val paramMarker       = builder.mark()
    val paramTypeMarker   = builder.mark()

    if (GivenType()) {
      paramTypeMarker.done(ScalaElementType.PARAM_TYPE)
      paramMarker.done(ScalaElementType.PARAM)
      paramClauseMarker.done(ScalaElementType.PARAM_CLAUSE)
      true
    } else {
      paramTypeMarker.drop()
      paramMarker.drop()
      paramClauseMarker.rollbackTo()
      false
    }
  }
}

object NewGivenImpl {
  def apply()(implicit builder: ScalaPsiBuilder): IElementType = {
    val constructorInvocation = builder.mark()

    val givenTypeIsMissingInAlias = builder.getTokenType == ScalaTokenTypes.tASSIGN
    if (givenTypeIsMissingInAlias) {
      builder.error(ErrMsg("type.expected"))
    }
    if (givenTypeIsMissingInAlias || GivenType()) {
      builder.getTokenType match {
        case ScalaTokenTypes.tASSIGN =>
          constructorInvocation.drop()
          builder.advanceLexer() // ate `=`
          if (!ExprInIndentationRegion()) builder.error(ErrMsg("expression.expected"))
          ScalaElementType.GIVEN_ALIAS_DEFINITION
        case ScalaTokenTypes.tCOLON | ScalaTokenTypes.tLBRACE =>
          constructorInvocation.done(ScalaElementType.CONSTRUCTOR)

          val templateParents = constructorInvocation.precede()
          templateParents.done(ScalaElementType.TEMPLATE_PARENTS)

          TemplateBody()

          val extendsBlock = templateParents.precede()
          extendsBlock.done(ScalaElementType.EXTENDS_BLOCK)
          ScalaElementType.GivenDefinition
        case _ =>
          constructorInvocation.rollbackTo()
          parseStructuralCase()
      }
    } else {
      constructorInvocation.drop()
      parseStructuralCase()
    }
  }

  private def parseStructuralCase()(implicit builder: ScalaPsiBuilder): IElementType = {
    val extendsBlockMarker = builder.mark()
    builder.getProductions
    if (builder.getTokenType == ScalaTokenTypes.kWITH) {
      builder.error(ScalaBundle.message("type.expected"))
      builder.advanceLexer()
    }

    GivenParents()

    if (builder.getTokenType == ScalaTokenTypes.kWITH) {
      builder.error(ScalaBundle.message("expected.more.types"))
      builder.advanceLexer()
    }

    TemplateBody()
    extendsBlockMarker.done(ScalaElementType.EXTENDS_BLOCK)
    End()
    ScalaElementType.GivenDefinition
  }
}

object GivenType extends InfixType {
  override protected def parseSubType(star: Boolean, isPattern: Boolean, typeVariables: Boolean)
                                     (implicit builder: ScalaPsiBuilder): Boolean =
    AnnotType(isPattern = false)

  override protected def errorMessage: String = ScalaBundle.message("type.expected")
}

object OldGivenDef {
  def parse()(implicit builder: ScalaPsiBuilder): Option[IElementType] = {
    val sigMarker = {
      val marker = builder.mark()

      if (OldGivenSig()) Option(marker)
      else {
        marker.drop()
        None
      }
    }

    if (sigMarker.nonEmpty && builder.newlineBeforeCurrentToken) {
      sigMarker.foreach(_.drop())
      builder.error(ScalaBundle.message("type.expected"))
      return Some(ScalaElementType.GIVEN_ALIAS_DECLARATION)
    }

    val elementType = parseGivenAlias(hasSignature = sigMarker.nonEmpty)
    if (elementType.nonEmpty) {
      sigMarker.foreach(_.drop())
      elementType
    } else {
      parseGivenDefinition(sigMarker)
    }
  }

  private def parseGivenAlias(hasSignature: Boolean)
                             (implicit builder: ScalaPsiBuilder): Option[IElementType] = {
    val aliasMarker  = builder.mark()

    //NOTE: using AnnotType instead of Type, because
    //Type would parse `given Foo with { ... }` as a CompoundType instead of ExtendsBlock
    val typeAnnotationParsed = AnnotType(isPattern = false)

    val tokenType = builder.getTokenType

    val isAliasDefinition = tokenType == ScalaTokenTypes.tASSIGN
    val isAliasDeclaration = !LPAREN_WITH_TOKEN_SET.contains(tokenType)
    val isGivenAlias = isAliasDefinition || isAliasDeclaration

    if (!typeAnnotationParsed) {
      if (hasSignature && isGivenAlias) {
        //parse incomplete given alias definition during typing:
        //given value: <Caret> =
        builder.error(ScalaBundle.message("type.expected"))
      } else {
        aliasMarker.rollbackTo()
        return None
      }
    }

    if (isGivenAlias) {
      val elementType =
        if (isAliasDefinition) {
          builder.advanceLexer() // ate =

          if (!ExprInIndentationRegion())
            builder.error(ScalaBundle.message("expression.expected"))

          End()
          ScalaElementType.GIVEN_ALIAS_DEFINITION
        } else ScalaElementType.GIVEN_ALIAS_DECLARATION

      aliasMarker.drop()
      Some(elementType)
    } else {
      aliasMarker.rollbackTo()
      None
    }
  }

  private val nonConstructorStartId = ScalaTokenTypes.SOFT_KEYWORDS.getTypes.map(_.toString).toSet

  private def parseGivenDefinition(sigMarker: Option[PsiBuilder.Marker])
                                  (implicit builder: ScalaPsiBuilder): Option[IElementType] = {
    val extendsBlockMarker = builder.mark()
    val templateParents = builder.mark()

    if (!Constructor()) {
      if (builder.getTokenType == ScalaTokenTypes.kWITH) {
        //parse incomplete given structural instance during typing:
        //given value: <CARET> with MyTrait with {}
        builder.error(ScalaBundle.message("type.expected"))
      }
      else {
        templateParents.drop()
        extendsBlockMarker.drop()
        sigMarker.foreach(_.drop())
        builder.error(ScalaBundle.message("identifier.expected"))
        return Some(ScalaElementType.GIVEN_ALIAS_DECLARATION)
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

    parseConstructors()

    templateParents.done(ScalaElementType.TEMPLATE_PARENTS)

    val canParseBody = {
      val parsedWith = builder.getTokenType == ScalaTokenTypes.kWITH
      if (parsedWith) builder.advanceLexer() // ate `with`
      parsedWith
    }

    if (canParseBody && !GivenTemplateBody()) {
      builder.error(ScalaBundle.message("lbrace.expected"))
    }

    extendsBlockMarker.done(ScalaElementType.EXTENDS_BLOCK)

    End()
    sigMarker.foreach(_.drop())
    Some(ScalaElementType.GivenDefinition)
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

    val hasParamClauses =
      if (builder.getTokenType == ScalaTokenTypes.tLPARENTHESIS) {
        // parsing just a `(` it could be a paranthesised type
        // but if there is a `using` it cannot be a type so we can assume that this is a parameter clause
        // except if there was an identifier, then it could be a constructor invocation `given Foo(using 3);`
        isSignatureForSure ||= !hasIdentifier && builder.predict(_.getTokenText == "using")

        ParamClauses()
      } else false

    def addEmptyParamClausesIfNecessary(): Unit =
      if (!hasParamClauses) {
        // if there was no physical param clause add empty PARAM_CLAUSES
        // after the colon... this makes no sense to the old syntax, but is conformant with the new syntax
        builder.mark().done(ScalaElementType.PARAM_CLAUSES)
      }

    if (builder.getTokenType == ScalaTokenTypes.tCOLON) {
      builder.advanceLexer() // ate :
      givenSigMarker.drop()
      addEmptyParamClausesIfNecessary()
      true
    } else if (isSignatureForSure) {
      builder.error(ScalaBundle.message("colon.expected"))
      givenSigMarker.drop()
      addEmptyParamClausesIfNecessary()
      true
    } else {
      givenSigMarker.rollbackTo()
      builder.mark().done(ScalaElementType.PARAM_CLAUSES)
      false
    }
  }
}
