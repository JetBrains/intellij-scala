package org.jetbrains.plugins.scala.externalHighlighters

import java.util.concurrent.ScheduledThreadPoolExecutor

import com.intellij.openapi.editor.event.{DocumentEvent, DocumentListener}
import com.intellij.openapi.fileEditor.{FileEditorManagerEvent, FileEditorManagerListener}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingMode
import org.jetbrains.plugins.scala.externalHighlighters.compiler.{HighlightingCompiler, HighlightingCompilerImpl, LoggingHighlightingCompiler}
import org.jetbrains.plugins.scala.project.VirtualFileExt

class RegisterHighlightingCompilerListener(project: Project)
  extends FileEditorManagerListener {

  private val compiler: HighlightingCompiler = new HighlightingCompilerImpl

  @volatile private var listener: Option[CompileDocumentListener] = None

  override def selectionChanged(event: FileEditorManagerEvent): Unit =
    if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) {
      deregister(event.getOldFile)
      register(event.getNewFile)
    }

  private def deregister(virtualFile: VirtualFile): Unit =
    for {
      oldListener <- listener
      oldDocument <- Option(virtualFile).flatMap(_.toDocument)
    } {
      oldDocument.removeDocumentListener(oldListener)
      listener = None
    }

  private def register(virtualFile: VirtualFile): Unit =
    Option(virtualFile).flatMap(_.toDocument).foreach { newDocument =>
      val newListener = new CompileDocumentListener(compiler)
      listener = Some(newListener)
      newDocument.addDocumentListener(newListener)
    }

  private class CompileDocumentListener(compiler: HighlightingCompiler)
    extends DocumentListener {

    private val executor = new ScheduledThreadPoolExecutor(1)

    final override def documentChanged(event: DocumentEvent): Unit =
      if (executor.getQueue.size == 0) {
        val delay = ScalaHighlightingMode.compilationDelay
        val runnable: Runnable = () => compiler.compile(project, event.getDocument)
        executor.schedule(runnable, delay.length, delay.unit)
      }
  }
}
