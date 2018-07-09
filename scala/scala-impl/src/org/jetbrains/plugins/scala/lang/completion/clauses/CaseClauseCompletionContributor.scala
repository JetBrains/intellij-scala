package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import com.intellij.codeInsight.completion._
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.{Consumer, ProcessingContext}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

class CaseClauseCompletionContributor extends ScalaCompletionContributor {

  extend(new ScalaCompletionProvider {

    override protected def completionsFor(position: PsiElement)
                                         (implicit parameters: CompletionParameters,
                                          context: ProcessingContext): Iterable[ScalaLookupItem] = {
      val maybeClass = position.findContextOfType(classOf[ScStableReferenceElementPattern])
        .flatMap(_.expectedType)
        .flatMap(_.extractClass)

      val targetClasses = maybeClass match {
        case Some(SealedDefinition(Inheritors(namedInheritors, _))) => namedInheritors
        case Some(definition: ScTypeDefinition) => Seq(definition)
        case _ => Seq.empty
      }

      // TODO find conflicting CompletionContributor
      for {
        clazz <- targetClasses
        if !clazz.isInstanceOf[ScObject]
        name <- patternTexts(clazz)(position)
      } yield {
        val item = new ScalaLookupItem(clazz, name)
        item.isLocalVariable = true
        item
      }
    }
  })

  extend(new DelegatingCompletionProvider[ScPattern] {

    override def addCompletions(resultSet: CompletionResultSet)
                               (implicit parameters: CompletionParameters,
                                context: ProcessingContext): Unit = {
      positionFromParameters.findContextOfType(classOf[ScReferencePattern]).foreach {
        runBasicCompletionContributor(resultSet)(_, parameters)
      }
    }

    /**
      * Enable completion for object with unapply/unapplySeq methods on case label position.
      * Case label with low letter treat as ScReferencePattern and don't handle with ScalaBasicCompletionContributor,
      * this handler add open and closed brackets to treat element as ScCodeReferenceElement
      * and run ScalaBasicCompletionContributor.
      */
    private def runBasicCompletionContributor(resultSet: CompletionResultSet)
                                             (implicit position: ScReferencePattern,
                                              parameters: CompletionParameters): Unit = {
      val pattern = createElement("".parenthesize(), resultSet)
      val newParameters = createParameters(pattern)
      resultSet.runRemainingContributors(newParameters, createConsumer(resultSet))
    }

    override protected def createElement(text: String,
                                         context: PsiElement,
                                         child: PsiElement): ScPattern =
      ScalaPsiElementFactory.createCaseClauseFromTextWithContext(text, context, child)
        .flatMap(_.pattern).get

    override protected def createConsumer(resultSet: CompletionResultSet)
                                         (implicit position: PsiElement): Consumer[CompletionResult] =
      (result: CompletionResult) => {
        val lookupElement = result.getLookupElement

        val extractorExists = (lookupElement.getPsiElement match {
          case obj: ScObject => obj.members
          case _ => Seq.empty
        }).exists {
          case function: ScFunctionDefinition => function.isUnapplyMethod
          case _ => false
        }

        if (extractorExists) resultSet.consume(lookupElement)
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