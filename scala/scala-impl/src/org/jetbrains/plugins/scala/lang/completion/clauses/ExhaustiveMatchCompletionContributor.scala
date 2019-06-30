package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.psi._
import com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValue
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types._
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
    case (ExpectedType(api.FunctionType(_, Seq(targetType))), _) => Some(targetType)
    case _ => None
  }

  private def extend[
    Target <: ScalaPsiElement with result.Typeable : ClassTag,
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
        ) {
          case (_, presentation: LookupElementPresentation) =>
            presentation.setItemText(keywordLookupString)
            presentation.setItemTextBold(true)

            presentation.setTailText(" ", true)
            presentation.appendTailText(rendererTailText, true)
        }
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
          .map(_.canonicalClauseText)
          .mkString(prefix, "\n", suffix)

        (components, clausesText)
      }
    }

    import api.designator._

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
        case api.ExtractClass(SealedDefinition(inheritors)) if inheritors.namedInheritors.nonEmpty =>
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
      psi.TypeAdjuster.adjustFor(
        for {
          (element, _) <- pairs
          (_, typeElement) <- findTypeElement(element)
        } yield typeElement,
        addImports = addImports,
        useTypeAliases = false
      )

      for {
        (element, components: ClassPatternComponents[_]) <- pairs
        (pattern, ScSimpleTypeElement.unwrapped(codeReference)) <- findTypeElement(element)

        replacement = ScalaPsiElementFactory.createPatternFromTextWithContext(
          components.presentablePatternText(Right(codeReference)),
          pattern.getContext,
          pattern
        )
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
  }

  private final class SealedClassGenerationStrategy(inheritors: Inheritors) extends PatternGenerationStrategy {

    override def patterns: Seq[PatternComponents] = {
      val Inheritors(namedInheritors, isInstantiatiable) = inheritors

      namedInheritors.map {
        case scalaObject: ScObject => new StablePatternComponents(scalaObject)
        case SyntheticExtractorPatternComponents(components) => components
        case definition => new TypedPatternComponents(definition)
      } ++ (if (isInstantiatiable) Some(WildcardPatternComponents) else None)
    }
  }

  private final class EnumGenerationStrategy(enumClass: PsiClass, qualifiedName: String,
                                             membersNames: Seq[String]) extends PatternGenerationStrategy {

    override def patterns: Seq[StablePatternComponents] = membersNames.map { name =>
      new StablePatternComponents(enumClass, qualifiedName + "." + name)
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

        reformatClauses(clauses)
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
