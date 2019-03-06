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
        handler = new ClausesInsertHandler(strategy, handlerTargetClass, prefixAndSuffix)
      } result.addElement(lookupString, handler)(tailText = rendererTailText)
    }
  )
}

object ExhaustiveMatchCompletionContributor {

  private[lang] val Exhaustive = "exhaustive"

  private[lang] def rendererTailText = s" ($Exhaustive)"

  private type PrefixAndSuffix = Option[(String, String)]

  private[lang] sealed trait PatternGenerationStrategy {

    def patterns(implicit place: PsiElement): Seq[PatternComponents]
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
          ScalaKeyword.CASE + " " + component.text + " " + functionArrow
        }.mkString(prefix, "\n", suffix)

        (components, clausesText)
      }
    }

    def unapply(`type`: ScType): Option[PatternGenerationStrategy] =
      `type`.extractDesignatorSingleton.orElse {
        Some(`type`)
      }.collect {
        case valueType@ScProjectionType(DesignatorOwner(enumeration@ScalaEnumeration()), _) =>
          new ScalaEnumGenerationStrategy(valueType, enumeration)
        case valueType@ScDesignatorType(enum: PsiClass) if enum.isEnum =>
          new JavaEnumGenerationStrategy(valueType, enum)
        case ExtractClass(SealedDefinition(inheritors)) if inheritors.namedInheritors.nonEmpty =>
          new SealedClassGenerationStrategy(inheritors)
      }

    private[this] object ScalaEnumeration {

      private[this] val EnumerationFQN = "scala.Enumeration"

      def unapply(scalaObject: ScObject): Boolean =
        scalaObject.supers
          .map(_.qualifiedName)
          .contains(EnumerationFQN)
    }

  }

  private class SealedClassGenerationStrategy(inheritors: Inheritors) extends PatternGenerationStrategy {

    override def patterns(implicit place: PsiElement): Seq[PatternComponents] =
      inheritors.exhaustivePatterns
  }

  private class JavaEnumGenerationStrategy(valueType: ScType, enum: PsiClass) extends PatternGenerationStrategy {

    override def patterns(implicit place: PsiElement): Seq[TypedPatternComponents] = {
      val qualifiedName = valueType.presentableText

      enum.getFields.collect {
        case constant: PsiEnumConstant => new StablePatternComponents(enum, qualifiedName, constant.getName)
      }
    }
  }

  private class ScalaEnumGenerationStrategy(valueType: ScType,
                                            enumeration: ScObject) extends PatternGenerationStrategy {

    override def patterns(implicit place: PsiElement): Seq[TypedPatternComponents] = {
      val qualifiedName = enumeration.qualifiedName

      enumeration.members.flatMap {
        case value: ScValue
          if ResolveUtils.isAccessible(value, place, forCompletion = true) &&
            isEnumerationValue(value) => value.declaredNames
        case _ => Seq.empty
      }.map {
        new StablePatternComponents(enumeration, qualifiedName, _)
      }
    }

    private def isEnumerationValue(value: ScValue) =
      value.`type`().exists(_.conforms(valueType))
  }

  private class ClausesInsertHandler[E <: ScExpression](strategy: PatternGenerationStrategy, clazz: Class[E],
                                                        prefixAndSuffix: PrefixAndSuffix)
                                                       (implicit place: PsiElement)
    extends ClauseInsertHandler(clazz) {

    override protected def handleInsert(implicit insertionContext: InsertionContext): Unit = {
      val (components, clausesText) = strategy.createClauses(prefixAndSuffix)
      ClauseInsertHandler.replaceText(clausesText)

      val InsertionContextExt(editor, document, file, project) = insertionContext
      onTargetElement { statement =>
        val clauses = findCaseClauses(statement)
        strategy.adjustTypes(components, clauses.caseClauses)
        CodeStyleManager.getInstance(project).reformatText(file, ju.Collections.singleton(statement.getTextRange))
      }

      val whiteSpace = onTargetElement { statement =>
        val clauses = findCaseClauses(statement)
        val caseClauses = clauses.caseClauses
        replaceWhiteSpace(caseClauses.head)(clauses.getNextSibling)
      }

      val offset = whiteSpace.getStartOffset + 1
      PsiDocumentManager.getInstance(project)
        .doPostponedOperationsAndUnblockDocument(document)
      editor.getCaretModel.moveToOffset(offset)
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
