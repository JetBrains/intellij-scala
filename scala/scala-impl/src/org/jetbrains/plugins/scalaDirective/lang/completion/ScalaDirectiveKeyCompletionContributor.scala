package org.jetbrains.plugins.scalaDirective.lang.completion

import com.intellij.codeInsight.completion.{CompletionParameters, CompletionProvider, CompletionResultSet, CompletionType}
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionContributor
import org.jetbrains.plugins.scalaDirective.dependencies.ScalaDirectiveDependencyKeys
import org.jetbrains.plugins.scalaDirective.lang.completion.lookups.ScalaDirectiveLookupItem

import scala.jdk.CollectionConverters.IterableHasAsJava

final class ScalaDirectiveKeyCompletionContributor extends ScalaCompletionContributor {
  register(ScalaDirectiveDependencyKeys)
  register(ScalaDirectiveScalaKey)

  private def register(keys: String*): Unit = register(keys)

  private def register(keys: Iterable[String]): Unit =
    extend(CompletionType.BASIC, ScalaDirectiveKeyPattern, new CompletionProvider[CompletionParameters] {
      override def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet): Unit =
        result.addAllElements(keys.map(ScalaDirectiveLookupItem(_)).asJava)
    })
}
