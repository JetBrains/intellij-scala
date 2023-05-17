package org.jetbrains.plugins.scala.worksheet.ui.printers

import com.intellij.diff.tools.util.BaseSyncScrollable
import com.intellij.diff.tools.util.SyncScrollSupport.TwosideSyncScrollSupport
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.{CommonDataKeys, DataProvider}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.{CaretEvent, CaretListener, VisibleAreaEvent, VisibleAreaListener}
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.{Editor, EditorFactory, VisualPosition}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.ui.JBSplitter
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.extensions.{StringExt, invokeLater}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.util.ui.extensions.JComponentExt
import org.jetbrains.plugins.scala.worksheet.WorksheetBundle
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetDiffSplitters.SimpleWorksheetSplitter
import org.jetbrains.plugins.scala.worksheet.ui.{WorksheetDiffSplitters, WorksheetFoldGroup}
import org.jetbrains.plugins.scala.worksheet.utils.FileAttributeUtilCache

import java.awt.{Dimension, Rectangle}
import java.util
import javax.swing.JComponent
import scala.concurrent.duration.{DurationInt, FiniteDuration}

//noinspection TypeAnnotation
object WorksheetEditorPrinterFactory {

  val END_MESSAGE = WorksheetBundle.message("worksheet.printers.output.exceeds.cutoff.limit") + "\n"
  val BULK_COUNT = 15 // TODO: add a setting

  private var _IDLE_TIME: FiniteDuration = 1000.millis
  def IDLE_TIME: FiniteDuration = _IDLE_TIME
  @TestOnly
  def IDLE_TIME_=(value: FiniteDuration): Unit = _IDLE_TIME = value


  val DEFAULT_WORKSHEET_VIEWERS_RATIO = 0.5f
  private val SYNC_SCROLL_SUPPORT_KEY: Key[TwosideSyncScrollSupport] = Key.create("SyncScrollSupport")

  private val LAST_WORKSHEET_RUN_RESULT = new FileAttribute("LastWorksheetRunResult", 2, false)
  private val LAST_WORKSHEET_RUN_RATIO = new FileAttribute("ScalaWorksheetLastRatio", 1, false)

  def synch(
    originalEditor: Editor,
    worksheetViewer: Editor,
    diffSplitter: Option[SimpleWorksheetSplitter] = None,
    foldGroup: Option[WorksheetFoldGroup] = None
  ): Unit = {

    class MyCaretAdapterBase extends CaretListener {
      override def equals(obj: Any): Boolean = obj match {
        case _: MyCaretAdapterBase => true
        case _ => false
      }

      override def hashCode(): Int = 12345
    }

    def createListener(recipient: Editor, editor: Editor): CaretListener = foldGroup match {
      case Some(group) =>
        new CaretListener {
          override def caretPositionChanged(e: CaretEvent): Unit = {
            if (!e.getEditor.asInstanceOf[EditorImpl].getContentComponent.hasFocus) return
            val line = Math.min(group.left2rightOffset(editor.getCaretModel.getVisualPosition.getLine), recipient.getDocument.getLineCount)
            recipient.getCaretModel.moveToVisualPosition(new VisualPosition(line, 0))
          }
        }

      case _ =>
        new CaretListener {
          override def caretPositionChanged(e: CaretEvent): Unit = {
            if (!e.getEditor.asInstanceOf[EditorImpl].getContentComponent.hasFocus) return
            recipient.getCaretModel.moveToVisualPosition(editor.getCaretModel.getVisualPosition)
          }
        }
    }

    def checkAndAdd(don: Editor, recipient: Editor): Unit = {
      val cache = WorksheetCache.getInstance(don.getProject)

      cache.getPatchedFlag(don) match {
        case "50" | null =>
          cache.removePatchedFlag(don)
          don.getCaretModel.removeCaretListener(new MyCaretAdapterBase)
          don.getCaretModel.addCaretListener(createListener(recipient, don))
          cache.setPatchedFlag(don, if (foldGroup.isDefined) "100" else "50")
        case _ =>
      }
    }

    //TODO: do this matching early, maybe even extract separate private method which would accept EditorImpl
    (originalEditor, worksheetViewer) match {
      case (originalImpl: EditorImpl, viewerImpl: EditorImpl) =>
        invokeLater {
          checkAndAdd(originalImpl, viewerImpl)

          val line = Math.min(originalImpl.getCaretModel.getVisualPosition.getLine, viewerImpl.getDocument.getLineCount)
          viewerImpl.getCaretModel.moveToVisualPosition(new VisualPosition(line, 0))

          val syncSupport = new TwosideSyncScrollSupport(
            util.Arrays.asList(originalEditor, worksheetViewer),
            SynchronizingNoopScrollable
          )
          originalEditor.putUserData(SYNC_SCROLL_SUPPORT_KEY, syncSupport)

          diffSplitter.foreach { splitter =>
            val listener: VisibleAreaListener = (e: VisibleAreaEvent) => {
              splitter.redrawDiffs()
              syncSupport.visibleAreaChanged(e)
            }

            originalEditor.getScrollingModel.addVisibleAreaListener(listener)
            worksheetViewer.getScrollingModel.addVisibleAreaListener(listener)
          }
        }
      case _ =>
    }
  }

  private object SynchronizingNoopScrollable extends BaseSyncScrollable {
    override def processHelper(scrollHelper: BaseSyncScrollable.ScrollHelper): Unit = ()
    override def isSyncScrollEnabled: Boolean = true
  }

  /**
   * Forces viewer editor scroll to be synchronized with main editor
   */
  @RequiresEdt
  def synchronizeViewerScrollWithMainEditor(mainEditor: Editor): Unit = {
    val syncScrollSupport = mainEditor.getUserData(SYNC_SCROLL_SUPPORT_KEY)

    //NOTE: SyncScrollSupport.ScrollHelper.syncVerticalScroll is private
    //So the only way to trigger proper synchronization is via visibleAreaChanged
    //We need to invoke it twice with a +-1 y coordinate change because in SyncScrollSupport.ScrollHelper.visibleAreaChanged
    //it's checked that coordinate is actually changed

    val scrollModel = mainEditor.getScrollingModel
    val visibleArea = scrollModel.getVisibleArea
    val visibleAreaIntermediate = new Rectangle(visibleArea)
    visibleAreaIntermediate.y -= 1

    syncScrollSupport.visibleAreaChanged(new VisibleAreaEvent(mainEditor, visibleArea, visibleAreaIntermediate))
    syncScrollSupport.visibleAreaChanged(new VisibleAreaEvent(mainEditor, visibleAreaIntermediate, visibleArea))
  }

  def saveWorksheetEvaluation(file: VirtualFile, result: String, ratio: Float): Unit = {
    FileAttributeUtilCache.writeAttribute(LAST_WORKSHEET_RUN_RESULT, file, result)
    FileAttributeUtilCache.writeAttribute(LAST_WORKSHEET_RUN_RATIO, file, ratio.toString)
  }

  def saveOnlyRatio(file: VirtualFile, ratio: Float): Unit =
    FileAttributeUtilCache.writeAttribute(LAST_WORKSHEET_RUN_RATIO, file, ratio.toString)

  def loadWorksheetEvaluation(file: VirtualFile): Option[(String, Float)] = {
    val ratioAttribute = FileAttributeUtilCache.readAttribute(LAST_WORKSHEET_RUN_RATIO, file)
    val ratio = ratioAttribute.flatMap(_.toFloatOpt).getOrElse(DEFAULT_WORKSHEET_VIEWERS_RATIO)
    FileAttributeUtilCache.readAttribute(LAST_WORKSHEET_RUN_RESULT, file).map(s => (s, ratio))
  }

  def deleteWorksheetEvaluation(file: VirtualFile): Unit = {
    FileAttributeUtilCache.writeAttribute(LAST_WORKSHEET_RUN_RESULT, file, "")
    FileAttributeUtilCache.writeAttribute(LAST_WORKSHEET_RUN_RATIO, file, DEFAULT_WORKSHEET_VIEWERS_RATIO.toString)
  }

  def createViewer(editor: Editor): Editor =
    setupRightSideViewer(editor, getOrCreateViewerEditorFor(editor), modelSync = true)

  def getDefaultUiFor(editor: Editor, scalaFile: ScalaFile): WorksheetEditorPrinter = {
    val printer = newDefaultUiFor(editor, scalaFile)

    // TODO: now we cache it only for unit tests but maybe we should also cache it like in getIncrementalUiFor
    val cache = WorksheetCache.getInstance(editor.getProject)
    cache.addPrinter(editor, printer)

    printer.scheduleWorksheetUpdate()
    printer
  }

  def getIncrementalUiFor(editor: Editor, scalaFile: ScalaFile, showReplErrorsInEditor: Boolean): WorksheetEditorPrinter = {
    val cache = WorksheetCache.getInstance(editor.getProject)

    cache.getPrinter(editor) match {
      case Some(printer: WorksheetEditorPrinterRepl) =>
        printer.scalaFile = scalaFile
        printer.showReplErrorsInEditor = showReplErrorsInEditor
        printer
      case _                                         =>
        val printer = newIncrementalUiFor(editor, scalaFile, showReplErrorsInEditor)
        cache.addPrinter(editor, printer)
        printer.scheduleWorksheetUpdate()
        printer
    }
  }

  private def newDefaultUiFor(editor: Editor, scalaFile: ScalaFile): WorksheetEditorPrinterPlain = {
    val viewerEditor = getOrCreateViewerEditorFor(editor)
    val sideViewer = setupRightSideViewer(editor, viewerEditor)
    new WorksheetEditorPrinterPlain(editor, sideViewer, scalaFile)
  }

  private def newIncrementalUiFor(editor: Editor, scalaFile: ScalaFile, showReplErrorsInEditor: Boolean): WorksheetEditorPrinterRepl = {
    val viewerEditor = getOrCreateViewerEditorFor(editor)
    val sideViewer = setupRightSideViewer(editor, viewerEditor)
    new WorksheetEditorPrinterRepl(
      editor,
      sideViewer,
      scalaFile,
      showReplErrorsInEditor
    )
  }

  private def setupRightSideViewer(editor: Editor, viewer: Editor, modelSync: Boolean = false): Editor = {
    val editorComponent = editor.getComponent
    val editorContentComponent = editor.getContentComponent

    val viewerSettings = viewer.getSettings
    viewerSettings.setLineMarkerAreaShown(false)
    viewerSettings.setLineNumbersShown(false)

    val prop = editorComponent.components.nextOption()
      .collect { case splitter: JBSplitter => splitter.getProportion }
      .getOrElse(DEFAULT_WORKSHEET_VIEWERS_RATIO)

    val dimension = editorComponent.getSize()
    val prefDim = new Dimension(dimension.width / 2, dimension.height)

    editor.getSettings.setFoldingOutlineShown(false)

    viewer.getComponent.setPreferredSize(prefDim)

    if (modelSync) {
      synch(editor, viewer)
    }

    editorContentComponent.setPreferredSize(prefDim)

    if (!ApplicationManager.getApplication.isUnitTestMode) {
      WorksheetDiffSplitters.addSplitterIfNeeded(editor, viewer, prop)
    }

    WorksheetCache.getInstance(editor.getProject).addViewer(viewer, editor)
    viewer
  }

  private def getOrCreateViewerEditorFor(editor: Editor): Editor = {
    val project = editor.getProject
    val viewer = WorksheetCache.getInstance(project).getViewer(editor)
    viewer match {
      case editor: EditorImpl => editor
      case _                  => createBlankEditor(project)
    }
  }

  private def createBlankEditor(project: Project): Editor = {
    val factory: EditorFactory = EditorFactory.getInstance
    val editor: Editor = factory.createViewer(factory.createDocument(""), project)
    editor.setBorder(null)
    editor.getContentComponent.getParent match {
      case jComp: JComponent =>
        val dataProvider: DataProvider = (dataId: String) => {
          if (CommonDataKeys.HOST_EDITOR.is(dataId)) editor
          else null
        }
        DataManager.registerDataProvider(jComp, dataProvider)
      case _ =>
    }
    editor
  }
}
