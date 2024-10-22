package org.jetbrains.sbt.shell

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.NonNls

final class SbtShellCompletionContributor extends CompletionContributor with DumbAware {
  extend(
    CompletionType.BASIC,
    PlatformPatterns.psiElement().withLanguage(SbtShellLanguage),
    SbtShellCompletionProvider)
}

object SbtShellCompletionProvider extends CompletionProvider[CompletionParameters] {

  // TODO just some commonly used builtins for now. later, load all the tasks, settings, commands, projects etc from sbt server and give more complete suggestions based on that!
  @NonNls private val tasks = Seq(
    "compile", "test", "console", "clean", "update", "updateClassifiers", "updateSbtClassifiers",
    "products", "publish", "publishLocal", "consoleProject")

  @NonNls private val settings = Seq("libraryDependencies", "baseDirectory", "sourceDirectory", "unmanagedBase", "target")

  // TODO figure out how to use the parsers for InputKeys and Commands to supply their autocompletion to shell
  @NonNls private val inputs = Seq("run", "runMain", "testOnly")
  @NonNls private val commands = Seq("help", "reload", "plugins", "settings", "project", "projects")

  private val all = tasks ++ settings ++ inputs ++ commands

  override def addCompletions(parameters: CompletionParameters,
                              context: ProcessingContext,
                              result: CompletionResultSet): Unit =
    all.foreach { key =>
      val elem = LookupElementBuilder.create(key)
      result.addElement(elem)
    }
}
