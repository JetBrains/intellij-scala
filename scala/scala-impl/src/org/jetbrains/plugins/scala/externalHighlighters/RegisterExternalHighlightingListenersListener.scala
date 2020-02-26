package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.{DocumentEvent, DocumentListener}
import com.intellij.openapi.fileEditor.{FileDocumentManager, FileEditorManagerEvent, FileEditorManagerListener}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiManager, PsiTreeChangeAdapter, PsiTreeChangeEvent}
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingMode
import org.jetbrains.plugins.scala.externalHighlighters.compiler.{JpsCompiler, JpsCompilerImpl, DocumentCompiler, DocumentCompilerImpl}
import org.jetbrains.plugins.scala.project.VirtualFileExt
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.util.ExclusiveDelayedExecutor

class RegisterExternalHighlightingListenersListener(project: Project)
  extends FileEditorManagerListener {

  private val documentCompiler: DocumentCompiler = new DocumentCompilerImpl
  private val jpsCompiler: JpsCompiler = new JpsCompilerImpl
  private val jpsCompilerExecutor = new ExclusiveDelayedExecutor

  private var listeners: Option[Listeners] = None

  override def selectionChanged(event: FileEditorManagerEvent): Unit = {
    println("selectionChanged")
    deregisterListeners()
    registerListeners(event.getNewFile.toOption)
  }

  private def deregisterListeners(): Unit = {
    listeners.foreach(_.deregister())
    listeners = None
  }

  private def registerListeners(selectedFile: Option[VirtualFile]): Unit = {
    val newListeners = new Listeners(selectedFile)
    listeners = Some(newListeners)
    newListeners.register()
  }

  private def ifEnabled(action: => Unit): Unit =
    if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project))
      action

  private def saveDocument(document: Document): Unit =
    FileDocumentManager.getInstance.saveDocument(document)

  private def saveSelectedAndCompileProject(selectedDocument: Option[Document]): Unit = {
    val result = jpsCompilerExecutor.execute(ScalaHighlightingMode.compilationJpsDelay) {
      invokeAndWait(selectedDocument.foreach(saveDocument))
      jpsCompiler.compile(project)
    }
    println(s"Project compiled: $result")
  }

  private case class Listeners(selectedDocumentListener: Option[SelectedDocumentListener],
                               otherFilesListener: OtherFilesListener)
    extends Registering {

    def this(selectedFile: Option[VirtualFile]) =
      this(
        selectedDocumentListener = selectedFile.flatMap(_.toDocument).map(new SelectedDocumentListener(_)),
        otherFilesListener = new OtherFilesListener(selectedFile)
      )

    private def allRegistering: Seq[Registering] =
      productIterator.toSeq.flatMap {
        case option: Option[_] => option.toSeq
        case flatValue => Seq(flatValue)
      }.collect {
        case registering: Registering => registering
      }

    override def register(): Unit =
      allRegistering.foreach(_.register())

    override def deregister(): Unit =
      allRegistering.foreach(_.deregister())
  }

  private class SelectedDocumentListener(selectedDocument: Document)
    extends DocumentListener
      with Registering {

    private var wasChanged = false
    private val executor = new ExclusiveDelayedExecutor

    final override def documentChanged(event: DocumentEvent): Unit = ifEnabled {
      wasChanged = true
      executor.execute(ScalaHighlightingMode.compilationDelay) {
        documentCompiler.compile(project, event.getDocument)
      }
    }

    override def deregister(): Unit = {
      selectedDocument.removeDocumentListener(this)
      if (wasChanged) saveSelectedAndCompileProject(Some(selectedDocument))
    }

    override def register(): Unit =
      selectedDocument.addDocumentListener(this)
  }

  private class OtherFilesListener(val selectedFile: Option[VirtualFile])
    extends PsiTreeChangeAdapter
      with Registering {

    override def childrenChanged(event: PsiTreeChangeEvent): Unit =
      for {
        psiFile <- event.getFile.toOption
        changedFile <- psiFile.getVirtualFile.toOption
        if !selectedFile.contains(changedFile)
      } handle(changedFile)

    override def childRemoved(event: PsiTreeChangeEvent): Unit =
      if (event.getFile eq null)
        for {
          child <- event.getChild.toOption
          containingFile <- child.getContainingFile.toOption
          removedFile <- containingFile.getVirtualFile.toOption
        } handle(removedFile)

    private def handle(virtualFile: VirtualFile): Unit = ifEnabled {
      virtualFile.toDocument.foreach { document =>
        saveDocument(document)
        saveSelectedAndCompileProject(selectedFile.flatMap(_.toDocument))
      }
    }

    override def deregister(): Unit =
      psiManager.removePsiTreeChangeListener(this)

    override def register(): Unit =
      psiManager.addPsiTreeChangeListener(this)

    private def psiManager: PsiManager =
      PsiManager.getInstance(project)
  }

  trait Registering {
    def deregister(): Unit
    def register(): Unit
  }
}
