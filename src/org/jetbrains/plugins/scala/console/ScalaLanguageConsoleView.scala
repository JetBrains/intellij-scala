package org.jetbrains.plugins.scala.console

import com.intellij.execution.console.ConsoleRootType

/**
 * @author Alexander Podkhalyuzin
 * @since 14.04.2010
 */
object ScalaLanguageConsoleView {
  val SCALA_CONSOLE = "Scala Console"
  val SCALA_CONSOLE_ROOT_TYPE = new ConsoleRootType("scala", SCALA_CONSOLE) {}
}
