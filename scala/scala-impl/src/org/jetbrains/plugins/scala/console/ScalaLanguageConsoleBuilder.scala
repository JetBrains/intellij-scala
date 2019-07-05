package org.jetbrains.plugins.scala.console

import com.intellij.execution.console.LanguageConsoleImpl
import com.intellij.openapi.project.Project

object ScalaLanguageConsoleBuilder {
  def createConsole(project: Project): LanguageConsoleImpl = {
    val consoleView = new ScalaLanguageConsole(project, ScalaLanguageConsoleView.ScalaConsole)

    ScalaConsoleInfo.setIsConsole(consoleView.getFile, flag = true)

    consoleView.setPrompt(null)
    consoleView
  }
}
