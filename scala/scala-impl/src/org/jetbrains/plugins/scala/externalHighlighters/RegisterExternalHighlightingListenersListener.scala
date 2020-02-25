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
    deregisterListeners()
    registerListeners(event.getNewFile)
  }

  private def deregisterListeners(): Unit = {
    listeners.foreach(_.deregister())
    listeners = None
  }

  private def registerListeners(selectedFile: VirtualFile): Unit =
    Option(selectedFile).flatMap(_.toDocument).foreach { _ =>
      val newListeners = new Listeners(selectedFile)
      listeners = Some(newListeners)
      newListeners.register()
    }

  private def ifEnabled(action: => Unit): Unit =
    if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project))
      action

  private def saveDocument(document: Document): Unit =
    FileDocumentManager.getInstance.saveDocument(document)

  private def compileProject(selectedFile: VirtualFile): Unit =
    jpsCompilerExecutor.execute(ScalaHighlightingMode.compilationJpsDelay) {
      invokeAndWait(selectedFile.toDocument.foreach(saveDocument))
      jpsCompiler.compile(project)
    }

  private case class Listeners(selectedFileListener: SelectedFileListener,
                               otherFilesListener: OtherFilesListener)
    extends Registering {

    def this(selectedFile: VirtualFile) =
      this(
        selectedFileListener = new SelectedFileListener(selectedFile),
        otherFilesListener = new OtherFilesListener(selectedFile)
      )

    private def allRegistering: Seq[Registering] =
      productIterator.toList.asInstanceOf[Seq[Registering]]

    override def register(): Unit =
      allRegistering.foreach(_.register())

    override def deregister(): Unit =
      allRegistering.foreach(_.deregister())
  }

  private class SelectedFileListener(selectedFile: VirtualFile)
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

    override def deregister(): Unit =
      selectedFile.toDocument.foreach { selectedDocument =>
        selectedDocument.removeDocumentListener(this)
        compileProject(selectedFile)
      }

    override def register(): Unit =
      selectedFile.toDocument.foreach(_.addDocumentListener(this))
  }

  private class OtherFilesListener(val selectedFile: VirtualFile)
    extends PsiTreeChangeAdapter
      with Registering {

    override def childrenChanged(event: PsiTreeChangeEvent): Unit = ifEnabled {
      Option(event.getFile)
        .flatMap(_.getVirtualFile.toOption)
        .filter(_ != selectedFile)
        .flatMap(_.toDocument)
        .foreach { document =>
          saveDocument(document)
          compileProject(selectedFile)
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
