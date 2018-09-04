package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import com.intellij.codeInsight.completion._
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.{Consumer, ProcessingContext}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

class CaseClauseCompletionContributor extends ScalaCompletionContributor {

  import CaseClauseCompletionContributor._

  extend(new ClausesCompletionProvider(classOf[ScStableReferenceElementPattern]) {

    import ClausesCompletionProvider._

    override protected def addCompletions(pattern: ScStableReferenceElementPattern,
                                          position: PsiElement,
                                          result: CompletionResultSet): Unit = {
      val maybeInheritors = pattern.expectedType
        .flatMap(_.extractClass)
        .collect {
          case SealedDefinition(inheritors) => inheritors
          case definition: ScTypeDefinition => Inheritors(Seq(definition))
        }

      for {
        inheritors <- maybeInheritors.toSeq
        components <- inheritors.inexhaustivePatterns(position)

        lookupString = components.defaultExtractorText()
        lookupElement = createLookupElement(lookupString)(itemTextItalic = true) {
          createInsertHandler(components)
        }
      } result.addElement(lookupElement)
    }
  })

  extend(new CompletionProvider[CompletionParameters] {

    override def addCompletions(parameters: CompletionParameters,
                                context: ProcessingContext,
                                resultSet: CompletionResultSet): Unit = for {
      ExtractorCompletionProvider(provider) <- positionFromParameters(parameters).findContextOfType(classOf[ScReferencePattern]).toSeq

      provider <- AotCompletionProvider :: provider :: Nil
    } provider.addCompletions(parameters, context, resultSet)
  })

  private def extend(provider: CompletionProvider[CompletionParameters]): Unit = {
    extend(
      CompletionType.BASIC,
      PlatformPatterns.psiElement.inside(classOf[ScCaseClause]),
      provider
    )
  }

}

object CaseClauseCompletionContributor {

  import ScalaPsiElementFactory.createPatternFromTextWithContext

  private object AotCompletionProvider extends aot.CompletionProvider[ScTypedPattern] {

    override protected def findTypeElement(pattern: ScTypedPattern): Option[ScalaPsiElement] =
      pattern.typePattern.map(_.typeElement)

    override protected def createConsumer(resultSet: CompletionResultSet, position: PsiElement): aot.Consumer = new aot.TypedConsumer(resultSet)

    override protected def createElement(text: String, context: PsiElement, child: PsiElement): ScTypedPattern =
      createPatternFromTextWithContext(text, context, child).asInstanceOf[ScTypedPattern]
  }

  private def createInsertHandler(components: ExtractorPatternComponents[_]) = new ClausesInsertHandler(classOf[ScParenthesisedPattern]) {

    import ClausesInsertHandler._

    override def handleInsert(implicit insertionContext: InsertionContext): Unit = {
      replaceTextPhase(components.text.parenthesize())

      onTargetElement { pattern =>
        adjustTypesPhase(false, (pattern, components)) {
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