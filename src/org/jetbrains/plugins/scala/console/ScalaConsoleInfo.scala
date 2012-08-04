package org.jetbrains.plugins.scala.console

import collection.mutable.ArrayBuffer
import com.intellij.execution.process.{ConsoleHistoryModel, ProcessHandler}

/**
 * @author Ksenia.Sautina
 * @since 7/27/12
 */

object ScalaConsoleInfo {
  private var console: ScalaLanguageConsole = null
  private var processHandler: ProcessHandler = null
  private var model: ConsoleHistoryModel = null
  private val allConsoles = new ArrayBuffer[ScalaLanguageConsole]()

  def getConsole: ScalaLanguageConsole = console
  def getModel = model
  def getProcessHandler = processHandler

  def addConsole(console: ScalaLanguageConsole) {
    this.console = console
    allConsoles.+=(console)
  }

  def addProcessHandler(processHandler: ProcessHandler) {
    this.processHandler = processHandler
  }

  def addModel(model: ConsoleHistoryModel) {
    this.model = model
  }

  def deleteConsole(console: ScalaLanguageConsole) {
    allConsoles.-=(console)
  }

  def dispose() {
    console = null
    allConsoles.clear()
  }
}
