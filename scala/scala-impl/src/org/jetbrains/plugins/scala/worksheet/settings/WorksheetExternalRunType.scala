package org.jetbrains.plugins.scala.worksheet.settings

import com.intellij.openapi.editor.{Editor, LogicalPosition}
import com.intellij.psi.PsiErrorElement
import org.jetbrains.annotations.{NonNls, NotNull}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompilerUtil.WorksheetCompileRunRequest
import org.jetbrains.plugins.scala.worksheet.processor.{WorksheetDefaultSourcePreprocessor, WorksheetIncrementalSourcePreprocessor}
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetExternalRunType.WorksheetPreprocessError
import org.jetbrains.plugins.scala.worksheet.ui.printers.{WorksheetEditorPrinter, WorksheetEditorPrinterFactory}

abstract sealed class WorksheetExternalRunType {
  @NonNls def getName: String

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

  final case class WorksheetPreprocessError(message: String, position: LogicalPosition)

  object WorksheetPreprocessError {

    def apply(errorElement: PsiErrorElement, ifEditor: Option[Editor]): WorksheetPreprocessError = {
      val message = errorElement.getErrorDescription
      val position = ifEditor.map(_.offsetToLogicalPosition(errorElement.getTextOffset)).getOrElse(new LogicalPosition(0, 0))
      WorksheetPreprocessError(message, position)
    }
  }

  object PlainRunType extends WorksheetExternalRunType {
    override def getName: String = "Plain"

    override def isReplRunType: Boolean = false

    override def createPrinter(editor: Editor, file: ScalaFile): WorksheetEditorPrinter =
      WorksheetEditorPrinterFactory.getDefaultUiFor(editor, file)

    override def process(srcFile: ScalaFile, editor: Editor): Either[WorksheetPreprocessError, WorksheetCompileRunRequest] = {
      val result = WorksheetDefaultSourcePreprocessor.preprocess(srcFile, editor.getDocument)
      result
        .map { case WorksheetDefaultSourcePreprocessor.PreprocessResult(code, className) =>
          WorksheetCompileRunRequest.RunCompile(code, className)
        }
        .left.map(WorksheetPreprocessError(_, Some(editor)))
    }
  }

  object ReplRunType extends WorksheetExternalRunType {
    override def getName: String = "REPL"

    override def isReplRunType: Boolean = true

    override def createPrinter(editor: Editor, file: ScalaFile): WorksheetEditorPrinter =
      WorksheetEditorPrinterFactory.getIncrementalUiFor(editor, file)

    override def process(srcFile: ScalaFile, editor: Editor): Either[WorksheetPreprocessError, WorksheetCompileRunRequest] = {
      val result = WorksheetIncrementalSourcePreprocessor.preprocess(srcFile, editor)
      result
        .map { case WorksheetIncrementalSourcePreprocessor.PreprocessResult(commandsEncoded, queuedElements) =>
          WorksheetCompileRunRequest.RunRepl(commandsEncoded, queuedElements)
        }
        .left.map(WorksheetPreprocessError(_, Some(editor)))
    }
  }
}
