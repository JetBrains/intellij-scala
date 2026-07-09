package org.jetbrains.plugins.scalaDirective.lang.completion

import com.intellij.codeInsight.completion.CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED
import com.intellij.codeInsight.completion.{CompletionParameters, CompletionProvider, CompletionResultSet}
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.completion.positionFromParameters
import org.jetbrains.plugins.scala.packagesearch.lang.completion.BaseDependencyCompletionParameters
import org.jetbrains.plugins.scalaDirective.lang.completion.ScalaDirectiveDependencyCompletionProviderBase._
import org.jetbrains.plugins.scalaDirective.util.ScalaDirectiveValueKind

abstract class ScalaDirectiveDependencyCompletionProviderBase extends CompletionProvider[CompletionParameters] {
  protected def addCompletions(params: DependencyCompletionParameters, resultSet: CompletionResultSet): Unit

  override protected def addCompletions(params: CompletionParameters, processingContext: ProcessingContext, resultSet: CompletionResultSet): Unit = {
    resultSet.restartCompletionOnAnyPrefixChange()

    val dependencyParams = new DependencyCompletionParameters(params, resultSet)
    addCompletions(dependencyParams, resultSet)
  }
}

object ScalaDirectiveDependencyCompletionProviderBase {
  final class DependencyCompletionParameters(completionParams: CompletionParameters, resultSet: CompletionResultSet)
    extends BaseDependencyCompletionParameters(completionParams, resultSet, positionFromParameters(completionParams)) {
    val (placeText: String, valueKind: ScalaDirectiveValueKind) = ScalaDirectiveValueKind.extract(place.getText)

    val tokens: Array[String] = placeText.split(':').filterNot(_.isBlank)
    val currentTokenIdx: Int = tokens.indexWhere(_.contains(DUMMY_IDENTIFIER_TRIMMED))

    def currentToken: String = tokens(currentTokenIdx)
  }
}
