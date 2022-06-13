package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.fileEditor.{FileDocumentManager, FileEditor, FileEditorManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{JavaProjectRootsUtil, TestSourcesFilter}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiErrorElement, PsiFile, PsiJavaFile, PsiManager}
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.incremental.scala.remote.SourceScope
import org.jetbrains.plugins.scala.compiler.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, ObjectExt, PsiElementExt, PsiFileExt, ToNullSafe, inReadAction}
import org.jetbrains.plugins.scala.externalHighlighters.TriggerCompilerHighlightingService.hasErrors
import org.jetbrains.plugins.scala.externalHighlighters.compiler.DocumentCompiler
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.VirtualFileExt
import org.jetbrains.plugins.scala.util.ScalaUtil

import scala.concurrent.{ExecutionContext, Future}

@Service
final class TriggerCompilerHighlightingService(project: Project)
  extends Disposable {

  private val threadPool = AppExecutorUtil.createBoundedApplicationPoolExecutor("TriggerCompilerHighlighting", 1)
  private implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(threadPool)

  def triggerOnFileChange(psiFile: PsiFile, virtualFile: VirtualFile): Unit =
    if (isHighlightingEnabled && isHighlightingEnabledFor(psiFile, virtualFile) && !hasErrors(psiFile))
      virtualFile.findDocument.foreach { document =>
        val scalaFile = psiFile.asOptionOf[ScalaFile]
        val debugReason = s"file content changed: ${psiFile.getName}"
        Future {
          if (psiFile.isScalaWorksheet)
            scalaFile.foreach(triggerWorksheetCompilation(_, document))
          else if (ScalaHighlightingMode.documentCompilerEnabled)
            scalaFile.foreach(triggerDocumentCompilation(debugReason, document, _))
          else
            triggerIncrementalCompilation(debugReason, virtualFile, document)
        }
      }

  def triggerOnSelectionChange(editor: FileEditor): Unit =
    if (ScalaHighlightingMode.documentCompilerEnabled && isHighlightingEnabled) {
      val fileName = Option(editor.getFile).map(_.getName).getOrElse("<no file>")
      val debugReason = s"selected editor changed: $fileName"
      for {
        virtualFile <- editor.getFile.nullSafe
        psiFile <- inReadAction(PsiManager.getInstance(project).findFile(virtualFile)).nullSafe
        if isHighlightingEnabledFor(psiFile, virtualFile) && !hasErrors(psiFile)
        document <- inReadAction(virtualFile.findDocument)
        scalaFile <- psiFile.asOptionOf[ScalaFile]
      } Future {
        if (psiFile.isScalaWorksheet)
          triggerWorksheetCompilation(scalaFile, document)
        else
          triggerIncrementalCompilation(debugReason, virtualFile, document)
      }
    }

  private[externalHighlighters] def triggerOnEditorCreated(editor: Editor): Unit =
    if (!ScalaHighlightingMode.documentCompilerEnabled && isHighlightingEnabled) {
      val document = editor.getDocument
      for {
        virtualFile <- FileDocumentManager.getInstance().getFile(document).nullSafe
        debugReason = s"Editor created for file: ${virtualFile.getCanonicalPath}"
        psiFile <- inReadAction(PsiManager.getInstance(project).findFile(virtualFile)).nullSafe
        if isHighlightingEnabledFor(psiFile, virtualFile) && !hasErrors(psiFile)
        scalaFile <- psiFile.asOptionOf[ScalaFile]
      } Future {
        if (psiFile.isScalaWorksheet)
          triggerWorksheetCompilation(scalaFile, document)
        else
          triggerIncrementalCompilation(debugReason, virtualFile, document)
      }
    }

  override def dispose(): Unit = {
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

  private def triggerIncrementalCompilation(debugReason: String, virtualFile: VirtualFile, document: Document): Unit = {
    val module = ScalaUtil.getModuleForFile(virtualFile)(project)
    val sourceScope =
      if (TestSourcesFilter.isTestSources(virtualFile, project)) SourceScope.Test
      else SourceScope.Production

    module.foreach { m =>
      CompilerHighlightingService.get(project).triggerIncrementalCompilation(debugReason, m, sourceScope, () => document.syncToDisk(project))
    }
  }

  def afterIncrementalCompilation(): Unit = {
    if (ScalaHighlightingMode.documentCompilerEnabled) {
      DocumentCompiler.get(project).clearOutputDirectories()
    }
  }

  private def triggerDocumentCompilation(
    debugReason: String,
    document: Document,
    psiFile: ScalaFile
  ): Unit =
    if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(psiFile))
      CompilerHighlightingService.get(project).triggerDocumentCompilation(
        debugReason,
        document
      )

  private def triggerWorksheetCompilation(psiFile: ScalaFile,
                                          document: Document): Unit =
    CompilerHighlightingService.get(project).triggerWorksheetCompilation(
      psiFile = psiFile,
      document = document
    )
}

object TriggerCompilerHighlightingService {

  def get(project: Project): TriggerCompilerHighlightingService =
    project.getService(classOf[TriggerCompilerHighlightingService])

  private def hasErrors(psiFile: PsiFile): Boolean =
    psiFile.elements.findByType[PsiErrorElement].isDefined
}
