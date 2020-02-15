package org.jetbrains.plugins.scala.worksheet.runconfiguration

import java.io.File

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.{Editor, EditorFactory}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.CompilerMessagesCollector
import org.jetbrains.plugins.scala.worksheet.ui.printers.{WorksheetEditorPrinter, WorksheetEditorPrinterRepl}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.{Failure, Try}

final class WorksheetCache extends Disposable {

  private val allViewers      = ContainerUtil.createWeakMap[Editor, List[Editor]]()
  private val allReplPrinters = ContainerUtil.createWeakMap[Editor, WorksheetEditorPrinter]()
  private val patchedEditors  = ContainerUtil.createWeakMap[Editor, String]()

  private val Log: Logger = Logger.getInstance(getClass)

  @TestOnly
  private val allCompilerMessagesCollectors = ContainerUtil.createWeakMap[Editor, CompilerMessagesCollector]()
  
  private val compilationInfo = mutable.HashMap.empty[String, (Int, File, File)]

  def updateOrCreateCompilationInfo(filePath: String, fileName: String): (Int, File, File) =
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

  @TestOnly
  def getCompilerMessagesCollector(inputEditor: Editor): Option[CompilerMessagesCollector] =
    Option(allCompilerMessagesCollectors.get(inputEditor))

  @TestOnly
  def addCompilerMessagesCollector(inputEditor: Editor, collector: CompilerMessagesCollector): Unit =
    allCompilerMessagesCollectors.put(inputEditor, collector)
  
  def peakCompilationIteration(filePath: String): Int =
    compilationInfo.get(filePath).map(_._1).getOrElse(-1)
  
  def getPrinter(inputEditor: Editor): Option[WorksheetEditorPrinter] =
    Option(allReplPrinters.get(inputEditor))
  
  def addPrinter(inputEditor: Editor, printer: WorksheetEditorPrinter): Unit =
    allReplPrinters.put(inputEditor, printer)
  
  def removePrinter(inputEditor: Editor): Unit = {
    val removed = allReplPrinters.remove(inputEditor)
    removed.close()
  }

  def getLastProcessedIncremental(inputEditor: Editor): Option[Int] =
    Option(allReplPrinters.get(inputEditor)).flatMap {
      case in: WorksheetEditorPrinterRepl => in.getLastProcessedLine
      case _                              => None
    }
  
  def setLastProcessedIncremental(inputEditor: Editor, line: Option[Int]): Unit =
    allReplPrinters.get(inputEditor) match {
      case inc: WorksheetEditorPrinterRepl => inc.setLastProcessedLine(line)
      case _                               =>
    }
  
  def getPatchedFlag(editor: Editor): String = Option(patchedEditors.get(editor)).orNull
  
  def setPatchedFlag(editor: Editor, flag: String): Unit =
    patchedEditors.put(editor, flag)
  
  def removePatchedFlag(editor: Editor): Unit =
    patchedEditors.remove(editor)
  
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

  def addViewer(viewer: Editor, editor: Editor): Unit =
    synchronized {
      allViewers.get(editor) match {
        case null =>
          allViewers.put(editor, viewer :: Nil)
        case list: List[Editor] =>
          allViewers.put(editor, viewer :: list)
      }
    }

  def disposeViewer(viewer: Editor, editor: Editor): Unit =
    synchronized {
      allViewers get editor match {
        case null =>
        case list: List[Editor] =>
          allViewers.put(editor, list.filter(sViewer => sViewer != viewer))
      }
    }

  override def dispose(): Unit = {
    invalidatePrinters()
    invalidateViewers()
  }

  private def logErrors[T](body: => T): Unit =
    Try(body) match {
      case Failure(exception) =>
        Log.error(exception)
      case _=>
    }

  private def invalidatePrinters(): Unit = {
    for {
      printer <- allReplPrinters.asScala.values
    } logErrors(printer.close())
    allReplPrinters.clear()
  }

  private def invalidateViewers(): Unit = {
    val factory = EditorFactory.getInstance()
    for {
      editors <- allViewers.values.asScala
      editor  <- editors
      if !editor.isDisposed
    } logErrors(factory.releaseEditor(editor))
    allViewers.clear()
  }

  private def get(editor: Editor): Editor =
    synchronized {
      allViewers.get(editor) match {
        case null => null
        case list => list.headOption.orNull
      }
    }
}

object WorksheetCache {
  def getInstance(project: Project): WorksheetCache = project.getService(classOf[WorksheetCache])
}