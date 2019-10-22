package org.jetbrains.plugins.scala.worksheet.settings

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompilerUtil._
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetSourceProcessor
import org.jetbrains.plugins.scala.worksheet.ui._
import org.jetbrains.plugins.scala.worksheet.ui.printers.{WorksheetEditorPrinter, WorksheetEditorPrinterFactory}

abstract class WorksheetExternalRunType {
  def getName: String

  def isReplRunType: Boolean

  def createPrinter(editor: Editor, file: ScalaFile): WorksheetEditorPrinter

  def showAdditionalSettingsPanel(): PsiFile => Unit = null
  
  def process(srcFile: ScalaFile, editor: Editor): WorksheetCompileRunRequest = {
    val code = WorksheetSourceProcessor.processSimple(srcFile, editor)
    RunSimple(code)
  }

  def onSettingsConfirmed(file: PsiFile): Unit = {}
  
  override def toString: String = getName
}

object RunTypes {

  private val PREDEFINED_TYPES = Array(PlainRunType, ReplRunType)
  private val PREDEFINED_TYPES_MAP = PREDEFINED_TYPES.map(rt => (rt.getName, rt)).toMap

  val EP_NAME: ExtensionPointName[WorksheetExternalRunType] =
    ExtensionPointName.create[WorksheetExternalRunType]("org.intellij.scala.worksheetExternalRunType")

  private def findEPRunTypes(): Array[WorksheetExternalRunType] = EP_NAME.getExtensions

  def findRunTypeByName(name: String): Option[WorksheetExternalRunType] =
    PREDEFINED_TYPES_MAP.get(name).orElse(findEPRunTypes().find(_.getName == name))

  def getDefaultRunType: WorksheetExternalRunType = ReplRunType
  
  def getAllRunTypes: Array[WorksheetExternalRunType] = PREDEFINED_TYPES ++ findEPRunTypes()
  
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
