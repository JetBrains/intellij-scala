package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import java.{util => ju}

import com.intellij.codeInsight.completion._
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.functionArrow
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScCaseClauses, ScTypedPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValue
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator._
import org.jetbrains.plugins.scala.lang.psi.types.api.{ExtractClass, FunctionType}
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils

final class ExhaustiveMatchCompletionContributor extends ScalaCompletionContributor {

  import ExhaustiveMatchCompletionContributor._

  extend(
    classOf[ScSugarCallExpr],
    classOf[ScSugarCallExpr]
  )(
    classOf[ScMatch],
    ScalaKeyword.MATCH,
  ) {
    case (sugarCall@ScSugarCallExpr(operand, operation, _), place)
      if !sugarCall.isInstanceOf[ScPrefixExpr] &&
        operation.isAncestorOf(place) => operand.`type`().toOption
    case _ => None
  }

  extend(
    classOf[ScArgumentExprList],
    classOf[ScBlockExpr]
  )(
    classOf[ScBlockExpr],
    ScalaKeyword.CASE,
    Some("", "")
  ) {
    case (ExpectedType(FunctionType(_, Seq(targetType))), _) => Some(targetType)
    case _ => None
  }

  private def extend[T <: ScalaPsiElement with Typeable](captureClass: Class[_ <: ScalaPsiElement], targetClass: Class[T])
                                                        (handlerTargetClass: Class[_ <: ScExpression], lookupString: String,
                                                         prefixAndSuffix: PrefixAndSuffix = None)
                                                        (`type`: (T, PsiElement) => Option[ScType]): Unit = extend(
    CompletionType.BASIC,
    PlatformPatterns.psiElement.inside(captureClass),
    new ClauseCompletionProvider(targetClass) {

      import ClauseCompletionProvider._

      override protected def addCompletions(typeable: T, result: CompletionResultSet)
                                           (implicit place: PsiElement): Unit = for {
        PatternGenerationStrategy(strategy) <- `type`(typeable, place)
      } result.addElement(
        lookupString,
        new ClausesInsertHandler(strategy, handlerTargetClass, prefixAndSuffix)
      )(
        itemTextBold = true,
        tailText = rendererTailText,
        grayed = true
      )
    }
  )
}

object ExhaustiveMatchCompletionContributor {

  private[lang] val Exhaustive = "exhaustive"

  private[lang] def rendererTailText = s" ($Exhaustive)"

  private type PrefixAndSuffix = Option[(String, String)]

  private[lang] sealed trait PatternGenerationStrategy {

    def patterns: Seq[PatternComponents]
  }

  private[lang] object PatternGenerationStrategy {

    implicit class StrategyExt(private val strategy: PatternGenerationStrategy) extends AnyVal {

      def adjustTypes(components: Seq[PatternComponents],
                      caseClauses: Seq[ScCaseClause]): Unit =
        ClauseInsertHandler.adjustTypes(
          strategy.isInstanceOf[SealedClassGenerationStrategy],
          caseClauses.flatMap(_.pattern).zip(components): _*
        ) {
          case ScTypedPattern(typeElement) => typeElement
        }

      def createClauses(prefixAndSuffix: PrefixAndSuffix = None)
                       (implicit place: PsiElement): (Seq[PatternComponents], String) = {
        val (prefix, suffix) = prefixAndSuffix.getOrElse(ScalaKeyword.MATCH + " {\n", "\n}")

        val components = strategy.patterns
        val clausesText = components.map { component =>
          s"${ScalaKeyword.CASE} ${component.text} $functionArrow"
        }.mkString(prefix, "\n", suffix)

        (components, clausesText)
      }
    }

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

    private[this] def collectMembers[C <: PsiClass, In <: PsiMember, Out <: PsiMember](clazz: C)
                                                                                      (predicate: C => Boolean)
                                                                                      (members: C => Seq[In])
                                                                                      (collector: PartialFunction[In, Out]) =
      if (predicate(clazz)) toOption(members(clazz).collect(collector))
      else None

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

  private final class ClausesInsertHandler[E <: ScExpression](strategy: PatternGenerationStrategy, clazz: Class[E],
                                                              prefixAndSuffix: PrefixAndSuffix)
                                                             (implicit place: PsiElement)
    extends ClauseInsertHandler(clazz) {

    override protected def handleInsert(implicit context: InsertionContext): Unit = {
      val (components, clausesText) = strategy.createClauses(prefixAndSuffix)
      replaceText(clausesText)

      onTargetElement { statement =>
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
