package org.jetbrains.plugins.scala.worksheet.runconfiguration

import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.{Editor, EditorFactory}
import com.intellij.openapi.project.Project
import com.intellij.util.containers.WeakHashMap
import org.jetbrains.plugins.scala.worksheet.ui.{WorksheetEditorPrinterBase, WorksheetIncrementalEditorPrinter}

/**
  * User: Dmitry.Naydanov
  * Date: 03.02.17.
  */
class WorksheetCache(project: Project) extends AbstractProjectComponent(project)  {
  private val allViewers = new WeakHashMap[Editor, List[(Editor)]]()
  private val allReplPrinters = new WeakHashMap[Editor, WorksheetEditorPrinterBase]()
  private val patchedEditors = new WeakHashMap[Editor, String]()
  
  def getPrinter(inputEditor: Editor): Option[WorksheetEditorPrinterBase] = Option(allReplPrinters get inputEditor)
  
  def addPrinter(inputEditor: Editor, printer: WorksheetEditorPrinterBase) {
    allReplPrinters.put(inputEditor, printer)
  }
  
  def removePrinter(inputEditor: Editor) {
    allReplPrinters.remove(inputEditor)
  }
  
  def getLastProcessedIncremental(inputEditor: Editor): Option[Int] = {
    Option(allReplPrinters get inputEditor) flatMap {
      case in: WorksheetIncrementalEditorPrinter => in.getLastProcessedLine
      case _ => None
    }
  }
  
  def setLastProcessedIncremental(inputEditor: Editor, v: Option[Int]) {
    allReplPrinters get inputEditor match {
      case inc: WorksheetIncrementalEditorPrinter => inc setLastProcessedLine v
      case _ => 
    }
  }
  
  def getPatchedFlag(editor: Editor): String = Option(patchedEditors.get(editor)).orNull
  
  def setPatchedFlag(editor: Editor, flag: String) {
    patchedEditors.put(editor, flag)
  }
  
  def removePatchedFlag(editor: Editor) {
    patchedEditors.remove(editor)
  }
  
  def getViewer(editor: Editor): Editor = get(editor)

  def addViewer(viewer: Editor, editor: Editor) {
    synchronized {
      allViewers get editor match {
        case null =>
          allViewers.put(editor, viewer :: Nil)
        case list: List[Editor] =>
          allViewers.put(editor, viewer :: list)
      }
    }
  }

  def disposeViewer(viewer: Editor, editor: Editor) {
    synchronized {
      allViewers get editor match {
        case null =>
        case list: List[Editor] =>
          allViewers.put(editor, list.filter(sViewer => sViewer != viewer))
      }
    }
  }

  private def invalidateViewers() {
    val i = allViewers.values().iterator()
    val factory = EditorFactory.getInstance()

    while (i.hasNext) {
      i.next().foreach {
        case e: EditorImpl =>
          if (!e.isDisposed) try {
            factory.releaseEditor(e)
          } catch {
            case _: Exception => //ignore
          }
        case _ =>
      }
    }
  }

  private def get(editor: Editor): Editor = {
    synchronized {
      allViewers get editor match {
        case null => null
        case list => list.headOption.orNull
      }
    }
  }

  override def projectClosed(): Unit = {
    invalidateViewers()
  }

  override def getComponentName: String = "WorksheetCache"
}

object WorksheetCache {
  def getInstance(project: Project): WorksheetCache = project.getComponent(classOf[WorksheetCache])
}