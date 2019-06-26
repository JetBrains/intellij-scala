package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import java.{util => ju}

import com.intellij.codeInsight.completion._
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValue
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator._
import org.jetbrains.plugins.scala.lang.psi.types.api.{ExtractClass, FunctionType}
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiUtil, TypeAdjuster}
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils

final class ExhaustiveMatchCompletionContributor extends ScalaCompletionContributor {

  import ExhaustiveMatchCompletionContributor._

  import reflect.ClassTag

  extend[ScSugarCallExpr, ScMatch, ScSugarCallExpr](
    ScalaKeyword.MATCH
  ) {
    case (sugarCall@ScSugarCallExpr(operand, operation, _), place)
      if !sugarCall.isInstanceOf[ScPrefixExpr] &&
        operation.isAncestorOf(place) => operand.`type`().toOption
    case _ => None
  }

  extend[ScBlockExpr, ScBlockExpr, ScArgumentExprList](
    ScalaKeyword.CASE,
    Some("", "")
  ) {
    case (ExpectedType(FunctionType(_, Seq(targetType))), _) => Some(targetType)
    case _ => None
  }

  private def extend[
    Target <: ScalaPsiElement with Typeable : ClassTag,
    Expression <: ScExpression : ClassTag,
    Capture <: ScalaPsiElement : ClassTag
  ](keywordLookupString: String,
    prefixAndSuffix: PrefixAndSuffix = None)
   (`type`: (Target, PsiElement) => Option[ScType]): Unit = extend(
    CompletionType.BASIC,
    inside[Capture],
    new ClauseCompletionProvider[Target] {

      override protected def addCompletions(typeable: Target, result: CompletionResultSet)
                                           (implicit place: PsiElement): Unit = for {
        PatternGenerationStrategy(strategy) <- `type`(typeable, place)

        lookupElement = buildLookupElement(
          keywordLookupString,
          new ClausesInsertHandler[Expression](strategy, prefixAndSuffix)
        )(itemTextBold = true, tailText = rendererTailText, grayed = true)
      } result.addElement(lookupElement)
    }
  )
}

object ExhaustiveMatchCompletionContributor {

  private[lang] val Exhaustive = "exhaustive"

  private[lang] def rendererTailText = "(" + Exhaustive + ")"

  private type PrefixAndSuffix = Option[(String, String)]

  private[lang] sealed trait PatternGenerationStrategy {

    def patterns: Seq[PatternComponents]
  }

  private[lang] object PatternGenerationStrategy {

    implicit class StrategyExt(private val strategy: PatternGenerationStrategy) extends AnyVal {

      def adjustTypes(components: Seq[PatternComponents],
                      caseClauses: Seq[ScCaseClause]): Unit =
        adjustTypesOnClauses(
          addImports = strategy.isInstanceOf[SealedClassGenerationStrategy],
          caseClauses.zip(components): _*
        )

      def createClauses(prefixAndSuffix: PrefixAndSuffix = None)
                       (implicit place: PsiElement): (Seq[PatternComponents], String) = {
        val (prefix, suffix) = prefixAndSuffix
          .getOrElse(ScalaKeyword.MATCH + " {\n", "\n}")

        val components = strategy.patterns
        val clausesText = components
          .map(_.text)
          .map(createClause)
          .mkString(prefix, "\n", suffix)

        (components, clausesText)
      }
    }

    def createClause(patternText: String)
                    (implicit place: PsiElement): String =
      s"${ScalaKeyword.CASE} $patternText ${ScalaPsiUtil.functionArrow}"

    def unapply(`type`: ScType)
               (implicit place: PsiElement): Option[PatternGenerationStrategy] =
      `type`.extractDesignatorSingleton.orElse {
        Some(`type`)
      }.flatMap {
        case valueType@ScProjectionType(DesignatorOwner(enumClass@NonEmptyScalaEnumeration(values)), _) =>
          toOption {
            values.filter(_.`type`().exists(_.conforms(valueType)))
              .flatMap(_.declaredNames)
          }.map {
            new EnumGenerationStrategy(enumClass, enumClass.qualifiedName, _)
          }
        case valueType@ScDesignatorType(enumClass@NonEmptyJavaEnum(constants)) =>
          Some(new EnumGenerationStrategy(enumClass, valueType.presentableText, constants.map(_.getName)))
        case ExtractClass(SealedDefinition(inheritors)) if inheritors.namedInheritors.nonEmpty =>
          Some(new SealedClassGenerationStrategy(inheritors))
        case _ => None
      }

    def adjustTypesOnClauses(addImports: Boolean,
                             pairs: (ScCaseClause, PatternComponents)*): Unit =
      adjustTypes(addImports, pairs: _*) {
        case ScCaseClause(Some(pattern@ScTypedPattern(typeElement)), _, _) => pattern -> typeElement
      }

    def adjustTypes[E <: ScalaPsiElement](addImports: Boolean,
                                          pairs: (E, PatternComponents)*)
                                         (collector: PartialFunction[E, (ScPattern, ScTypeElement)]): Unit = {
      val findTypeElement = collector.lift
      TypeAdjuster.adjustFor(
        for {
          (element, _) <- pairs
          (_, typeElement) <- findTypeElement(element)
        } yield typeElement,
        addImports = addImports,
        useTypeAliases = false
      )

      for {
        (element, components) <- pairs
        (pattern, typeElement) <- findTypeElement(element)

        patternText = replacementText(typeElement, components)
        replacement = ScalaPsiElementFactory.createPatternFromTextWithContext(patternText, element.getContext, element)
      } pattern.replace(replacement)
    }

    private[this] object NonEmptyScalaEnumeration {

      private[this] val EnumerationFQN = "scala.Enumeration"

      def unapply(`object`: ScObject)
                 (implicit place: PsiElement): Option[Seq[ScValue]] =
        collectMembers(`object`)(_.supers.map(_.qualifiedName).contains(EnumerationFQN))(_.members) {
          case value: ScValue if ResolveUtils.isAccessible(value, place, forCompletion = true) => value
        }
    }

    private[this] object NonEmptyJavaEnum {

      def unapply(clazz: PsiClass): Option[Seq[PsiEnumConstant]] =
        collectMembers(clazz)(_.isEnum)(_.getFields) {
          case constant: PsiEnumConstant => constant
        }
    }

    private[this] def toOption[T](seq: Seq[T]) =
      if (seq.isEmpty) None else Some(seq)

    private[this] def collectMembers[
      C <: PsiClass,
      In <: PsiMember,
      Out <: PsiMember
    ](clazz: C)
     (predicate: C => Boolean)
     (members: C => Seq[In])
     (collector: PartialFunction[In, Out]) =
      if (predicate(clazz)) toOption(members(clazz).collect(collector))
      else None

    private[this] def replacementText(typeElement: ScTypeElement,
                                      components: PatternComponents): String = {
      def referenceText = (typeElement match {
        case SimpleTypeReferenceReference(reference) => reference
        case ScParameterizedTypeElement(SimpleTypeReferenceReference(reference), _) => reference
      }).getText

      typeElement match {
        case simpleTypeElement: ScSimpleTypeElement if simpleTypeElement.singleton => referenceText
        case _ =>
          components match {
            case extractorComponents: ExtractorPatternComponents[_] =>
              extractorComponents.extractorText(referenceText)
            case _ =>
              val name = typeElement.`type`().toOption
                .flatMap(NameSuggester.suggestNamesByType(_).headOption)
                .getOrElse(extensions.Placeholder)
              s"$name: ${typeElement.getText}"
          }
      }
    }

    private[this] object SimpleTypeReferenceReference {

      def unapply(typeElement: ScSimpleTypeElement): Option[ScStableCodeReference] =
        typeElement.reference
    }
  }

  private class SealedClassGenerationStrategy(inheritors: Inheritors) extends PatternGenerationStrategy {

    override def patterns: Seq[PatternComponents] = inheritors.exhaustivePatterns
  }

  private class EnumGenerationStrategy(enumClass: PsiClass,
                                       qualifiedName: String,
                                       membersNames: Seq[String]) extends PatternGenerationStrategy {

    override def patterns: Seq[TypedPatternComponents] = membersNames.map {
      new StablePatternComponents(enumClass, qualifiedName, _)
    }
  }

  private final class ClausesInsertHandler[E <: ScExpression : reflect.ClassTag](strategy: PatternGenerationStrategy,
                                                                                 prefixAndSuffix: PrefixAndSuffix)
                                                                                (implicit place: PsiElement)
    extends ClauseInsertHandler {

    override protected def handleInsert(implicit context: InsertionContext): Unit = {
      val (components, clausesText) = strategy.createClauses(prefixAndSuffix)
      replaceText(clausesText)

      onTargetElement { statement: E =>
        val clauses = findCaseClauses(statement)
        strategy.adjustTypes(components, clauses.caseClauses)

        CodeStyleManager.getInstance(context.getProject).reformatText(
          context.getFile,
          ju.Collections.singleton(statement.getTextRange)
        )
      }

      val whiteSpace = onTargetElement { statement =>
        val clauses = findCaseClauses(statement)
        val caseClauses = clauses.caseClauses
        replaceWhiteSpace(caseClauses.head)(clauses.getNextSibling)
      }

      moveCaret(whiteSpace.getStartOffset + 1)
    }

    private def findCaseClauses(target: E): ScCaseClauses =
      target.findLastChildByType[ScCaseClauses](parser.ScalaElementType.CASE_CLAUSES)

    private def replaceWhiteSpace(clause: ScCaseClause)
                                 (nextSibling: => PsiElement) = {
      val whiteSpace = (clause.getNextSibling match {
        case null => nextSibling
        case sibling => sibling
      }).asInstanceOf[PsiWhiteSpaceImpl]

      whiteSpace.replaceWithText(" " + whiteSpace.getText)
    }
  }

}
