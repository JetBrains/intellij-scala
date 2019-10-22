package org.jetbrains.plugins.scala.worksheet.settings

import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompilerUtil._
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetSourceProcessor
import org.jetbrains.plugins.scala.worksheet.ui.printers.{WorksheetEditorPrinter, WorksheetEditorPrinterFactory}

abstract sealed class WorksheetExternalRunType {
  def getName: String

  def isReplRunType: Boolean

  def createPrinter(editor: Editor, file: ScalaFile): WorksheetEditorPrinter
  
  def process(srcFile: ScalaFile, editor: Editor): WorksheetCompileRunRequest
  
  override def toString: String = getName
}

object WorksheetExternalRunType {

  private val PredefinedTypes    = Array(PlainRunType, ReplRunType)
  private val PredefinedTypesMap = PredefinedTypes.map(rt => (rt.getName, rt)).toMap

  def getDefaultRunType: WorksheetExternalRunType = ReplRunType

  def getAllRunTypes: Array[WorksheetExternalRunType] = PredefinedTypes

  def findRunTypeByName(name: String): Option[WorksheetExternalRunType] = PredefinedTypesMap.get(name)
  
  object PlainRunType extends WorksheetExternalRunType {
    override def getName: String = "Plain"

    override def isReplRunType: Boolean = false

    override def createPrinter(editor: Editor, file: ScalaFile): WorksheetEditorPrinter =
      WorksheetEditorPrinterFactory.getDefaultUiFor(editor, file)

    override def process(srcFile: ScalaFile, editor: Editor): WorksheetCompileRunRequest = {
      val result = WorksheetSourceProcessor.processDefault(srcFile, editor.getDocument)
      result match {
        case Right((code, className)) => RunCompile(code, className)
        case Left(errorElement)       => ErrorWhileCompile(errorElement, Some(editor))
      }
    }
  }

  object ReplRunType extends WorksheetExternalRunType {
    override def getName: String = "REPL"

    override def isReplRunType: Boolean = true

    override def createPrinter(editor: Editor, file: ScalaFile): WorksheetEditorPrinter =
      WorksheetEditorPrinterFactory.getIncrementalUiFor(editor, file)

    override def process(srcFile: ScalaFile, editor: Editor): WorksheetCompileRunRequest = {
      val result = WorksheetSourceProcessor.processIncremental(srcFile, editor)
      result match {
        case Right(code)        => RunRepl(code)
        case Left(errorElement) => ErrorWhileCompile(errorElement, Some(editor))
      }
    }
  }
}
