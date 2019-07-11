package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.ScType

final class ExhaustiveMatchCompletionContributor extends ScalaCompletionContributor {

  import CompletionType.BASIC
  import ExhaustiveMatchCompletionContributor._

  extend(
    BASIC,
    inside[ScSugarCallExpr],
    new ExhaustiveClauseCompletionProvider[ScSugarCallExpr](ScalaKeyword.MATCH) {

      override protected def targetType(call: ScSugarCallExpr)
                                       (implicit place: PsiElement): Option[ScType] = call match {
        case _: ScPrefixExpr => None
        case ScSugarCallExpr(operand, operation, _) if operation.isAncestorOf(place) => operand.`type`().toOption
        case _ => None
      }

      override protected def createInsertHandler(strategy: PatternGenerationStrategy)
                                                (implicit place: PsiElement) =
        new ExhaustiveClauseInsertHandler[ScMatch](strategy, None, None)
    }
  )

  extend(
    BASIC,
    inside[ScArgumentExprList],
    new ExhaustiveClauseCompletionProvider[ScBlockExpr](ScalaKeyword.CASE) {

      override protected def targetType(block: ScBlockExpr)
                                       (implicit place: PsiElement): Option[ScType] =
        expectedFunctionalType(block)

      override protected def createInsertHandler(strategy: PatternGenerationStrategy)
                                                (implicit place: PsiElement) =
        new ExhaustiveClauseInsertHandler[ScBlockExpr](strategy, Some(""), Some(""))
    }
  )
}

object ExhaustiveMatchCompletionContributor {

  private[lang] val Exhaustive = "exhaustive"

  private[lang] def rendererTailText = "(" + Exhaustive + ")"

  private abstract class ExhaustiveClauseCompletionProvider[
    E <: ScExpression : reflect.ClassTag,
  ](keywordLookupString: String) extends ClauseCompletionProvider[E] {

    override final protected def addCompletions(expression: E, result: CompletionResultSet)
                                               (implicit place: PsiElement): Unit = for {
      PatternGenerationStrategy(strategy) <- targetType(expression)

      lookupElement = buildLookupElement(
        keywordLookupString,
        createInsertHandler(strategy)
      ) {
        case (_, presentation: LookupElementPresentation) =>
          presentation.setItemText(keywordLookupString)
          presentation.setItemTextBold(true)

          presentation.setTailText(" ", true)
          presentation.appendTailText(rendererTailText, true)
      }
    } result.addElement(lookupElement)

    protected def targetType(expression: E)
                            (implicit place: PsiElement): Option[ScType]

    protected def createInsertHandler(strategy: PatternGenerationStrategy)
                                     (implicit place: PsiElement): ExhaustiveClauseInsertHandler[_]
  }

  private final class ExhaustiveClauseInsertHandler[
    E <: ScExpression : reflect.ClassTag
  ](strategy: PatternGenerationStrategy,
    prefix: Option[String],
    suffix: Option[String])
   (implicit place: PsiElement) extends ClauseInsertHandler[E] {

    override protected def handleInsert(implicit context: InsertionContext): Unit = {
      val (components, clausesText) = strategy.createClauses(prefix, suffix)
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
