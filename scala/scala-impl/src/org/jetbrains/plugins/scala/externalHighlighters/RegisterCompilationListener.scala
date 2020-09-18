package org.jetbrains.plugins.scala
package externalHighlighters

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.{FileEditorManager, FileEditorManagerListener}
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.{Project, ProjectManagerListener}
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import org.jetbrains.plugins.scala.compiler.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.extensions.ToNullSafe
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.{ProjectExt, VirtualFileExt}
import org.jetbrains.plugins.scala.util.RescheduledExecutor

private class RegisterCompilationListener
  extends ProjectManagerListener {

  import RegisterCompilationListener.MyPsiTreeChangeListener

  override def projectOpened(project: Project): Unit = {
    val listener = new MyPsiTreeChangeListener(project)
    PsiManager.getInstance(project).addPsiTreeChangeListener(listener, project.unloadAwareDisposable)
  }
}

object RegisterCompilationListener {

  // cause worksheet compilation doesn't require whole project rebuild
  // we start highlighting it right away on editor opening
  final class MyFileEditorManagerListener(project: Project)
    extends FileEditorManagerListener {

    override def fileOpened(source: FileEditorManager, file: VirtualFile): Unit = {
      PsiManager.getInstance(project).findFile(file) match {
        case psiFile: ScalaFile if psiFile.isWorksheetFile =>
          scheduler(project).tryHighlight(psiFile, file)
        case _ =>
      }
    }
  }

  private class MyPsiTreeChangeListener(project: Project)
    extends PsiTreeChangeAdapter {

    private def scheduler = project.getService(classOf[HighlightingScheduler])

    override def childrenChanged(event: PsiTreeChangeEvent): Unit =
      for {
        file <- event.getFile.nullSafe
      } scheduler.tryHighlight(file)

    override def childRemoved(event: PsiTreeChangeEvent): Unit =
      if (event.getFile eq null)
        for {
          child <- event.getChild.nullSafe
          file <- child.getContainingFile.nullSafe
        } scheduler.tryHighlight(file)
  }

  private def scheduler(project: Project) = project.getService(classOf[HighlightingScheduler])

  @Service
  private final class HighlightingScheduler(project: Project) extends Disposable {
    private val nonJpsExecutor = new RescheduledExecutor("CompileNonJpsExecutor", this)

    protected def compiler: HighlightingCompiler = HighlightingCompiler.get(project)

    def tryHighlight(file: PsiFile): Unit =
      file.getVirtualFile match {
        case null =>
        case virtualFile =>
          tryHighlight(file, virtualFile)
      }

    def tryHighlight(file: PsiFile, virtualFile: VirtualFile): Unit =
      if (needToHighlighting(virtualFile)) {
        val document = virtualFile.findDocument match {
          case Some(v) => v
          case _ =>
            return
        }
        doTryHighlight(file, virtualFile, document)
      }

    private def needToHighlighting(virtualFile: VirtualFile): Boolean = {
      // in case user has "use compile server" setting disabled (for any reason) do not even try highlighting
      // yes, then user will not have any error highlighting
      // We already discussed once that probably nobody have the server disabled, and maybe we should remove this legacy
      // setting. See statistics of ScalaCompileServerSettings settings in 2020.2.
      if (!ScalaCompileServerSettings.getInstance.COMPILE_SERVER_ENABLED)
        false
      else if (!virtualFile.isInLocalFileSystem)
        false
      else if (!ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project))
        false
      else
        true
    }

    private def doTryHighlight(file: PsiFile, virtualFile: VirtualFile, document: Document): Unit = {
      // this is ugly, unify with JpsCompiler, we could create some common interface,
      // but need to think carefully about proper abstractions
      HighlightingCompilerHelper.implementations.find(_.canHighlight(file)) match {
        case Some(highlighter) =>
          nonJpsExecutor.schedule(ScalaHighlightingMode.compilationDelay, key = virtualFile.getPath) {
            val indicator = new EmptyProgressIndicator
            val client = new CompilerEventGeneratingClient(project, indicator)
            highlighter.runHighlightingCompilation(project, file, document, client)
          }
        case _ =>
          file match {
            case _: ScalaFile | _: PsiJavaFile =>
              document.syncToDisk(project)
              val projectFileIndex = ProjectFileIndex.getInstance(project)
              compiler.rescheduleCompilation(delayedProgressShow = true, forceCompileFile = Some(virtualFile))
            case _ =>
          }
      }
    }

    override def dispose(): Unit = {}
  }
}
