package org.jetbrains.plugins.scala.console

import com.intellij.execution.console.LanguageConsoleImpl
import org.jetbrains.plugins.scala.ScalaFileType
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import collection.mutable.HashMap
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTrait, ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScFunction, ScVariable, ScValue}
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil

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