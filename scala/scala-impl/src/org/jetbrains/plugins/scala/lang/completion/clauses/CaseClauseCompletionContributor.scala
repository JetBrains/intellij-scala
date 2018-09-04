package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import com.intellij.codeInsight.completion._
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.{Consumer, ProcessingContext}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.aot.ScalaAotCompletionContributor
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createPatternFromTextWithContext

class CaseClauseCompletionContributor extends ScalaCompletionContributor {

  import CaseClauseCompletionContributor._

  extend(new ClausesCompletionProvider(classOf[ScStableReferenceElementPattern]) {

    import ClausesCompletionProvider._

    override protected def addCompletions(pattern: ScStableReferenceElementPattern,
                                          position: PsiElement,
                                          result: CompletionResultSet): Unit = {
      val maybeInheritors = pattern
        .expectedType
        .flatMap(_.extractClass)
        .collect {
          case SealedDefinition(inheritors) => inheritors
          case definition: ScTypeDefinition => Inheritors(Seq(definition))
        }

      maybeInheritors match {
        case Some(inheritors) =>
          for {
            components <- inheritors.inexhaustivePatterns(position)

            lookupString = components.defaultExtractorText()
            lookupElement = createLookupElement(lookupString)(itemTextItalic = true) {
              createInsertHandler(components)
            }
          } result.addElement(lookupElement)
        case _ =>
      }
    }
  })

  extend(new CompletionProvider[CompletionParameters] {

    override def addCompletions(parameters: CompletionParameters,
                                context: ProcessingContext,
                                resultSet: CompletionResultSet): Unit = {
      val providers = PsiTreeUtil.getContextOfType(positionFromParameters(parameters), classOf[ScBindingPattern]) match {
        case (_: ScReferencePattern) childOf (_: ScNamingPattern | _: ScTypedPattern) => Seq.empty
        case pattern: ScReferencePattern => Seq(AotCompletionProvider, extractorCompletionProvider(pattern))
        case _ => Seq.empty
      }

      providers.foreach {
        _.addCompletions(parameters, context, resultSet)
      }
    }
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

  private val AotCompletionProvider = {
    import ScalaAotCompletionContributor._
    new AotCompletionProvider[ScTypedPattern] {

      override protected def findTypeElement(pattern: ScTypedPattern): Option[ScalaPsiElement] =
        pattern.typePattern.map(_.typeElement)

      override protected def createConsumer(resultSet: CompletionResultSet)
                                           (implicit position: PsiElement): AotConsumer = new TypedAotConsumer(resultSet)

      override protected def createElement(text: String, context: PsiElement, child: PsiElement): ScTypedPattern =
        createPatternFromTextWithContext(text, context, child).asInstanceOf[ScTypedPattern]
    }
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

  private def extractorCompletionProvider(pattern: ScReferencePattern) = new DelegatingCompletionProvider[ScPattern] {

    private implicit def element: PsiElement = pattern

    /**
      * Enable completion for object with unapply/unapplySeq methods on case label position.
      * Case label with low letter treat as ScReferencePattern and don't handle with ScalaBasicCompletionContributor,
      * this handler add open and closed brackets to treat element as ScCodeReferenceElement
      * and run ScalaBasicCompletionContributor.
      */
    override protected def addCompletions(resultSet: CompletionResultSet,
                                          prefix: String)
                                         (implicit parameters: CompletionParameters,
                                          context: ProcessingContext): Unit = {
      val pattern = createElement("".parenthesize(), prefix)
      val newParameters = createParameters(pattern)
      resultSet.runRemainingContributors(newParameters, createConsumer(resultSet))
    }

    override protected def createElement(text: String, context: PsiElement, child: PsiElement): ScPattern =
      createPatternFromTextWithContext(text, context, child)

    override protected def createConsumer(resultSet: CompletionResultSet)
                                         (implicit position: PsiElement): Consumer[CompletionResult] =
      (result: CompletionResult) => {
        val lookupElement = result.getLookupElement

        val extractorExists = lookupElement.getPsiElement match {
          case Extractor(_) => true
          case _ => false
        }

        if (extractorExists) resultSet.consume(lookupElement)
      }
  }
}