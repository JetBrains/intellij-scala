package org.jetbrains.plugins.scala.worksheet.settings

import com.intellij.openapi.editor.{Editor, LogicalPosition}
import com.intellij.psi.PsiErrorElement
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompilerUtil._
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetSourceProcessor
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetExternalRunType.WorksheetPreprocessError
import org.jetbrains.plugins.scala.worksheet.ui.printers.{WorksheetEditorPrinter, WorksheetEditorPrinterFactory}

abstract sealed class WorksheetExternalRunType {
  def getName: String

  def isReplRunType: Boolean

  @NotNull
  def createPrinter(editor: Editor, file: ScalaFile): WorksheetEditorPrinter

  def process(srcFile: ScalaFile, editor: Editor): Either[WorksheetPreprocessError, WorksheetCompileRunRequest]

  override def toString: String = getName
}

object WorksheetExternalRunType {

  private val PredefinedTypes    = Array(PlainRunType, ReplRunType)
  private val PredefinedTypesMap = PredefinedTypes.map(rt => (rt.getName, rt)).toMap

  def getDefaultRunType: WorksheetExternalRunType = ReplRunType

  def getAllRunTypes: Array[WorksheetExternalRunType] = PredefinedTypes

  def findRunTypeByName(name: String): Option[WorksheetExternalRunType] = PredefinedTypesMap.get(name)

  case class WorksheetPreprocessError(message: String, position: LogicalPosition)

  object WorksheetPreprocessError {

    def apply(errorElement: PsiErrorElement, ifEditor: Option[Editor]): WorksheetPreprocessError = {
      val message = errorElement.getErrorDescription
      val position = ifEditor.map(_.offsetToLogicalPosition(errorElement.getTextOffset)).getOrElse(new LogicalPosition(0, 0))
      new WorksheetPreprocessError(message, position)
    }
  }

  object PlainRunType extends WorksheetExternalRunType {
    override def getName: String = "Plain"

    override def isReplRunType: Boolean = false

    override def createPrinter(editor: Editor, file: ScalaFile): WorksheetEditorPrinter =
      WorksheetEditorPrinterFactory.getDefaultUiFor(editor, file)

    override def process(srcFile: ScalaFile, editor: Editor): Either[WorksheetPreprocessError, WorksheetCompileRunRequest] = {
      val result = WorksheetSourceProcessor.processDefault(srcFile, editor.getDocument)
      result
        .map { case (code, className) => RunCompile(code, className) }
        .left.map { errorElement => WorksheetPreprocessError(errorElement, Some(editor)) }
    }
  }

  object ReplRunType extends WorksheetExternalRunType {
    override def getName: String = "REPL"

    override def isReplRunType: Boolean = true

    override def createPrinter(editor: Editor, file: ScalaFile): WorksheetEditorPrinter =
      WorksheetEditorPrinterFactory.getIncrementalUiFor(editor, file)

    override def process(srcFile: ScalaFile, editor: Editor): Either[WorksheetPreprocessError, WorksheetCompileRunRequest] = {
      val result = WorksheetSourceProcessor.processIncremental(srcFile, editor)
      result
        .map { code => RunRepl(code) }
        .left.map { errorElement => WorksheetPreprocessError(errorElement, Some(editor)) }
    }
  }
}
