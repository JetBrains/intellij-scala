package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.TypeAdjuster.adjustFor
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValue
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createPatternFromTextWithContext
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{DesignatorOwner, ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{ExtractClass, FunctionType}
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils.isAccessible

import scala.reflect.ClassTag

final class ExhaustiveMatchCompletionContributor extends ScalaCompletionContributor {

  import ExhaustiveMatchCompletionContributor._

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
    Target <: ScExpression : ClassTag,
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
          addImports = strategy.isInstanceOf[DirectInheritorsGenerationStrategy],
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

    def unapply(`type`: ScType)
               (implicit place: PsiElement): Option[PatternGenerationStrategy] = {
      val valueType = `type`.extractDesignatorSingleton.getOrElse(`type`)
      val strategy = valueType match {
        case ScProjectionType(DesignatorOwner(enumClass@ScalaEnumeration(values)), _) =>
          val membersNames = for {
            value <- values
            if isAccessible(value, place, forCompletion = true)
            if value.`type`().exists(_.conforms(valueType))

            declaredName <- value.declaredNames
          } yield declaredName

          membersNames match {
            case Seq() => null
            case _ => new EnumGenerationStrategy(enumClass, enumClass.qualifiedName, membersNames)
          }
        case ScDesignatorType(enumClass@JavaEnum(enumConstants)) =>
          enumConstants match {
            case Seq() => null
            case _ =>
              new EnumGenerationStrategy(
                enumClass,
                valueType.presentableText,
                enumConstants.map(_.getName)
              )
          }
        case ExtractClass(DirectInheritors(inheritors)) =>
          new DirectInheritorsGenerationStrategy(inheritors)
        case _ =>
          null
      }

      Option(strategy)
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
      adjustFor(
        for {
          (element, _) <- pairs
          (_, typeElement) <- findTypeElement(element)
        } yield typeElement,
        addImports = addImports,
        useTypeAliases = false
      )

      for {
        (element, components: ClassPatternComponents) <- pairs
        (pattern, ScSimpleTypeElement.unwrapped(codeReference)) <- findTypeElement(element)

        replacement = createPatternFromTextWithContext(
          components.presentablePatternText(Right(codeReference)),
          pattern.getContext,
          pattern
        )
      } pattern.replace(replacement)
    }

    private[this] object ScalaEnumeration {

      private[this] val EnumerationFQN = "scala.Enumeration"

      def unapply(enumClass: ScObject): Option[Seq[ScValue]] =
        if (enumClass.supers.map(_.qualifiedName).contains(EnumerationFQN))
          Some(enumClass.members.filterBy[ScValue])
        else
          None
    }

    private[this] object JavaEnum {

      def unapply(enumClass: PsiClass): Option[Seq[PsiEnumConstant]] =
        if (enumClass.isEnum)
          Some(enumClass.getFields.toSeq.filterBy[PsiEnumConstant])
        else
          None
    }
  }

  private final class DirectInheritorsGenerationStrategy(inheritors: Inheritors) extends PatternGenerationStrategy {

    override def patterns: Seq[PatternComponents] = {
      val Inheritors(namedInheritors, isExhaustive) = inheritors

      namedInheritors.map {
        case scalaObject: ScObject => new StablePatternComponents(scalaObject)
        case CaseClassPatternComponents(components) => components
        case definition => new TypedPatternComponents(definition)
      } ++ (if (isExhaustive) None else Some(WildcardPatternComponents))
    }
  }

  private final class EnumGenerationStrategy(enumClass: PsiClass, qualifiedName: String,
                                             membersNames: Seq[String]) extends PatternGenerationStrategy {

    override def patterns: Seq[StablePatternComponents] = membersNames.map { name =>
      new StablePatternComponents(enumClass, qualifiedName + "." + name)
    }
  }

  private final class ClausesInsertHandler[E <: ScExpression : ClassTag](strategy: PatternGenerationStrategy,
                                                                         prefixAndSuffix: PrefixAndSuffix)
                                                                        (implicit place: PsiElement)
    extends ClauseInsertHandler {

    override protected def handleInsert(implicit context: InsertionContext): Unit = {
      val (components, clausesText) = strategy.createClauses(prefixAndSuffix)
      replaceText(clausesText)

      onTargetElement { statement: E =>
        val caseClauses = statement.findLastChildByType[ScCaseClauses](parser.ScalaElementType.CASE_CLAUSES)

        val clauses = caseClauses.caseClauses
        strategy.adjustTypes(components, clauses)

        reformatAndMoveCaret(caseClauses, clauses.head, statement.getTextRange)
      }
    }
  }

}
