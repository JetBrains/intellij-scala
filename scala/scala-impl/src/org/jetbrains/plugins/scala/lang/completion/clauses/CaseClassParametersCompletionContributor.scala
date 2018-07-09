package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementWeigher}
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester

import scala.collection.JavaConverters

/**
  * Created by kate
  * on 1/29/16
  */
class CaseClassParametersCompletionContributor extends ScalaCompletionContributor {

  extend(CompletionType.BASIC,
    PlatformPatterns.psiElement,
    new CompletionProvider[CompletionParameters] {

      def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet): Unit = {
        val position = positionFromParameters(parameters)

        val maybeParametersOwner = position.findContextOfType(classOf[ScConstructorPattern]).collect {
          case ScConstructorPattern(ScReferenceElement(function: ScFunctionDefinition), _) => function
        }.flatMap { function =>
          function.syntheticCaseClass.orElse {
            if (function.isUnapplyMethod) Some(function) else None
          }
        }

        val caseClassParameters = maybeParametersOwner.toSeq
          .flatMap(_.parameters)
        if (caseClassParameters.isEmpty) return

        val myPosition = findPosition(position, caseClassParameters.length)

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

      private def findPosition(position: PsiElement, length: Int): Int = {
        val maybeIndex = for {
          pattern <- position.findContextOfType(classOf[ScPattern])
          list <- position.findContextOfType(classOf[ScPatternArgumentList])
          patterns = list.patterns
          if patterns.length <= length
        } yield patterns.indexOf(pattern)
        maybeIndex.getOrElse(-1)
      }
    }
  )
}