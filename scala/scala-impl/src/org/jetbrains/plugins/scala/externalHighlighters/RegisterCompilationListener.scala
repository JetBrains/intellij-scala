package org.jetbrains.plugins.scala.externalHighlighters

import java.util.concurrent.ConcurrentHashMap

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.{FileEditorManager, FileEditorManagerListener}
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.{Project, ProjectManagerListener}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiJavaFile, PsiManager, PsiTreeChangeAdapter, PsiTreeChangeEvent}
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingMode
import org.jetbrains.plugins.scala.compiler.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.extensions.ToNullSafe
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.VirtualFileExt
import org.jetbrains.plugins.scala.util.RescheduledExecutor
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler

import scala.concurrent.duration.DurationInt

private class RegisterCompilationListener
  extends ProjectManagerListener {

  import RegisterCompilationListener.MyPsiTreeChangeListener

  private val listeners = new ConcurrentHashMap[Project, MyPsiTreeChangeListener]()

  override def projectOpened(project: Project): Unit = {
    val listener = new MyPsiTreeChangeListener(project)
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
  private val worksheetScheduler = new RescheduledExecutor("CompileWorksheetExecutor")

  // cause worksheet compilation doesn't require whole project rebuild
  // we start highlighting it right away on editor opening
  final class MyFileEditorManagerListener(override val project: Project)
    extends FileEditorManagerListener
      with HighlightingScheduler {

    protected override val compiler: JpsCompiler = JpsCompiler.get(project)

    override def fileOpened(source: FileEditorManager, file: VirtualFile): Unit =
      PsiManager.getInstance(project).findFile(file) match {
        case psiFile: ScalaFile if psiFile.isWorksheetFile =>
          tryHighlight(psiFile, file)
        case _ =>
      }
  }

  private class MyPsiTreeChangeListener(override val project: Project)
    extends PsiTreeChangeAdapter
      with HighlightingScheduler {

    protected override def compiler: JpsCompiler = JpsCompiler.get(project)

    override def childrenChanged(event: PsiTreeChangeEvent): Unit =
      for {
        file <- event.getFile.nullSafe
      } tryHighlight(file)

    override def childRemoved(event: PsiTreeChangeEvent): Unit =
      if (event.getFile eq null)
        for {
          child <- event.getChild.nullSafe
          file <- child.getContainingFile.nullSafe
        } tryHighlight(file)
  }

  sealed trait HighlightingScheduler {

    protected def project: Project
    protected def compiler: JpsCompiler

    protected def tryHighlight(file: PsiFile): Unit =
      file.getVirtualFile match {
        case null =>
        case virtualFile =>
          tryHighlight(file, virtualFile)
      }

    protected def tryHighlight(file: PsiFile, virtualFile: VirtualFile): Unit = {
      // in case user has "use compile server" setting disabled (for any reason) do not even try highlighting
      // yes, then user will not have any error highlighting
      // We already discussed once that probably nobody have the server disabled, and maybe we should remove this legacy
      // setting. See statistics of ScalaCompileServerSettings settings in 2020.2.
      if (!ScalaCompileServerSettings.getInstance.COMPILE_SERVER_ENABLED) return

      if (!virtualFile.isInLocalFileSystem) return
      if (!ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) return
      val document = virtualFile.findDocument.getOrElse(return)

      val delay = ScalaHighlightingMode.compilationDelay
      file match {
        case scalaFile: ScalaFile if scalaFile.isWorksheetFile =>
          worksheetScheduler.schedule(delay, virtualFile.getPath) {
            compileWorksheet(scalaFile, document)
          }
        case _: ScalaFile | _: PsiJavaFile =>
          document.syncToDisk(project)
          executor.schedule(delay) {
            compiler.compile()
          }
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
  }
}
