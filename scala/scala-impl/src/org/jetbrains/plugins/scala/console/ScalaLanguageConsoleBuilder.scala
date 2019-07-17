package org.jetbrains.plugins.scala.console

import java.awt.Color

import com.intellij.execution.console.LanguageConsoleImpl
import com.intellij.execution.filters.TextConsoleBuilderImpl
import com.intellij.openapi.project.Project
import com.intellij.ui.SideBorder

class ScalaLanguageConsoleBuilder(project: Project)
  extends TextConsoleBuilderImpl(project) {

  override def createConsole: LanguageConsoleImpl = {
    val consoleView = new ScalaLanguageConsole(project, ScalaLanguageConsoleView.ScalaConsole)

    ScalaConsoleInfo.setIsConsole(consoleView.getFile, flag = true)

    consoleView.setPrompt(null)
    consoleView
  }
}
