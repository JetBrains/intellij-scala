package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.{DocumentEvent, DocumentListener}
import com.intellij.openapi.fileEditor.{FileEditorManagerEvent, FileEditorManagerListener}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingMode
import org.jetbrains.plugins.scala.externalHighlighters.compiler.{JpsCompilationUtil, DocumentCompiler, DocumentCompilerImpl}
import org.jetbrains.plugins.scala.project.VirtualFileExt
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.util.RescheduledExecutor

class RegisterSelectedDocumentListener(project: Project)
  extends FileEditorManagerListener {

  private val documentCompiler: DocumentCompiler = new DocumentCompilerImpl
  private var documentListener: Option[Listener] = None

  override def selectionChanged(event: FileEditorManagerEvent): Unit = {
    documentListener.foreach(_.deregister())
    val document = event.getNewFile.toOption.flatMap(_.toDocument)
    documentListener = document.map(new Listener(_))
    documentListener.foreach(_.register())
  }

  private class Listener(selectedDocument: Document)
    extends DocumentListener {

    private var wasChanged = false
    private val executor = new RescheduledExecutor

    final override def documentChanged(event: DocumentEvent): Unit =
      if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) {
        wasChanged = true
        executor.schedule(ScalaHighlightingMode.compilationDelay) {
          documentCompiler.compile(project, event.getDocument)
        }
      }

    def deregister(): Unit = {
      selectedDocument.removeDocumentListener(this)
      if (wasChanged)
        JpsCompilationUtil.saveDocumentAndCompileProject(Some(selectedDocument), project)
    }

    def register(): Unit =
      selectedDocument.addDocumentListener(this)
  }
}
