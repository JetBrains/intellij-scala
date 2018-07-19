package org.jetbrains.plugins.scala.worksheet.settings

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.cell.{CellDescriptor, RunCellAction}
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompilerUtil._
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetSourceProcessor
import org.jetbrains.plugins.scala.worksheet.ui._

/**
  * User: Dmitry.Naydanov
  * Date: 13.07.18.
  */
abstract class WorksheetExternalRunType {
  def getName: String

  def isReplRunType: Boolean

  def isUsesCell: Boolean

  def createPrinter(editor: Editor, file: ScalaFile): WorksheetEditorPrinter

  def process(srcFile: ScalaFile, ifEditor: Option[Editor]): WorksheetCompileRunRequest = RunSimple(
    WorksheetSourceProcessor.processSimple(srcFile, ifEditor)
  )

  def createRunCellAction(cellDescriptor: CellDescriptor): Option[AnAction] = None

  override def toString: String = getName
}

object RunTypes {
  private val PREDEFINED_TYPES = Array(PlainRunType, ReplRunType, ReplCellRunType)
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

    override def isUsesCell: Boolean = false

    override def createPrinter(editor: Editor, file: ScalaFile): WorksheetEditorPrinter =
      WorksheetEditorPrinterFactory.getDefaultUiFor(editor, file)

    override def process(srcFile: ScalaFile, ifEditor: Option[Editor]): WorksheetCompileRunRequest =
      WorksheetSourceProcessor.processDefault(srcFile, ifEditor.map(_.getDocument)) match {
        case Left((code, className)) => RunCompile(code, className)
        case Right(errorElement) => ErrorWhileCompile(errorElement, ifEditor)
      }
  }

  object ReplRunType extends WorksheetExternalRunType {
    override def getName: String = "REPL"

    override def isReplRunType: Boolean = true

    override def isUsesCell: Boolean = false

    override def createPrinter(editor: Editor, file: ScalaFile): WorksheetEditorPrinter =
      WorksheetEditorPrinterFactory.getIncrementalUiFor(editor, file)

    override def process(srcFile: ScalaFile, ifEditor: Option[Editor]): WorksheetCompileRunRequest =
      WorksheetSourceProcessor.processIncremental(srcFile, ifEditor) match {
        case Left((code, _)) => RunRepl(code)
        case Right(errorElement) => ErrorWhileCompile(errorElement, ifEditor)
      }
  }

  object ReplCellRunType extends WorksheetExternalRunType {
    override def getName: String = "REPL with Cells"

    override def isReplRunType: Boolean = true

    override def isUsesCell: Boolean = true

    override def createPrinter(editor: Editor, file: ScalaFile): WorksheetEditorPrinter =
      WorksheetEditorPrinterFactory.getConsoleUiFor(editor, file)

    override def createRunCellAction(cellDescriptor: CellDescriptor): Option[AnAction] = Option(new RunCellAction(cellDescriptor))
  }
}
