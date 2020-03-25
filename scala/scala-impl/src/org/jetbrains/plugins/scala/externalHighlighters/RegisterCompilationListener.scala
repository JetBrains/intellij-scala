package org.jetbrains.plugins.scala.externalHighlighters

import java.util.concurrent.ConcurrentHashMap

import com.intellij.openapi.project.{Project, ProjectManagerListener}
import com.intellij.psi.{PsiFile, PsiManager, PsiTreeChangeAdapter, PsiTreeChangeEvent}
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingMode
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.extensions.ToNullSafe
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.VirtualFileExt
import org.jetbrains.plugins.scala.util.RescheduledExecutor

private class RegisterCompilationListener
  extends ProjectManagerListener {

  import RegisterCompilationListener.PsiTreeListener

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
}

object RegisterCompilationListener {

  private val executor = new RescheduledExecutor("CompileJpsExecutor")

  private class PsiTreeListener(project: Project)
    extends PsiTreeChangeAdapter {

    private val compiler: JpsCompiler = JpsCompiler.get(project)

    override def childrenChanged(event: PsiTreeChangeEvent): Unit =
      for {
        file <- event.getFile.nullSafe
      } handleFileModification(file)

    override def childRemoved(event: PsiTreeChangeEvent): Unit =
      if (event.getFile eq null)
        for {
          child <- event.getChild.nullSafe
          file <- child.getContainingFile.nullSafe
        } handleFileModification(file)

    private def handleFileModification(file: PsiFile): Unit =
      file match {
        case scalaFile: ScalaFile => handleFileModification(scalaFile)
        case _ =>
      }

    private def handleFileModification(file: ScalaFile): Unit = {
      val virtualFile = file.getVirtualFile match {
        case null => return
        case file => file
      }
      if (virtualFile.isInLocalFileSystem && ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) {
        virtualFile.findDocument.foreach { modifiedDocument =>
          modifiedDocument.syncToDisk(project)
          executor.schedule(ScalaHighlightingMode.compilationDelay)(compiler.compile())
        }
      }
    }
  }
}
