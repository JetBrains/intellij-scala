package org.jetbrains.plugins.scala.console

import com.intellij.execution.console.ConsoleRootType

object ScalaLanguageConsoleView {
  val ScalaConsole = "Scala REPL"

  object ScalaConsoleRootType extends ConsoleRootType("scala", ScalaConsole)
}
