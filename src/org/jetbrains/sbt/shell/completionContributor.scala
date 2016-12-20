package org.jetbrains.sbt.shell

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/**
  * Created by jast on 2016-09-28.
  */
class SbtShellCompletionContributor extends CompletionContributor {

  extend(
    CompletionType.BASIC,
    PlatformPatterns.psiElement().withLanguage(SbtShellLanguage),
    SbtShellCompletionProvider)

}

object SbtShellCompletionProvider extends CompletionProvider[CompletionParameters] {
  override def addCompletions(parameters: CompletionParameters,
                              context: ProcessingContext,
                              result: CompletionResultSet): Unit = {

    // TODO just some commonly used builtins for now. later, load all the tasks, settings, commands, projects etc from sbt server and give more complete suggestions based on that!
    val tasks = Seq(
      "compile", "test", "console", "clean", "update", "updateClassifiers", "updateSbtClassifiers",
      "products", "publish", "publishLocal", "consoleProject")

    val settings = Seq("libraryDependencies", "baseDirectory", "sourceDirectory", "unmanagedBase", "target")

    // TODO figure out how to use the parsers for InputKeys and Commands to supply their autocompletion to shell
    val inputs = Seq("run", "runMain", "testOnly")
    val commands = Seq("help", "reload", "plugins", "settings", "project", "projects")

    val all = tasks ++ settings ++ inputs ++ commands

    all.foreach { key =>
      val elem = LookupElementBuilder.create(key)
      result.addElement(elem)
    }

  }

}