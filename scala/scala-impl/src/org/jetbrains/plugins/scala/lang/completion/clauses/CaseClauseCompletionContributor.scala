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
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
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

      val targetInheritors = maybeClass.collect {
        case SealedDefinition(inheritors) => inheritors
        case definition: ScTypeDefinition => Inheritors(Seq(definition))
      }.toSeq

      targetInheritors
        .flatMap(_.patterns(exhaustive = false)(position))
        .map {
          case (name, namedElement) =>
            val item = new ScalaLookupItem(namedElement, name)
            item.isLocalVariable = true
            item
        }
    }
  })

  extend(new CompletionProvider[CompletionParameters] {

    override def addCompletions(parameters: CompletionParameters,
                                context: ProcessingContext,
                                resultSet: CompletionResultSet): Unit = {
      import CaseClauseCompletionContributor._

      val providers = PsiTreeUtil.getContextOfType(positionFromParameters(parameters), classOf[ScBindingPattern]) match {
        case (_: ScReferencePattern) childOf (_: ScNamingPattern | _: ScTypedPattern) => Seq.empty
        case pattern: ScReferencePattern => Seq(AotCompletionProvider, extractorCompletionProvider(pattern))
        case _ => Seq.empty
      }

      providers.foreach(_.addCompletions(parameters, context, resultSet))
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
                                           (implicit position: PsiElement): AotConsumer = new AotConsumer(resultSet) {

        override protected def suggestItemText(lookupString: String): String =
          super.typedItemText(lookupString)
      }

      override protected def createElement(text: String, context: PsiElement, child: PsiElement): ScTypedPattern =
        createPattern(text, context, child).asInstanceOf[ScTypedPattern]
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
      createPattern(text, context, child)

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
  }

  private[this] def createPattern(text: String, context: PsiElement, child: PsiElement): ScPattern =
    ScalaPsiElementFactory.createCaseClauseFromTextWithContext(text, context, child)
      .flatMap(_.pattern).get
}