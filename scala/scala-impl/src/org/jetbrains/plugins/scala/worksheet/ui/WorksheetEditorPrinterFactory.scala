package org.jetbrains.plugins.scala.worksheet.ui

import java.awt.event.AdjustmentEvent
import java.awt.{BorderLayout, Dimension}

import javax.swing.{JComponent, JLayeredPane}
import com.intellij.ide.DataManager
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.{CommonDataKeys, DataProvider}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diff.impl.EditingSides
import com.intellij.openapi.diff.impl.util.SyncScrollSupport
import com.intellij.openapi.editor.event.{CaretEvent, CaretListener}
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.{Editor, EditorFactory, VisualPosition}
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.psi.{PsiDocumentManager, PsiFileFactory}
import com.intellij.ui.JBSplitter
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.processor.FileAttributeUtilCache
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetDiffSplitters.SimpleWorksheetSplitter
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaLanguage}

import scala.util.Random

/**
  * User: Dmitry.Naydanov
  * Date: 03.02.17.
  */
object WorksheetEditorPrinterFactory {
  val END_MESSAGE = "Output exceeds cutoff limit.\n"
  val BULK_COUNT = 15
  val IDLE_TIME_MLS = 1000
  val DEFAULT_WORKSHEET_VIEWERS_RATIO = 0.5f

  val DIFF_SPLITTER_KEY: Key[SimpleWorksheetSplitter] = Key.create[WorksheetDiffSplitters.SimpleWorksheetSplitter]("SimpleWorksheetViewerSplitter")
  val DIFF_SYNC_SUPPORT: Key[SyncScrollSupport] = Key.create[SyncScrollSupport]("WorksheetSyncScrollSupport")

  private val LAST_WORKSHEET_RUN_RESULT = new FileAttribute("LastWorksheetRunResult", 2, false)
  private val LAST_WORKSHEET_RUN_RATIO = new FileAttribute("ScalaWorksheetLastRatio", 1, false)

  def synch(originalEditor: Editor, worksheetViewer: Editor,
            diffSplitter: Option[WorksheetDiffSplitters.SimpleWorksheetSplitter] = None,
            foldGroup: Option[WorksheetFoldGroup] = None) {
    class MyCaretAdapterBase extends CaretListener {
      override def equals(obj: Any): Boolean = obj match {
        case _: MyCaretAdapterBase => true
        case _ => false
      }

      override def hashCode(): Int = 12345
    }

    def createListener(recipient: Editor, don: Editor): CaretListener = foldGroup map {
      group =>
        new CaretListener {
          override def caretPositionChanged(e: CaretEvent) {
            if (!e.getEditor.asInstanceOf[EditorImpl].getContentComponent.hasFocus) return
            recipient.getCaretModel.moveToVisualPosition(
              new VisualPosition(Math.min(group left2rightOffset don.getCaretModel.getVisualPosition.getLine, recipient.getDocument.getLineCount), 0))
          }
        }
    } getOrElse new CaretListener {
      override def caretPositionChanged(e: CaretEvent) {
        if (!e.getEditor.asInstanceOf[EditorImpl].getContentComponent.hasFocus) return
        recipient.getCaretModel.moveToVisualPosition(don.getCaretModel.getVisualPosition)
      }
    }

    def checkAndAdd(don: Editor, recipient: Editor) {
      val cache = WorksheetCache.getInstance(don.getProject)

      cache getPatchedFlag don match {
        case "50" | null =>
          cache removePatchedFlag don
          don.getCaretModel.removeCaretListener(new MyCaretAdapterBase)
          don.getCaretModel.addCaretListener(createListener(recipient, don))
          cache.setPatchedFlag(don, if (foldGroup.isDefined) "100" else "50")
        case _ =>
      }
    }


    (originalEditor, worksheetViewer) match {
      case (originalImpl: EditorImpl, viewerImpl: EditorImpl) =>
        ApplicationManager.getApplication invokeLater (() => {
          checkAndAdd(originalImpl, viewerImpl)

          viewerImpl.getCaretModel.moveToVisualPosition(
            new VisualPosition(Math.min(originalImpl.getCaretModel.getVisualPosition.line, viewerImpl.getDocument.getLineCount), 0)
          )

          val syncSupport = new SyncScrollSupport
          syncSupport.install(Array[EditingSides](new WorksheetDiffSplitters.WorksheetEditingSides(originalEditor, worksheetViewer, foldGroup)))

          originalEditor.putUserData(DIFF_SYNC_SUPPORT, syncSupport)

          diffSplitter foreach {
            splitter =>
              viewerImpl.getScrollPane.getVerticalScrollBar.addAdjustmentListener((_: AdjustmentEvent) => splitter.redrawDiffs())
          }
        })
      case _ =>
    }
  }

  def saveWorksheetEvaluation(file: ScalaFile, result: String, ratio: Float = DEFAULT_WORKSHEET_VIEWERS_RATIO) {
    FileAttributeUtilCache.writeAttribute(LAST_WORKSHEET_RUN_RESULT, file, result)
    FileAttributeUtilCache.writeAttribute(LAST_WORKSHEET_RUN_RATIO, file, ratio.toString)
  }

  def saveOnlyRatio(file: ScalaFile, ratio: Float = DEFAULT_WORKSHEET_VIEWERS_RATIO) {
    FileAttributeUtilCache.writeAttribute(LAST_WORKSHEET_RUN_RATIO, file, ratio.toString)
  }

  def loadWorksheetEvaluation(file: ScalaFile): Option[(String, Float)] = {
    val ratio = FileAttributeUtilCache.readAttribute(LAST_WORKSHEET_RUN_RATIO, file) map {
      rr =>
        try {
          java.lang.Float.parseFloat(rr)
        } catch {
          case _: NumberFormatException => DEFAULT_WORKSHEET_VIEWERS_RATIO
        }
    } getOrElse DEFAULT_WORKSHEET_VIEWERS_RATIO

    FileAttributeUtilCache.readAttribute(LAST_WORKSHEET_RUN_RESULT, file).map(s => (s, ratio))
  }

  def deleteWorksheetEvaluation(file: ScalaFile) {
    FileAttributeUtilCache.writeAttribute(LAST_WORKSHEET_RUN_RESULT, file, "")
    FileAttributeUtilCache.writeAttribute(LAST_WORKSHEET_RUN_RATIO, file, DEFAULT_WORKSHEET_VIEWERS_RATIO.toString)
  }

  def createViewer(editor: Editor, virtualFile: VirtualFile): Editor =
    setupRightSideViewer(editor, virtualFile, getOrCreateViewerEditorFor(editor, isPlain = true), modelSync = true)

  //kinda legacy code
  def getMacrosheetUiFor(editor: Editor, scalaFile: ScalaFile): WorksheetDefaultEditorPrinter =
    newDefaultUiFor(editor, scalaFile, isPlain = false)

  def getDefaultUiFor(editor: Editor, scalaFile: ScalaFile): WorksheetEditorPrinter = {
    val printer = newDefaultUiFor(editor, scalaFile, isPlain = true)
    printer.scheduleWorksheetUpdate()
    printer
  }

  def getIncrementalUiFor(editor: Editor, scalaFile: ScalaFile): WorksheetEditorPrinter =
    providePrinter(
      editor, scalaFile,
      _.isInstanceOf[WorksheetIncrementalEditorPrinter],
      (printer: WorksheetEditorPrinter) => {
        printer.asInstanceOf[WorksheetIncrementalEditorPrinter].updateScalaFile(scalaFile)
        printer
      },
      (editor: Editor, file: ScalaFile) => newIncrementalUiFor(editor, file)
    )

  def getConsoleUiFor(editor: Editor, scalaFile: ScalaFile): WorksheetEditorPrinter =
    providePrinter(
      editor: Editor, scalaFile: ScalaFile,
      _.isInstanceOf[WorksheetConsoleEditorPrinter],
      (printer: WorksheetEditorPrinter) => printer,
      (editor: Editor, file: ScalaFile) => newConsoleUiFor(editor, file)
    )

  private def providePrinter(editor: Editor, scalaFile: ScalaFile, condition: WorksheetEditorPrinter => Boolean,
                             update: WorksheetEditorPrinter => WorksheetEditorPrinter,
                             create: (Editor, ScalaFile) => WorksheetEditorPrinter): WorksheetEditorPrinter = {
    val cache = WorksheetCache.getInstance(editor.getProject)

    cache getPrinter editor match {
      case Some(cachedPrinter: WorksheetEditorPrinter) if condition(cachedPrinter) =>
        update(cachedPrinter)
      case _ =>
        val printer = create(editor, scalaFile)
        cache.addPrinter(editor, printer)
        printer.scheduleWorksheetUpdate()
        printer
    }
  }

  private def newDefaultUiFor(editor: Editor, scalaFile: ScalaFile, isPlain: Boolean) =
    new WorksheetDefaultEditorPrinter(
      editor,
      setupRightSideViewer(editor, scalaFile.getVirtualFile, getOrCreateViewerEditorFor(editor, isPlain)),
      scalaFile
    )

  private def newIncrementalUiFor(editor: Editor, scalaFile: ScalaFile): WorksheetIncrementalEditorPrinter =
    new WorksheetIncrementalEditorPrinter(
      editor,
      setupRightSideViewer(editor, scalaFile.getVirtualFile, getOrCreateViewerEditorFor(editor, isPlain = true)),
      scalaFile
    )

  private def newConsoleUiFor(editor: Editor, scalaFile: ScalaFile): WorksheetConsoleEditorPrinter =
    new WorksheetConsoleEditorPrinter(editor, scalaFile)

  private def setupRightSideViewer(editor: Editor, virtualFile: VirtualFile, rightSideEditor: Editor, modelSync: Boolean = false): Editor = {
    val editorComponent = editor.getComponent
    val editorContentComponent = editor.getContentComponent

    val worksheetViewer = rightSideEditor.asInstanceOf[EditorImpl]

    val prop = if (editorComponent.getComponentCount > 0) editorComponent.getComponent(0) match {
      case splitter: JBSplitter => splitter.getProportion
      case _ if worksheetViewer.getUserData(DIFF_SPLITTER_KEY) != null =>
        worksheetViewer.getUserData(DIFF_SPLITTER_KEY).getProportion
      case _ => DEFAULT_WORKSHEET_VIEWERS_RATIO
    } else DEFAULT_WORKSHEET_VIEWERS_RATIO

    val dimension = editorComponent.getSize()
    val prefDim = new Dimension(dimension.width / 2, dimension.height)

    editor.getSettings setFoldingOutlineShown false

    worksheetViewer.getComponent setPreferredSize prefDim

    if (modelSync) synch(editor, worksheetViewer)
    editorContentComponent.setPreferredSize(prefDim)

    if (!ApplicationManager.getApplication.isUnitTestMode) {
      val child = editorComponent.getParent
      val parent = child.getParent

      val diffPane = WorksheetDiffSplitters.createSimpleSplitter(editor, worksheetViewer, List.empty, List.empty, prop)

      worksheetViewer.putUserData(DIFF_SPLITTER_KEY, diffPane)

      @inline def preserveFocus(body: => Unit) {
        val hadFocus = editorContentComponent.hasFocus

        body

        if (hadFocus) editorContentComponent.requestFocusInWindow()
      }

      @inline def patchEditor(): Unit = preserveFocus {
        (parent, child) match {
          case (parentPane: JLayeredPane, _) =>
            parentPane remove child
            parentPane.add(diffPane, BorderLayout.CENTER)
          case (_, childPane: JLayeredPane) =>
            childPane remove editorComponent
            childPane.add(diffPane, BorderLayout.CENTER)
          case _ =>
        }
      }

      if (parent.getComponentCount > 1) parent.getComponent(1) match {
        case _: Splitter =>
          preserveFocus {
            parent.remove(1)
            parent.add(diffPane, 1)
          }
        case _ => patchEditor()
      } else patchEditor()
    }

    WorksheetCache.getInstance(editor.getProject).addViewer(worksheetViewer, editor)
    worksheetViewer
  }

  private def getOrCreateViewerEditorFor(editor: Editor, isPlain: Boolean) = {
    WorksheetCache.getInstance(editor.getProject) getViewer editor match {
      case editorImpl: EditorImpl => editorImpl
      case _ => if (isPlain) createBlankEditor(editor.getProject) else
        createBlankEditorWithLang(editor.getProject, ScalaLanguage.INSTANCE, ScalaFileType.INSTANCE)
    }
  }

  private def createBlankEditor(project: Project): Editor = {
    val factory: EditorFactory = EditorFactory.getInstance
    val editor: Editor = factory.createViewer(factory createDocument "", project)
    editor setBorder null
    editor.getContentComponent.getParent match {
      case jComp: JComponent =>
        jComp.putClientProperty(
          DataManager.CLIENT_PROPERTY_DATA_PROVIDER, new DataProvider {
            override def getData(dataId: String): Editor = if (CommonDataKeys.HOST_EDITOR.is(dataId)) editor else null
          })
      case _ =>
    }
    editor
  }

  private def createBlankEditorWithLang(project: Project, lang: Language, fileType: LanguageFileType): Editor = {
    val file = PsiFileFactory.getInstance(project).createFileFromText("dummy_" + Random.nextString(10), lang, "")
    val editor = EditorFactory.getInstance.createViewer(PsiDocumentManager.getInstance(project).getDocument(file), project)
    val editorHighlighter = EditorHighlighterFactory.getInstance.createEditorHighlighter(project, fileType)

    editor.asInstanceOf[EditorEx].setHighlighter(editorHighlighter)
    editor setBorder null
    editor
  }
}
