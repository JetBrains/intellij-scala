package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.{FileEditor, FileEditorManager}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.JavaProjectRootsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiErrorElement, PsiFile, PsiJavaFile, PsiManager}
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.compiler.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, ObjectExt, PsiElementExt, PsiFileExt, ToNullSafe, inReadAction}
import org.jetbrains.plugins.scala.externalHighlighters.TriggerCompilerHighlightingService.hasErrors
import org.jetbrains.plugins.scala.externalHighlighters.compiler.DocumentCompiler
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.VirtualFileExt
import org.jetbrains.plugins.scala.util.ScalaUtil

import java.nio.file.{Path, Paths}
import scala.concurrent.{ExecutionContext, Future}

@Service
final class TriggerCompilerHighlightingService(project: Project)
  extends Disposable {

  import TriggerCompilerHighlightingService.modulesForFiles

  private var alreadyHighlighted = Set.empty[Path]
  private var changedFiles = Set.empty[VirtualFile]

  private val threadPool = AppExecutorUtil.createBoundedApplicationPoolExecutor("TriggerCompilerHighlighting", 1)
  private implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(threadPool)

  def triggerOnFileChange(psiFile: PsiFile, virtualFile: VirtualFile): Unit =
    if (isHighlightingEnabled && isHighlightingEnabledFor(psiFile, virtualFile) && !hasErrors(psiFile))
      virtualFile.findDocument.foreach { document =>
        val scalaFile = psiFile.asOptionOf[ScalaFile]
        val debugReason = s"file content changed: ${psiFile.getName}"
        if (psiFile.isScalaWorksheet)
          scalaFile.foreach(triggerWorksheetCompilation(_, document, markHighlighted = None))
        else asyncAtomic {
          val changedFilesSizeBefore = changedFiles.size
          changedFiles += virtualFile
          if (changedFiles.size > 1 && changedFiles.size != changedFilesSizeBefore)
            triggerIncrementalCompilation(debugReason, modulesForFiles(changedFiles)(project))
          else
            scalaFile.foreach(triggerDocumentCompilation(debugReason, document, _, markHighlighted = None))
        }
      }

  def triggerOnSelectionChange(editor: FileEditor): Unit =
    if (isHighlightingEnabled) {
      val fileName = Option(editor.getFile).map(_.getName).getOrElse("<no file>")
      val debugReason = s"selected editor changed: $fileName"
      for {
        virtualFile <- editor.getFile.nullSafe
        psiFile <- inReadAction(PsiManager.getInstance(project).findFile(virtualFile)).nullSafe
        if isHighlightingEnabledFor(psiFile, virtualFile) && !hasErrors(psiFile)
        pathString <- virtualFile.getCanonicalPath.nullSafe
        document <- inReadAction(virtualFile.findDocument)
      } asyncAtomic {
        val path = Paths.get(pathString)
        if (changedFiles.nonEmpty) {
          triggerIncrementalCompilation(debugReason, modulesForFiles(changedFiles)(project))
        }
        else if (!alreadyHighlighted.contains(path)) {
          psiFile match {
            case scalaFile: ScalaFile =>
              if (scalaFile.isWorksheetFile) triggerWorksheetCompilation(scalaFile, document, markHighlighted = Some(path))
              else triggerDocumentCompilation(debugReason, document, scalaFile, markHighlighted = Some(path))
            case _ =>
          }
        }
      }
    }

  override def dispose(): Unit = {
    synchronized {
      changedFiles = Set.empty
      alreadyHighlighted = Set.empty
    }
    threadPool.shutdownNow()
  }

  @TestOnly
  var isAutoTriggerEnabled: Boolean =
    !ApplicationManager.getApplication.isUnitTestMode

  private def isHighlightingEnabled: Boolean = {
    isAutoTriggerEnabled &&
      !PowerSaveMode.isEnabled &&
      ScalaCompileServerSettings.getInstance.COMPILE_SERVER_ENABLED &&
      ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)
  }

  private def isHighlightingEnabledFor(psiFile: PsiFile, virtualFile: VirtualFile): Boolean = {
    lazy val isJavaOrScalaFile = psiFile match {
      case _ if psiFile.isScalaWorksheet => true
      case _: ScalaFile | _: PsiJavaFile if !JavaProjectRootsUtil.isOutsideJavaSourceRoot(psiFile) => true
      case _ => false
    }
    virtualFile.isInLocalFileSystem && isJavaOrScalaFile
  }

  private def triggerIncrementalCompilation(debugReason: String, modules: Seq[Module]): Unit = {
    if (showErrorsFromCompilerEnabledAtLeastForOneOpenEditor.isDefined)
      CompilerHighlightingService.get(project).triggerIncrementalCompilation(debugReason, modules)
  }

  // SCL-18946
  def showErrorsFromCompilerEnabledAtLeastForOneOpenEditor: Option[FileEditor] = {
    val psiManager = PsiManager.getInstance(project)

    def isEnabledFor(editor: FileEditor): Boolean = {
      val isShowErrorsFromCompilerEnabled = for {
        virtualFile <- Option(editor.getFile)
        psiFile <- Option(inReadAction(psiManager.findFile(virtualFile)))
      } yield ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(psiFile)
      isShowErrorsFromCompilerEnabled.getOrElse(false)
    }

    val openEditors = FileEditorManager.getInstance(project).getAllEditors
    openEditors.find(isEnabledFor)
  }

  def beforeIncrementalCompilation(): Unit = {
    changedFiles
      .flatMap { virtualFile => inReadAction(virtualFile.findDocument) }
      .foreach(_.syncToDisk(project))
    changedFiles = Set.empty
  }

  def afterIncrementalCompilation(): Unit = {
    alreadyHighlighted = Set.empty
    DocumentCompiler.get(project).clearOutputDirectories()
    FileEditorManager.getInstance(project).getSelectedEditor.nullSafe
      .foreach(triggerOnSelectionChange)
  }

  private def triggerDocumentCompilation(
    debugReason: String,
    document: Document,
    psiFile: ScalaFile,
    markHighlighted: Option[Path]
  ): Unit =
    if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(psiFile))
      CompilerHighlightingService.get(project).triggerDocumentCompilation(
        debugReason,
        document,
        afterCompilation = () => mark(markHighlighted)
      )

  private def triggerWorksheetCompilation(psiFile: ScalaFile,
                                          document: Document,
                                          markHighlighted: Option[Path]): Unit =
    CompilerHighlightingService.get(project).triggerWorksheetCompilation(
      psiFile = psiFile,
      document = document,
      afterCompilation = () => mark(markHighlighted)
    )

  private def mark(pathOption: Option[Path]): Unit = pathOption.foreach { path =>
    synchronized {
      alreadyHighlighted += path
    }
  }

  private def asyncAtomic(action: => Unit): Unit =
    Future(synchronized(action))
}

object TriggerCompilerHighlightingService {

  def get(project: Project): TriggerCompilerHighlightingService =
    project.getService(classOf[TriggerCompilerHighlightingService])

  private def hasErrors(psiFile: PsiFile): Boolean =
    psiFile.elements.findByType[PsiErrorElement].isDefined

  private def modulesForFiles(virtualFiles: Set[VirtualFile])(implicit project: Project): Seq[Module] =
    inReadAction(virtualFiles.flatMap(ScalaUtil.getModuleForFile).toSeq)
}
