package org.jetbrains.plugins.scala.console

import com.intellij.execution.console.ConsoleRootType
import org.jetbrains.plugins.scala.ScalaBundle

object ScalaLanguageConsoleView {
  val ScalaConsole: String = ScalaBundle.message("scala.console.config.display.name")

  object ScalaConsoleRootType extends ConsoleRootType("scala", ScalaConsole)
}
