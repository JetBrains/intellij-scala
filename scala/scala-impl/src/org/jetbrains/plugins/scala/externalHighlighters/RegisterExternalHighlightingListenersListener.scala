package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.openapi.editor.event.{DocumentEvent, DocumentListener}
import com.intellij.openapi.fileEditor.{FileDocumentManager, FileEditorManagerEvent, FileEditorManagerListener}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiManager, PsiTreeChangeAdapter, PsiTreeChangeEvent}
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingMode
import org.jetbrains.plugins.scala.externalHighlighters.compiler.{HighlightingCompiler, HighlightingCompilerImpl}
import org.jetbrains.plugins.scala.project.VirtualFileExt
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.plugins.scala.util.ExclusiveDelayedExecutor

class RegisterExternalHighlightingListenersListener(project: Project)
  extends FileEditorManagerListener {

  private val compiler: HighlightingCompiler = new HighlightingCompilerImpl
  private val saveAllDocumentsExecutor = new ExclusiveDelayedExecutor

  private var listeners: Option[Listeners] = None

  override def selectionChanged(event: FileEditorManagerEvent): Unit =
    if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) {
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

  private case class Listeners(documentListener: CompilingDocumentListener,
                               psiTreeListener: SavingProjectPsiTreeChangeListener)
    extends Registering {

    def this(selectedFile: VirtualFile) =
      this(
        documentListener = new CompilingDocumentListener(selectedFile),
        psiTreeListener = new SavingProjectPsiTreeChangeListener(selectedFile)
      )

    private def allRegistering: Seq[Registering] =
      productIterator.toList.asInstanceOf[Seq[Registering]]

    override def register(): Unit =
      allRegistering.foreach(_.register())

    override def deregister(): Unit =
      allRegistering.foreach(_.deregister())
  }

  private class CompilingDocumentListener(selectedFile: VirtualFile)
    extends DocumentListener
      with Registering {

    private var wasChanged = false
    private val executor = new ExclusiveDelayedExecutor

    final override def documentChanged(event: DocumentEvent): Unit = {
      wasChanged = true
      executor.execute(ScalaHighlightingMode.compilationDelay) {
        compiler.compile(project, event.getDocument)
      }
    }

    override def deregister(): Unit =
      selectedFile.toDocument.foreach { document =>
        document.removeDocumentListener(this)
        if (wasChanged) FileDocumentManager.getInstance.saveDocument(document)
      }

    override def register(): Unit =
      selectedFile.toDocument.foreach(_.addDocumentListener(this))
  }

  private class SavingProjectPsiTreeChangeListener(val selectedFile: VirtualFile)
    extends PsiTreeChangeAdapter
      with Registering {

    override def childrenChanged(event: PsiTreeChangeEvent): Unit =
      Option(event.getFile.getVirtualFile)
        .filter(_ != selectedFile)
        .foreach { _ =>
          saveAllDocumentsExecutor.execute(ScalaHighlightingMode.compilationJpsDelay) {
            invokeAndWait(FileDocumentManager.getInstance.saveAllDocuments())
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
