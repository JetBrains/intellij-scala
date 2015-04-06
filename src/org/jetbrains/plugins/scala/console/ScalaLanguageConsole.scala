package org.jetbrains.plugins.scala.console

import com.intellij.execution.console.{ConsoleHistoryController, LanguageConsoleImpl}
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

import scala.collection.mutable.HashMap

/**
 * User: Alefas
 * Date: 18.10.11
 */

class ScalaLanguageConsole(project: Project, title: String)
  extends LanguageConsoleImpl(project, title, ScalaFileType.SCALA_LANGUAGE) {
  private val textBuffer = new StringBuilder
  private var scalaFile = ScalaPsiElementFactory.createScalaFileFromText("1", project)
  myFile.asInstanceOf[ScalaFile].setContext(scalaFile, scalaFile.getLastChild)
  def getHistory = textBuffer.toString()

  override def attachToProcess(processHandler: ProcessHandler): Unit = {
    super.attachToProcess(processHandler)
    val controller = new ConsoleHistoryController(ScalaLanguageConsoleView.SCALA_CONSOLE_ROOT_TYPE, null, this)
    controller.install()

    ScalaConsoleInfo.addConsole(this, controller, processHandler)
  }

  private[console] def textSent(text: String) {
    textBuffer.append(text)
    scalaFile = ScalaPsiElementFactory.createScalaFileFromText(textBuffer.toString() + ";\n1", project)
    val types = new HashMap[String, TextRange]
    val values = new HashMap[String, (TextRange, Boolean)]
    def addValue(name: String, range: TextRange, replaceWithPlaceholder: Boolean) {
      values.get(name) match {
        case Some((oldRange, r)) =>
          val newText = if (r) "_" + StringUtil.repeatSymbol(' ', oldRange.getLength - 1) else StringUtil.repeatSymbol(' ', oldRange.getLength)
          textBuffer.replace(oldRange.getStartOffset, oldRange.getEndOffset, newText)
        case None =>
      }
      values.put(name, ((range, replaceWithPlaceholder)))
    }
    def addType(name: String, range: TextRange) {
      types.get(name) match {
        case Some(oldRange) =>
          val newText = StringUtil.repeatSymbol(' ', oldRange.getLength)
          textBuffer.replace(oldRange.getStartOffset, oldRange.getEndOffset, newText)
        case None =>
      }
      types.put(name, range)
    }
    scalaFile.getChildren.foreach {
      case v: ScValue => v.declaredElements.foreach(td => addValue(td.name, td.nameId.getTextRange, true))
      case v: ScVariable => v.declaredElements.foreach(td => addValue(td.name, td.nameId.getTextRange, true))
      case f: ScFunction => addValue(f.name, f.getTextRange, false)
      case o: ScObject => addValue(o.name, o.getTextRange, false)
      case c: ScClass =>  addType(c.name, c.nameId.getTextRange)
      case c: ScTrait =>  addType(c.name, c.nameId.getTextRange)
      case t: ScTypeAlias => addType(t.name, t.nameId.getTextRange)
      case _ => //do nothing
    }
    scalaFile = ScalaPsiElementFactory.createScalaFileFromText(textBuffer.toString() + ";\n1", project)
    myFile.asInstanceOf[ScalaFile].setContext(scalaFile, scalaFile.getLastChild)
  }
}