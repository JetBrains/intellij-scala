package org.jetbrains.plugins.scala.console

import com.intellij.execution.console.LanguageConsoleImpl
import org.jetbrains.plugins.scala.ScalaFileType
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
 * User: Alefas
 * Date: 18.10.11
 */

class ScalaLanguageConsole(project: Project, title: String)
  extends LanguageConsoleImpl(project, title, ScalaFileType.SCALA_LANGUAGE) {
  private val textBuffer = new StringBuilder
  private var scalaFile = ScalaPsiElementFactory.createScalaFileFromText("1", project)
  myFile.asInstanceOf[ScalaFile].setContext(scalaFile, scalaFile.getLastChild)

  private[console] def textSent(text: String) {
    textBuffer.append(text)
    scalaFile = ScalaPsiElementFactory.createScalaFileFromText(textBuffer.toString() + ";\n1", project)
    myFile.asInstanceOf[ScalaFile].setContext(scalaFile, scalaFile.getLastChild)
  }
}