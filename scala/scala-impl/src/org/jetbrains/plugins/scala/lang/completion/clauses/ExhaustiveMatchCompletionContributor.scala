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
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.functionArrow
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScCaseClauses, ScTypedPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValue
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator._
import org.jetbrains.plugins.scala.lang.psi.types.api.{ExtractClass, FunctionType}
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils

final class ExhaustiveMatchCompletionContributor extends ScalaCompletionContributor {

  import ClauseCompletionProvider._
  import CompletionType.BASIC
  import ExhaustiveMatchCompletionContributor._
  import PlatformPatterns.psiElement
  import ScalaKeyword._

  extend(
    BASIC,
    psiElement.inside(classOf[ScSugarCallExpr]),
    new ClauseCompletionProvider(classOf[ScSugarCallExpr]) {

      override protected def addCompletions(sugarCall: ScSugarCallExpr,
                                            position: PsiElement,
                                            result: CompletionResultSet): Unit =
        for {
          ScSugarCallExpr(operand, operation, _) <- Some(sugarCall)
          if !sugarCall.isInstanceOf[ScPrefixExpr] && operation.isAncestorOf(position)

          PatternGenerationStrategy(strategy) <- operand.`type`().toOption
          handler = new ClausesInsertHandler(
            strategy,
            classOf[ScMatch],
            prefix = DefaultPrefix,
            suffix = DefaultSuffix
          )(sugarCall) {

            override protected def findCaseClauses(target: ScMatch): ScCaseClauses =
              target.caseClauses
          }
        } result.addElement(MATCH, handler)(tailText = rendererTailText)
    }
  )
  extend(
    BASIC,
    psiElement.inside(classOf[ScArgumentExprList]),
    new ClauseCompletionProvider(classOf[ScBlockExpr]) {

      override protected def addCompletions(block: ScBlockExpr,
                                            position: PsiElement,
                                            result: CompletionResultSet): Unit =
        for {
          FunctionType(_, Seq(PatternGenerationStrategy(strategy))) <- block.expectedType()
          handler = new ClausesInsertHandler(
            strategy,
            classOf[ScBlockExpr],
            prefix = "",
            suffix = ""
          )(block) {

            override protected def findCaseClauses(target: ScBlockExpr): ScCaseClauses =
              target.findLastChildByType[ScCaseClauses](ScalaElementType.CASE_CLAUSES)
          }
        } result.addElement(CASE, handler)(tailText = rendererTailText)
    }
  )
}

object ExhaustiveMatchCompletionContributor {

  import ScalaKeyword._

  private val DefaultPrefix = s"$MATCH {\n"
  private val DefaultSuffix = "\n}"
  private[lang] val Exhaustive = "exhaustive"

  private[lang] def rendererTailText = s" ($Exhaustive)"

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

      def createClauses(prefix: String = DefaultPrefix,
                        suffix: String = DefaultSuffix)
                       (implicit place: PsiElement): (Seq[PatternComponents], String) = {
        val components = strategy.patterns
        val clausesText = components.map { component =>
          s"$CASE ${component.text} $functionArrow"
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

  private abstract class ClausesInsertHandler[E <: ScExpression](strategy: PatternGenerationStrategy, clazz: Class[E],
                                                                 prefix: String, suffix: String)
                                                                (implicit place: PsiElement)
    extends ClauseInsertHandler(clazz) {

    override protected def handleInsert(implicit insertionContext: InsertionContext): Unit = {
      val (components, clausesText) = strategy.createClauses(prefix, suffix)
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

    protected def findCaseClauses(target: E): ScCaseClauses

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
