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
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.VirtualFileExt
import org.jetbrains.plugins.scala.util.ScalaUtil

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}

@Service
final class TriggerCompilerHighlightingService(project: Project)
  extends Disposable {

  import TriggerCompilerHighlightingService.modulesForFiles

  private val modifiedFiles: TrieMap[VirtualFile, java.lang.Boolean] = TrieMap.empty

  private val threadPool = AppExecutorUtil.createBoundedApplicationPoolExecutor("TriggerCompilerHighlighting", 1)
  private implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(threadPool)

  def triggerOnFileChange(psiFile: PsiFile, virtualFile: VirtualFile): Unit =
    if (isHighlightingEnabled && isHighlightingEnabledFor(psiFile, virtualFile) && !hasErrors(psiFile))
      virtualFile.findDocument.foreach { document =>
        val scalaFile = psiFile.asOptionOf[ScalaFile]
        val debugReason = s"file content changed: ${psiFile.getName}"
        if (psiFile.isScalaWorksheet)
          scalaFile.foreach(triggerWorksheetCompilation(_, document))
        else Future {
          triggerSingleFileCompilation(debugReason, virtualFile)
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
        document <- inReadAction(virtualFile.findDocument)
      } Future {
        if (psiFile.isScalaWorksheet)
          triggerWorksheetCompilation(psiFile.asInstanceOf[ScalaFile], document)
        else {
          registerAsModified(virtualFile)
          triggerIncrementalCompilation(debugReason, modulesForFiles(modifiedFiles.keys.toSet)(project))
        }
      }
    }

  override def dispose(): Unit = {
    modifiedFiles.clear()
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
      CompilerHighlightingService.get(project).triggerIncrementalCompilation(debugReason, modules, beforeIncrementalCompilation, afterIncrementalCompilation)
  }

  private def triggerSingleFileCompilation(debugReason: String, virtualFile: VirtualFile): Unit = {
    if (showErrorsFromCompilerEnabledAtLeastForOneOpenEditor.isDefined) {
      val filePath = virtualFile.toNioPath
      CompilerHighlightingService.get(project).triggerSingleFileCompilation(debugReason, filePath, beforeIncrementalCompilation, () => registerAsModified(virtualFile))
    }
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
    modifiedFiles
      .keys
      .flatMap { virtualFile => inReadAction(virtualFile.findDocument) }
      .foreach(_.syncToDisk(project))
  }

  def afterIncrementalCompilation(): Unit = {
    modifiedFiles.clear()
  }

  private def registerAsModified(vf: VirtualFile): Unit = {
    modifiedFiles.put(vf, java.lang.Boolean.TRUE)
  }

  private def triggerWorksheetCompilation(psiFile: ScalaFile,
                                          document: Document): Unit =
    CompilerHighlightingService.get(project).triggerWorksheetCompilation(psiFile, document)
}

object TriggerCompilerHighlightingService {

  def get(project: Project): TriggerCompilerHighlightingService =
    project.getService(classOf[TriggerCompilerHighlightingService])

  private def hasErrors(psiFile: PsiFile): Boolean =
    psiFile.elements.findByType[PsiErrorElement].isDefined

  private def modulesForFiles(virtualFiles: Set[VirtualFile])(implicit project: Project): Seq[Module] =
    inReadAction(virtualFiles.flatMap(ScalaUtil.getModuleForFile).toSeq)
}
