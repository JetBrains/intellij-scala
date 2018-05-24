package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementWeigher}
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.{Consumer, ProcessingContext}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester

import scala.collection.JavaConverters

/**
  * Created by kate
  * on 1/29/16
  */
class ScalaCaseClassParametersNameContributor extends ScalaCompletionContributor {

  extend(CompletionType.BASIC,
    PlatformPatterns.psiElement,
    new CompletionProvider[CompletionParameters] {

      import PsiTreeUtil.getContextOfType

      def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet): Unit = {
        val position = positionFromParameters(parameters)

        val caseClassParameters: Seq[ScParameter] = getContextOfType(position, classOf[ScConstructorPattern]) match {
          case null => Seq.empty
          case pattern => pattern.ref.resolve() match {
            case function: ScFunctionDefinition =>
              function.syntheticCaseClass match {
                case Some(caseClass) => caseClass.parameters
                case _ if function.isUnapplyMethod => function.getParameterList.params
                case _ => Seq.empty
              }
            case _ => Seq.empty
          }
        }

        if (caseClassParameters.isEmpty) return

        val myPosition = findPosition(position, caseClassParameters.length).getOrElse(-1)

        val byName = caseClassParameters.map { parameter =>
          (parameter, parameter.name)
        }

        val byType = position.getContext match {
          case pattern: ScPattern =>
            for {
              (parameter, expectedType) <- caseClassParameters.lift(myPosition).zip(pattern.expectedType)
              name <- NameSuggester.suggestNamesByType(expectedType)
            } yield (parameter, name)
          case _ => Iterable.empty
        }

        val sorter = CompletionSorter.defaultSorter(parameters, result.getPrefixMatcher)
          .weighAfter("prefix", createWeigher(myPosition, caseClassParameters))

        val elements = createItems(byName ++ byType)
        result.withRelevanceSorter(sorter).addAllElements(elements)
      }

      private def createItems(pairs: Seq[(ScParameter, String)]) = {
        import JavaConverters._
        pairs.map {
          case (parameter, name) =>
            val result = new ScalaLookupItem(parameter, name)
            result.isLocalVariable = true
            result
        }.asJava
      }

      private def createWeigher(position: Int, parameters: Seq[ScParameter]) = new LookupElementWeigher("orderByPosition") {

        override def weigh(item: LookupElement): Comparable[_] = ScalaLookupItem.original(item) match {
          case scalaItem@ScalaLookupItem(parameter: ScParameter) if parameter.name == scalaItem.name /*not equals when name computed by type*/ =>
            position - parameters.indexOf(parameter) match {
              case 0 => -1
              case diff => diff.abs
            }
          case ScalaLookupItem(_) => 0
          case _ => null
        }
      }

      private def findPosition(position: PsiElement, length: Int): Option[Int] =
        for {
          pattern <- Option(getContextOfType(position, classOf[ScPattern]))
          list <- Option(getContextOfType(position, classOf[ScPatternArgumentList]))
          patterns = list.patterns
          if patterns.length <= length
        } yield patterns.indexOf(pattern)
    })

  /**
    * Enable completion for object with unapply/unapplySeq methods on case label position.
    * Case label with low letter treat as ScReferencePattern and don't handle with ScalaBasicCompletionContributor,
    * this handler add open and closed brackets to treat element as ScCodeReferenceElement
    * and run ScalaBasicCompletionContributor.
    */
  extend(CompletionType.BASIC,
    PlatformPatterns.psiElement
      .withParent(classOf[ScReferencePattern])
      .withSuperParent(2, classOf[ScCaseClause]),
    new CompletionProvider[CompletionParameters] {

      override def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet): Unit = {
        val context = positionFromParameters(parameters).getContext

        for {
          caseClause <- ScalaPsiElementFactory.createCaseClauseFromTextWithContext(result.getPrefixMatcher.getPrefix + "()", context.getContext, context)
          pattern <- caseClause.pattern
          element <- pattern.depthFirst().find(_.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER)
          identifier = parameters.withPosition(element, element.getTextRange.getEndOffset)
        } result.runRemainingContributors(identifier, createConsumer(result))
      }

      private def createConsumer(resultSet: CompletionResultSet) = new Consumer[CompletionResult] {

        override def consume(result: CompletionResult): Unit = {
          val lookupElement = result.getLookupElement
          val members = lookupElement.getPsiElement match {
            case obj: ScObject => obj.members
            case _ => Seq.empty
          }

          members.collect {
            case function: ScFunctionDefinition if function.isUnapplyMethod => function
          }.foreach { _ =>
            resultSet.consume(lookupElement)
          }
        }
      }
    })
}
