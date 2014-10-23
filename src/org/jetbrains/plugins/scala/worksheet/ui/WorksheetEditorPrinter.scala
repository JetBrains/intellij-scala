package org.jetbrains.plugins.scala
package worksheet.ui

import java.awt.event.{ActionEvent, ActionListener, AdjustmentEvent, AdjustmentListener}
import java.awt.{BorderLayout, Color, Dimension}
import java.util
import javax.swing.{JLayeredPane, Timer}

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diff.impl.EditingSides
import com.intellij.openapi.diff.impl.util.SyncScrollSupport
import com.intellij.openapi.editor._
import com.intellij.openapi.editor.event.{CaretAdapter, CaretEvent}
import com.intellij.openapi.editor.impl.{EditorImpl, FoldingModelImpl}
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiManager, PsiWhiteSpace}
import com.intellij.ui.JBSplitter
import org.jetbrains.plugins.scala
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.worksheet.processor.{FileAttributeUtilCache, WorksheetSourceProcessor}
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetViewerInfo

import _root_.scala.collection.mutable.ArrayBuffer

/**
 * User: Dmitry Naydanov
 * Date: 1/20/14
 */
class WorksheetEditorPrinter(originalEditor: Editor, worksheetViewer: Editor, file: ScalaFile) {
  private val project = originalEditor.getProject
  private val originalDocument = originalEditor.getDocument
  private val viewerDocument = worksheetViewer.getDocument
  private val timer = new Timer(WorksheetEditorPrinter.IDLE_TIME_MLS, TimerListener)
  
  private val outputBuffer = new StringBuilder
  private val foldingOffsets = ArrayBuffer.apply[(Int, Int, Int, Int)]()
  private var linesCount = 0
  private var totalCount = 0
  private var insertedToOriginal = 0
  private var prefix = ""
  @volatile private var terminated = false
  
  private var inited = false
  private var cutoffPrinted = false
  
  private val viewerFolding = worksheetViewer.getFoldingModel.asInstanceOf[FoldingModelImpl]
  private lazy val group =
    new WorksheetFoldGroup(getViewerEditor, originalEditor, project, worksheetViewer.getUserData(WorksheetEditorPrinter.DIFF_SPLITTER_KEY))

  @volatile private var buffed = 0

  originalEditor.asInstanceOf[EditorImpl].setScrollToCaret(false)
  worksheetViewer.asInstanceOf[EditorImpl].setScrollToCaret(false)
  
  def getViewerEditor = worksheetViewer
  
  def getOriginalEditor = originalEditor
  
  def scheduleWorksheetUpdate() {
    timer.start()
  }
  
  def processLine(line: String): Boolean = {
    if (line.stripSuffix("\n") == WorksheetSourceProcessor.END_OUTPUT_MARKER) {
      flushBuffer()
      
      terminated = true
      return true
    }

    if (!isInsideOutput && line.trim.length == 0) {
      outputBuffer append line
      totalCount += 1
    } else if (isResultEnd(line)) {
      WorksheetSourceProcessor extractLineInfoFrom line match {
        case Some((start, end)) =>
          if (!inited) {
            val first = init()
            val diffBetweenFirst = first map {
              case i => Math.min(i, start)
            } getOrElse start


            if (diffBetweenFirst > 0) prefix = StringUtil.repeat("\n", diffBetweenFirst)
          }

          val differ = end - start + 1 - linesCount
          
          if (differ > 0) {
            outputBuffer append getNewLines(differ)
          } else if (0 > differ) {
            insertedToOriginal -= differ
            
            foldingOffsets += (
              (start + insertedToOriginal + differ,
               outputBuffer.length - outputBuffer.reverseIterator.takeWhile(_ == '\n').length,
               end - start + 1, end)
            )
          }

          buffed += linesCount
          if (buffed > WorksheetEditorPrinter.BULK_COUNT) midFlush()
          clear()
        case _ =>
      }
      
    } else if (!cutoffPrinted) {
      linesCount += 1
      totalCount += 1
      
      if (linesCount > getOutputLimit) {
        outputBuffer append WorksheetEditorPrinter.END_MESSAGE
        cutoffPrinted = true
      } else outputBuffer append line
    }
    
    false
  }
  
  private def init(): Option[Int] = {
    inited = true

    val oldSync = originalEditor getUserData WorksheetEditorPrinter.DIFF_SYNC_SUPPORT
    if (oldSync != null) oldSync.dispose()

    WorksheetEditorPrinter.synch(originalEditor, worksheetViewer,
      Option(worksheetViewer.getUserData(WorksheetEditorPrinter.DIFF_SPLITTER_KEY)), Some(group))

    extensions.invokeLater {
      viewerFolding runBatchFoldingOperation new Runnable {
        override def run() {
          viewerFolding.clearFoldRegions()
        }
      }
      getViewerEditor.getCaretModel.moveToVisualPosition(new VisualPosition(0, 0))
    }

    if (file != null) {
      @inline def checkFlag(psi: PsiElement) =
        psi != null && psi.getCopyableUserData(WorksheetSourceProcessor.WORKSHEET_PRE_CLASS_KEY) != null

      var s = file.getFirstChild
      var f = checkFlag(s)

      while (s.isInstanceOf[PsiWhiteSpace] || f) {
        s = s.getNextSibling
        f = checkFlag(s)
      }

      if (s != null) Some(s.getTextRange.getStartOffset) else None
    } else None
  }
  
  private def isResultEnd(line: String) = line startsWith WorksheetSourceProcessor.END_TOKEN_MARKER
  
  private def getNewLines(count: Int) = StringUtil.repeatSymbol('\n', count)
  
  private def clear() {
    linesCount = 0
    cutoffPrinted = false
  }
  
  def flushBuffer() {
    if (!inited) init()
    if (terminated) return
    val str = getCurrentText
    
    if (timer.isRunning) timer.stop()
    
    updateWithPersistentScroll(viewerDocument, str)
    
    outputBuffer.clear()
    prefix = ""

    extensions.invokeLater {
      getViewerEditor.getMarkupModel.removeAllHighlighters()
    }

    scala.extensions.inReadAction {
      PsiDocumentManager.getInstance(project).getPsiFile(originalEditor.getDocument) match {
        case scalaFile: ScalaFile =>
          WorksheetEditorPrinter.saveWorksheetEvaluation(scalaFile, str)
        case _ =>
      }
    }
//    flushFolding()
  }
  
  def midFlush() {
    if (terminated || buffed == 0) return
        
    val str = getCurrentText
    buffed = 0

    updateWithPersistentScroll(viewerDocument, str)
//    flushFolding()
//    incUpdate(str)
  }
  
  def getCurrentText = prefix + outputBuffer.toString()

  def internalError(errorMessage: String) {
    extensions.invokeLater {
      extensions.inWriteAction {
        simpleUpdate("Internal error: " + errorMessage, viewerDocument)
      }
    }
    terminated = true
  }
  
  private def updateWithPersistentScroll(document: Document, text: String) {//todo - to do
    val foldingOffsetsCopy = foldingOffsets.clone()
    foldingOffsets.clear()
    val ed = getViewerEditor


    extensions.invokeLater {
      extensions.inWriteAction {
        val scroll = originalEditor.getScrollingModel.getVerticalScrollOffset
        val worksheetScroll = worksheetViewer.getScrollingModel.getVerticalScrollOffset

        simpleUpdate(text, document)

        originalEditor.getScrollingModel.scrollVertically(scroll)
        worksheetViewer.getScrollingModel.scrollHorizontally(worksheetScroll)

        CommandProcessor.getInstance().executeCommand(project, new Runnable {
          override def run() {
            viewerFolding runBatchFoldingOperation(new Runnable {
              override def run() {
                foldingOffsetsCopy map {
                  case (start, end, limit, originalEnd) =>
                    val offset = originalDocument getLineEndOffset Math.min(originalEnd, originalDocument.getLineCount)
                    val linesCount = viewerDocument.getLineNumber(end) - start - limit + 1

                    new WorksheetFoldRegionDelegate(
                      ed, viewerDocument.getLineStartOffset(start + limit - 1), end,
                      offset, linesCount, group, limit
                    )
                } foreach {
                  case region =>
                    viewerFolding addFoldRegion region
                }

                WorksheetFoldGroup.save(file, group)
              }
            }, false)
          }
        }, null, null)
      }
    }
  }

  private def commitDocument(doc: Document) {
    PsiDocumentManager getInstance project commitDocument doc
  }
  
  private def isInsideOutput = linesCount != 0 
  
  private def getOutputLimit = ScalaProjectSettings.getInstance(project).getOutputLimit

  private def simpleUpdate(text: String, document: Document) {
    document setText text
    commitDocument(document)
  }

  private def incUpdate(text: String, document: Document) {//todo
    val linesOld = viewerDocument.getLineCount
    val total = totalCount

    if (total >= linesOld || text.length >= viewerDocument.getTextLength) {
      document setText text
      commitDocument(document)
    } else {
      CommandProcessor.getInstance().executeCommand(project, new Runnable {
        override def run() {
          if (linesOld != viewerDocument.getLineCount) return
          viewerDocument.deleteString(0, text.length)
          viewerDocument.insertString(0, text)

          for (i <- total until viewerDocument.getLineCount) getViewerEditor.getMarkupModel.addLineHighlighter(i, 0,
            new TextAttributes(Color.gray, null, null, null, 0))
          commitDocument(viewerDocument)
        }
      }, null, null)
    }
  }

  object TimerListener extends ActionListener {
    override def actionPerformed(e: ActionEvent): Unit = midFlush()
  }
}

object WorksheetEditorPrinter {
  val END_MESSAGE = "Output exceeds cutoff limit.\n"
  val BULK_COUNT = 15
  val IDLE_TIME_MLS = 1000

  val DIFF_SPLITTER_KEY = Key.create[WorksheetDiffSplitters.SimpleWorksheetSplitter]("SimpleWorksheetViewerSplitter")
  val DIFF_SYNC_SUPPORT = Key.create[SyncScrollSupport]("WorksheetSyncScrollSupport")

  private val LAST_WORKSHEET_RUN_RESULT = new FileAttribute("LastWorksheetRunResult", 2, false)

  private val patched = new util.WeakHashMap[Editor, String]()

  def getPatched = patched

  private def synch(originalEditor: Editor, worksheetViewer: Editor,
                    diffSplitter: Option[WorksheetDiffSplitters.SimpleWorksheetSplitter] = None,
                    foldGroup: Option[WorksheetFoldGroup] = None) {
    class MyCaretAdapterBase extends CaretAdapter {
      override def equals(obj: Any): Boolean = obj match {
        case _: MyCaretAdapterBase => true
        case _ => false
      }

      override def hashCode(): Int = 12345
    }

    def createListener(recipient: Editor, don: Editor) = foldGroup map {
      case group => new CaretAdapter {
        override def caretPositionChanged(e: CaretEvent) {
          if (!e.getEditor.asInstanceOf[EditorImpl].getContentComponent.hasFocus) return
          recipient.getCaretModel.moveToVisualPosition(
            new VisualPosition(Math.min(group left2rightOffset don.getCaretModel.getVisualPosition.getLine, recipient.getDocument.getLineCount), 0))
        }
      }
    } getOrElse new CaretAdapter {
        override def caretPositionChanged(e: CaretEvent) {
          if (!e.getEditor.asInstanceOf[EditorImpl].getContentComponent.hasFocus) return
          recipient.getCaretModel.moveToVisualPosition(don.getCaretModel.getVisualPosition)
        }
    }

    def checkAndAdd(don: Editor, recipient: Editor) {
      patched get don match {
        case "50" | null =>
          patched remove don
          don.getCaretModel.removeCaretListener(new MyCaretAdapterBase)
          don.getCaretModel.addCaretListener(createListener(recipient, don))
          patched.put(don, if (foldGroup.isDefined) "100" else "50")
        case _ =>
      }
    }


    (originalEditor, worksheetViewer) match {
      case (originalImpl: EditorImpl, viewerImpl: EditorImpl) =>
        ApplicationManager.getApplication invokeLater new Runnable {
          override def run() {
            checkAndAdd(originalImpl, viewerImpl)
//            checkAndAdd(viewerImpl, originalImpl)

            viewerImpl.getCaretModel.moveToVisualPosition(
              new VisualPosition(Math.min(originalImpl.getCaretModel.getVisualPosition.line, viewerImpl.getDocument.getLineCount), 0)
            )

            val syncSupport = new SyncScrollSupport
            syncSupport.install(Array[EditingSides](new WorksheetDiffSplitters.WorksheetEditingSides(originalEditor, worksheetViewer)))

            originalEditor.putUserData(DIFF_SYNC_SUPPORT, syncSupport)

            diffSplitter map {
              case splitter =>
                viewerImpl.getScrollPane.getVerticalScrollBar.addAdjustmentListener(new AdjustmentListener {
                  override def adjustmentValueChanged(e: AdjustmentEvent): Unit = splitter.redrawDiffs()
                })
            }
          }
        }
      case _ =>
    }
  }

  def saveWorksheetEvaluation(file: ScalaFile, result: String) {
    FileAttributeUtilCache.writeAttribute(LAST_WORKSHEET_RUN_RESULT, file, result)
  }
  
  def loadWorksheetEvaluation(file: ScalaFile): Option[String] = FileAttributeUtilCache.readAttribute(LAST_WORKSHEET_RUN_RESULT, file)
  
  def deleteWorksheetEvaluation(file: ScalaFile) {
    FileAttributeUtilCache.writeAttribute(LAST_WORKSHEET_RUN_RESULT, file, "")
  }

  def newWorksheetUiFor(editor: Editor, virtualFile: VirtualFile) =
    new WorksheetEditorPrinter(editor,  createWorksheetViewer(editor, virtualFile),
      PsiManager getInstance editor.getProject findFile virtualFile match {
        case scalaFile: ScalaFile => scalaFile
        case _ => null
      }
    )

  def createWorksheetViewer(editor: Editor, virtualFile: VirtualFile, modelSync: Boolean = false): Editor = {
    val editorComponent = editor.getComponent
    val project = editor.getProject

    val worksheetViewer = WorksheetViewerInfo getViewer editor match {
      case editorImpl: EditorImpl => editorImpl
      case _ => createBlankEditor(project).asInstanceOf[EditorImpl]
    }

    val prop = if (editorComponent.getComponentCount > 0) editorComponent.getComponent(0) match {
      case splitter: JBSplitter => splitter.getProportion
      case _ if worksheetViewer.getUserData(DIFF_SPLITTER_KEY) != null =>
        worksheetViewer.getUserData(DIFF_SPLITTER_KEY).getProportion
      case _ => 0.5f
    } else 0.5f

    val dimension = editorComponent.getSize()
    val prefDim = new Dimension(dimension.width / 2, dimension.height)

    editor.getSettings setFoldingOutlineShown false

    worksheetViewer.getComponent setPreferredSize prefDim

    if (modelSync) synch(editor, worksheetViewer)
    editor.getContentComponent.setPreferredSize(prefDim)

    if (!ApplicationManager.getApplication.isUnitTestMode) {
      val child = editorComponent.getParent
      val parent = child.getParent

      val diffPane = WorksheetDiffSplitters.createSimpleSplitter(editor, worksheetViewer, List.empty, List.empty, prop)

      worksheetViewer.putUserData(DIFF_SPLITTER_KEY, diffPane)

      @inline def patchEditor() {
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
        case splitter: Splitter =>
          parent.remove(1)
          parent.add(diffPane, 1)
        case _ => patchEditor()
      } else patchEditor()
    }

    WorksheetViewerInfo.addViewer(worksheetViewer, editor)
    worksheetViewer
  }

  private def createBlankEditor(project: Project): Editor = {
    val factory: EditorFactory = EditorFactory.getInstance
    val editor: Editor = factory.createViewer(factory createDocument "", project)
    editor setBorder null
    editor
  }
}
