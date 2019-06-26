package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import com.intellij.codeInsight.completion._
import com.intellij.psi.PsiElement
import com.intellij.util.{Consumer, ProcessingContext}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScBlockExpr}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, PartialFunctionType}

final class CaseClauseCompletionContributor extends ScalaCompletionContributor {

  import CaseClauseCompletionContributor._
  import ClauseCompletionProvider._

  import reflect.ClassTag

  extend[ScArgumentExprList](
    new ClauseCompletionProvider[ScBlockExpr] {

      override protected def addCompletions(block: ScBlockExpr, result: CompletionResultSet)
                                           (implicit place: PsiElement): Unit =
        block.expectedType().collect {
          case PartialFunctionType(_, targetType) => targetType
          case FunctionType(_, Seq(targetType)) => targetType
        }.flatMap {
          _.extractClass
        }.collect {
          case SealedDefinition(inheritors) => inheritors
        }.foreach { inheritors =>
          for {
            components <- inheritors.inexhaustivePatterns // TODO objects!!!
            patternText = components.extractorText()
          } result.addElement(
            ScalaKeyword.CASE,
            new CaseClauseInsertHandler(patternText)
          )(
            itemTextBold = true,
            tailText = " " + patternText
          )
        }
    }
  )

  extend[ScCaseClause](
    new ClauseCompletionProvider[ScStableReferencePattern] {

      override protected def addCompletions(pattern: ScStableReferencePattern, result: CompletionResultSet)
                                           (implicit place: PsiElement): Unit = {
        val maybeInheritors = pattern.expectedType
          .flatMap(_.extractClass)
          .collect {
            case SealedDefinition(inheritors) => inheritors
            case definition: ScTypeDefinition => Inheritors(Seq(definition))
          }

        for {
          inheritors <- maybeInheritors.toSeq
          components <- inheritors.inexhaustivePatterns

          lookupString = components.extractorText()
        } result.addElement(
          lookupString,
          new PatternInsertHandler(lookupString, components)
        )(itemTextItalic = true)
      }
    }
  )

  extend[ScCaseClause](
    new CompletionProvider[CompletionParameters] {

      override def addCompletions(parameters: CompletionParameters,
                                  context: ProcessingContext,
                                  resultSet: CompletionResultSet): Unit = for {
        ExtractorCompletionProvider(provider) <- positionFromParameters(parameters).parentOfType(classOf[ScReferencePattern]).toSeq

        provider <- AotCompletionProvider :: provider :: Nil
      } provider.addCompletions(parameters, context, resultSet)
    }
  )

  private def extend[
    Capture <: ScalaPsiElement : ClassTag
  ](provider: CompletionProvider[CompletionParameters]): Unit =
    extend(CompletionType.BASIC, inside[Capture], provider)

}

object CaseClauseCompletionContributor {

  import ExhaustiveMatchCompletionContributor.PatternGenerationStrategy._
  import ScalaPsiElementFactory.createPatternFromTextWithContext

  private object AotCompletionProvider extends aot.CompletionProvider[ScTypedPattern] {

    override protected def findTypeElement(pattern: ScTypedPattern): Option[ScalaPsiElement] =
      pattern.typePattern.map(_.typeElement)

    override protected def createConsumer(resultSet: CompletionResultSet, position: PsiElement): aot.Consumer = new aot.TypedConsumer(resultSet)

    override protected def createElement(text: String, context: PsiElement, child: PsiElement): ScTypedPattern =
      createPatternFromTextWithContext(text, context, child).asInstanceOf[ScTypedPattern]
  }

  private final class CaseClauseInsertHandler(patternText: String)
                                             (implicit place: PsiElement)
    extends ClauseInsertHandler[ScCaseClause] {

    override protected def handleInsert(implicit context: InsertionContext): Unit = {
      replaceText(createClause(patternText) + " ")

      moveCaret(context.getTailOffset)
    }
  }

  private final class PatternInsertHandler(lookupString: String,
                                           components: ExtractorPatternComponents[_])
    extends ClauseInsertHandler[ScConstructorPattern] {

    override def handleInsert(implicit context: InsertionContext): Unit = {
      replaceText(lookupString)

      onTargetElement { pattern =>
        adjustTypes(false, (pattern, components)) {
          case ScParenthesisedPattern(ScTypedPattern(typeElement)) => typeElement
        }
      }
    }
  }

  /**
   * Enable completion for object with unapply/unapplySeq methods on case label position.
   * Case label with low letter treat as ScReferencePattern and don't handle with ScalaBasicCompletionContributor,
   * this handler add open and closed brackets to treat element as ScCodeReferenceElement
   * and run ScalaBasicCompletionContributor.
   */
  private class ExtractorCompletionProvider(pattern: ScPattern) extends DelegatingCompletionProvider[ScPattern] {

    override protected def addCompletions(resultSet: CompletionResultSet,
                                          prefix: String)
                                         (implicit parameters: CompletionParameters,
                                          context: ProcessingContext): Unit = {
      val replacement = createElement("".parenthesize(), prefix, pattern)
      val newParameters = createParameters(replacement)
      resultSet.runRemainingContributors(newParameters, createConsumer(resultSet, pattern))
    }

    override protected def createElement(text: String, context: PsiElement, child: PsiElement): ScPattern =
      createPatternFromTextWithContext(text, context, child)

    override protected def createConsumer(resultSet: CompletionResultSet, position: PsiElement): Consumer[CompletionResult] =
      (result: CompletionResult) => {
        val lookupElement = result.getLookupElement

        val extractorExists = lookupElement.getPsiElement match {
          case Extractor(_) => true
          case _ => false
        }

        if (extractorExists) resultSet.consume(lookupElement)
      }
  }


  private object ExtractorCompletionProvider {

    def unapply(pattern: ScReferencePattern): Option[DelegatingCompletionProvider[ScPattern]] = pattern.getParent match {
      case _: ScNamingPattern | _: ScTypedPattern => None
      case _ => Some(new ExtractorCompletionProvider(pattern))
    }
  }

}