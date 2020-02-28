package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.{EditorFactoryEvent, EditorFactoryListener}
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

class RegisterOnProjectOpenedListener
  extends ProjectManagerListener {

  override def projectOpened(project: Project): Unit = {
    PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeListener(project))
    EditorFactory.getInstance.addEditorFactoryListener(new EditorCreatedListener(project), project)
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

  private class EditorCreatedListener(project: Project)
    extends EditorFactoryListener {

    override def editorCreated(event: EditorFactoryEvent): Unit = {
      val editor = event.getEditor
      val state = HighlightingStateManager.get(project)
      ExternalHighlighters.applyHighlighting(project, editor, state)
    }
  }
}
