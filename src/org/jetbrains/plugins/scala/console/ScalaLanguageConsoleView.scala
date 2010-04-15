package org.jetbrains.plugins.scala.console

import com.intellij.execution.console.LanguageConsoleViewImpl
import org.jetbrains.plugins.scala.ScalaFileType
import com.intellij.openapi.project.Project
import com.intellij.execution.process.ProcessHandler

/**
 * User: Alexander Podkhalyuzin
 * Date: 14.04.2010
 */

class ScalaLanguageConsoleView(project: Project) extends
  LanguageConsoleViewImpl(project, "Scala Console", ScalaFileType.SCALA_LANGUAGE) {
  private var myHandler: ProcessHandler = null

  override def attachToProcess(processHandler: ProcessHandler): Unit = {
    myHandler = processHandler
    super.attachToProcess(processHandler)
  }

  def getHandler = myHandler
}