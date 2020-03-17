package org.jetbrains.plugins.scala.externalHighlighters

import java.util.concurrent.ConcurrentHashMap

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.{Project, ProjectManagerListener}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiManager, PsiTreeChangeAdapter, PsiTreeChangeEvent}
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingMode
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.externalHighlighters.compiler.JpsCompilationUtil
import org.jetbrains.plugins.scala.project.VirtualFileExt

private class RegisterProjectListeners
  extends ProjectManagerListener {

  private val listeners = new ConcurrentHashMap[Project, PsiTreeListener]()

  override def projectOpened(project: Project): Unit = {
    val listener = new PsiTreeListener(project)
    listeners.put(project, listener)
    PsiManager.getInstance(project).addPsiTreeChangeListener(listener)
  }

  override def projectClosing(project: Project): Unit = {
    val listener = listeners.remove(project)
    PsiManager.getInstance(project).removePsiTreeChangeListener(listener)
  }

  private class PsiTreeListener(project: Project)
    extends PsiTreeChangeAdapter {

    override def childrenChanged(event: PsiTreeChangeEvent): Unit = {
      val selectedFile = project.selectedDocument.flatMap(_.virtualFile)
      for {
        psiFile <- event.getFile.toOption
        changedFile <- psiFile.getVirtualFile.toOption
        if !selectedFile.contains(changedFile)
      } handle(changedFile)
    }

    override def childRemoved(event: PsiTreeChangeEvent): Unit =
      if (event.getFile eq null)
        for {
          child <- event.getChild.toOption
          containingFile <- child.getContainingFile.toOption
          removedFile <- containingFile.getVirtualFile.toOption
        } handle(removedFile)

    private def handle(modifiedFile: VirtualFile): Unit =
      if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project))
        modifiedFile.toDocument.foreach { document =>
          FileDocumentManager.getInstance.saveDocument(document)
          JpsCompilationUtil.saveDocumentAndCompileProject(project.selectedDocument, project)
        }
  }
}
