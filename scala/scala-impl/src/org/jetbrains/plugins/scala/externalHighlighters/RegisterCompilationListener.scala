package org.jetbrains.plugins.scala
package externalHighlighters

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import org.jetbrains.plugins.scala.compiler.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.extensions.ToNullSafe
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.project.VirtualFileExt
import org.jetbrains.plugins.scala.util.RescheduledExecutor
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler

import scala.concurrent.duration.DurationInt

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

    override def fileOpened(source: FileEditorManager, file: VirtualFile): Unit =
      PsiManager.getInstance(project).findFile(file) match {
        case psiFile: ScalaFile if psiFile.isWorksheetFile =>
          scheduler(project).tryHighlight(psiFile, file)
        case _ =>
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
    private val worksheetExecutor = new RescheduledExecutor("CompileWorksheetExecutor", this)

    protected def compiler: HighlightingCompiler = HighlightingCompiler.get(project)

    def tryHighlight(file: PsiFile): Unit =
      file.getVirtualFile match {
        case null =>
        case virtualFile =>
          tryHighlight(file, virtualFile)
      }

    def tryHighlight(file: PsiFile, virtualFile: VirtualFile): Unit = {
      // in case user has "use compile server" setting disabled (for any reason) do not even try highlighting
      // yes, then user will not have any error highlighting
      // We already discussed once that probably nobody have the server disabled, and maybe we should remove this legacy
      // setting. See statistics of ScalaCompileServerSettings settings in 2020.2.
      if (!ScalaCompileServerSettings.getInstance.COMPILE_SERVER_ENABLED) return

      if (!virtualFile.isInLocalFileSystem) return
      if (!ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) return
      val document = virtualFile.findDocument.getOrElse(return)

      file match {
        case scalaFile: ScalaFile if scalaFile.isWorksheetFile =>
          worksheetExecutor.schedule(ScalaHighlightingMode.compilationDelay, virtualFile.getPath) {
            compileWorksheet(scalaFile, document)
          }
        case _: ScalaFile | _: PsiJavaFile =>
          document.syncToDisk(project)
          compiler.rescheduleCompilation(delayedProgressShow = true, forceCompileFile = Some(virtualFile))
        case _ =>
      }
    }

    protected def compileWorksheet(scalaFile: ScalaFile, document: Document): Unit = {
      val module = scalaFile.module.getOrElse(return)
      val compiler = new WorksheetCompiler(module, scalaFile)
      val indicator = new EmptyProgressIndicator
      compiler.compileOnlySync(
        document,
        client = new CompilerEventGeneratingClient(project, indicator),
        waitAtMost = 60.seconds
      )
    }

    override def dispose(): Unit = {}
  }
}
