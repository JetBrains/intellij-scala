package org.jetbrains.plugins.scala
package worksheet.ui

import com.intellij.openapi.editor.{Document, EditorFactory, Editor}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBSplitter
import java.awt.{BorderLayout, Dimension}
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetViewerInfo
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.application.ApplicationManager
import javax.swing.{Timer, JComponent, JLayeredPane}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetSourceProcessor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import java.awt.event.{ActionEvent, ActionListener}
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.vfs.newvfs.FileAttribute
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala

/**
 * User: Dmitry Naydanov
 * Date: 1/20/14
 */
class WorksheetEditorPrinter(originalEditor: Editor, worksheetViewer: Editor) {
  private val project = originalEditor.getProject
  private val originalDocument = originalEditor.getDocument
  private val viewerDocument = worksheetViewer.getDocument
  private val timer = new Timer(WorksheetEditorPrinter.IDLE_TIME_MLS, TimerListener)
  
  private val outputBuffer = new StringBuilder
  private var linesCount = 0
  private var totalCount = 0
  private var insertedToOriginal = 0
  @volatile private var terminated = false
  
  private var inited = false
  private var cutoffPrinted = false
  
  @volatile private var buffed = 0
  
  originalEditor.asInstanceOf[EditorImpl].setScrollToCaret(false)
  worksheetViewer.asInstanceOf[EditorImpl].setScrollToCaret(false)
  
  def getViewerEditor = worksheetViewer
  
  def getOriginalEditor = originalEditor
  
  def scheduleWorksheetUpdate() {
    timer.start()
  }
  
  def processLine(line: String): Boolean = {
    if (!inited) init()
    
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
          val differ = end - start + 1 - linesCount // inputSize - linesCount

          if (differ > 0) {
            /*if (!cutoffPrinted)*/ outputBuffer append getNewLines(differ)
          } else if (0 > differ) {
            val actualEnd = end + insertedToOriginal
            insertedToOriginal -= differ 
            
            extensions.invokeLater {
              extensions.inWriteAction {
                CommandProcessor.getInstance() runUndoTransparentAction new Runnable {
                  override def run() {
                    originalDocument.insertString(originalDocument getLineEndOffset actualEnd, getNewLines(-differ))
                    commitDocument(originalDocument)
                  }
                }
              }
            }
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
  
  private def init() {
    inited = true

    (originalEditor, worksheetViewer) match {
      case (originalImpl: EditorImpl, viewerImpl: EditorImpl) =>
        ApplicationManager.getApplication invokeLater new Runnable {
          override def run() {
            viewerImpl.getScrollPane.getVerticalScrollBar setModel originalImpl.getScrollPane.getVerticalScrollBar.getModel
          }
        }
      case _ =>
    }
  }
  
  private def isResultEnd(line: String) = line startsWith WorksheetSourceProcessor.END_TOKEN_MARKER
  
  private def getNewLines(count: Int) = StringUtil.repeatSymbol('\n', count)
  
  private def clear() {
    linesCount = 0
    cutoffPrinted = false
  }
  
  def flushBuffer() {
    if (terminated) return
    val str = outputBuffer.toString()
    
    if (timer.isRunning) timer.stop()
    
    updateWithPersistentScroll(viewerDocument, str)
    
    outputBuffer.clear()

    scala.extensions.inReadAction {
      PsiDocumentManager.getInstance(project).getPsiFile(originalEditor.getDocument) match {
        case scalaFile: ScalaFile => WorksheetEditorPrinter.saveWorksheetEvaluation(scalaFile, str)
        case _ =>
      }
    }
  }
  
  def midFlush() {
    if (terminated || buffed == 0) return
        
    val str = outputBuffer.toString()
    buffed = 0

    updateWithPersistentScroll(viewerDocument, str)
  }
  
  private def updateWithPersistentScroll(document: Document, text: String) {//todo - to do
    extensions.invokeLater {
      extensions.inWriteAction {
        val scroll = originalEditor.getScrollingModel.getVerticalScrollOffset
        val worksheetScroll = worksheetViewer.getScrollingModel.getVerticalScrollOffset
        
        document.setText(text)
        
        commitDocument(document)

        originalEditor.getScrollingModel.scrollVertically(scroll)
        worksheetViewer.getScrollingModel.scrollHorizontally(worksheetScroll)
      }
    }
  }

  private def commitDocument(doc: Document) {
    PsiDocumentManager getInstance project commitDocument doc
  }
  
  private def isInsideOutput = linesCount != 0 
  
  private def getOutputLimit = ScalaProjectSettings.getInstance(project).getOutputLimit
  

  object TimerListener extends ActionListener {
    override def actionPerformed(e: ActionEvent) {
      midFlush()
    }
  }
}

object WorksheetEditorPrinter {
  val END_MESSAGE = "Output exceeds cutoff limit.\n"
  val BULK_COUNT = 15
  val IDLE_TIME_MLS = 1000

  private val LAST_WORKSHEET_RUN_RESULT = new FileAttribute("LastWorksheetRunResult", 1, false)
  
  def saveWorksheetEvaluation(file: ScalaFile, result: String) {
    LAST_WORKSHEET_RUN_RESULT.writeAttributeBytes(file.getVirtualFile, result.getBytes)
  }
  
  def loadWorksheetEvaluation(file: ScalaFile): Option[String] = {
    Option(LAST_WORKSHEET_RUN_RESULT.readAttributeBytes(file.getVirtualFile)) map (new String(_))
  }
  
  def deleteWorksheetEvaluation(file: ScalaFile) {
    LAST_WORKSHEET_RUN_RESULT.writeAttributeBytes(file.getVirtualFile, Array.empty[Byte])
  }

  def newWorksheetUiFor(editor: Editor, virtualFile: VirtualFile) = 
    new WorksheetEditorPrinter(editor,  createWorksheetViewer(editor, virtualFile))
  
  def createWorksheetViewer(editor: Editor, virtualFile: VirtualFile, modelSync: Boolean = false): Editor = {
    val editorComponent = editor.getComponent
    val project = editor.getProject

    val prop = if (editorComponent.getComponentCount > 0) editorComponent.getComponent(0) match {
      case splitter: JBSplitter => splitter.getProportion
      case _ => 0.5f
    } else 0.5f
    val dimension = editorComponent.getSize()
    val prefDim = new Dimension(dimension.width / 2, dimension.height) 

    editor.getSettings setFoldingOutlineShown false

    val worksheetViewer = WorksheetViewerInfo getViewer editor match {
      case editorImpl: EditorImpl => editorImpl
      case _ => createBlankEditor(project).asInstanceOf[EditorImpl] 
    }

    worksheetViewer.getComponent setPreferredSize prefDim

    val gutter: EditorGutterComponentEx = worksheetViewer.getGutterComponentEx
    if (gutter != null && gutter.getParent != null) gutter.getParent remove gutter

    if (modelSync) {
      worksheetViewer.getScrollPane.getVerticalScrollBar.setModel(
        editor.asInstanceOf[EditorImpl].getScrollPane.getVerticalScrollBar.getModel)
    }
    editor.getContentComponent.setPreferredSize(prefDim)

    if (!ApplicationManager.getApplication.isUnitTestMode) {
      val child = editorComponent.getParent
      val parent = child.getParent

      @inline def patchEditor() {
        val pane = new JBSplitter(false, prop)
        pane setSecondComponent worksheetViewer.getComponent

        (parent, child) match {
          case (parentPane: JLayeredPane, _) => 
            parentPane remove child
            pane.setFirstComponent(child.getComponent(0).asInstanceOf[JComponent])
            parentPane.add(pane, BorderLayout.CENTER)
          case (_, childPane: JLayeredPane) =>
            childPane remove editorComponent
            pane setFirstComponent editorComponent
            childPane.add(pane, BorderLayout.CENTER)
          case _ => 
        }
      }
      
      if (parent.getComponentCount > 1) parent.getComponent(1) match {
        case splitter: JBSplitter => splitter setSecondComponent worksheetViewer.getComponent
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
