package org.jetbrains.plugins.scala.worksheet.runconfiguration

import java.io.File

import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.{Editor, EditorFactory}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.worksheet.ui.printers.{WorksheetEditorPrinter, WorksheetIncrementalEditorPrinter}

import scala.collection.mutable

/**
  * User: Dmitry.Naydanov
  * Date: 03.02.17.
  */
class WorksheetCache(project: Project) extends ProjectComponent  {
  private val allViewers = ContainerUtil.createWeakMap[Editor, List[Editor]]()
  private val allReplPrinters = ContainerUtil.createWeakMap[Editor, WorksheetEditorPrinter]()
  private val patchedEditors = ContainerUtil.createWeakMap[Editor, String]()
  
  private val compilationInfo = mutable.HashMap.empty[String, (Int, File, File)]

  def updateOrCreateCompilationInfo(filePath: String, fileName: String): (Int, File, File) = {
    compilationInfo.get(filePath) match {
      case Some(result@(it, src, out)) =>
        compilationInfo.put(filePath, (it + 1, src, out))
        result
      case _ =>
        val src = FileUtil.createTempFile(fileName, null, true)
        val out = FileUtil.createTempDirectory(fileName, null, true)

        compilationInfo.put(filePath, (1, src, out))
        (0, src, out)
    }
  }
  
  def peakCompilationIteration(filePath: String): Int = {
    compilationInfo.get(filePath).map(_._1).getOrElse(-1)
  }
  
  def getPrinter(inputEditor: Editor): Option[WorksheetEditorPrinter] = Option(allReplPrinters get inputEditor)
  
  def addPrinter(inputEditor: Editor, printer: WorksheetEditorPrinter) {
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
  
  def getViewer(editor: Editor): Editor = {
    val viewer = get(editor)
    
    if (viewer != null && viewer.isDisposed || editor.isDisposed) {
      synchronized {
        allViewers.remove(editor)
      }
      
      return null
    }
    
    viewer
  }

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