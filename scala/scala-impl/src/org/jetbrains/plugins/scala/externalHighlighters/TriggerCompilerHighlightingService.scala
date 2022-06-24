package org.jetbrains.plugins.scala
package externalHighlighters

import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.fileEditor.{FileDocumentManager, FileEditor}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{JavaProjectRootsUtil, TestSourcesFilter}
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.psi.{PsiErrorElement, PsiFile, PsiJavaFile, PsiManager}
import org.jetbrains.jps.incremental.scala.remote.SourceScope
import org.jetbrains.plugins.scala.compiler.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.externalHighlighters.TriggerCompilerHighlightingService.hasErrors
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.VirtualFileExt
import org.jetbrains.plugins.scala.util.ScalaUtil

import java.nio.file.Path
import scala.collection.concurrent.TrieMap
import scala.util.control.NonFatal

@Service
private[scala] final class TriggerCompilerHighlightingService(project: Project) extends Disposable {

  private val documentCompilerAvailable: TrieMap[Path, java.lang.Boolean] = TrieMap.empty

  def triggerOnFileChange(psiFile: PsiFile, virtualFile: VirtualFile): Unit =
    if (isHighlightingEnabled && isHighlightingEnabledFor(psiFile, virtualFile) && !hasErrors(psiFile))
      virtualFile.findDocument.foreach { document =>
        val scalaFile = psiFile.asOptionOf[ScalaFile]
        val debugReason = s"file content changed: ${psiFile.name}"
        if (psiFile.isScalaWorksheet)
          scalaFile.foreach(triggerWorksheetCompilation(_, document, debugReason))
        else if (ScalaHighlightingMode.documentCompilerEnabled) {
          scalaFile.foreach { f =>
            if (documentCompilerAvailable.contains(virtualFile.toNioPath)) {
              triggerDocumentCompilation(debugReason, document, f)
            } else {
              triggerIncrementalCompilation(debugReason, virtualFile)
            }
          }
        } else {
          triggerIncrementalCompilation(debugReason, virtualFile)
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
      } {
        if (psiFile.isScalaWorksheet)
          triggerWorksheetCompilation(scalaFile, document, debugReason)
        else
          triggerIncrementalCompilation(debugReason, virtualFile)
      }
    }

  override def dispose(): Unit = {
    documentCompilerAvailable.clear()
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
      } {
        if (psiFile.isScalaWorksheet)
          triggerWorksheetCompilation(scalaFile, document, debugReason)
        else
          triggerIncrementalCompilation(debugReason, virtualFile)
      }
    }

  private[scala] var isAutoTriggerEnabled: Boolean =
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

  private def triggerIncrementalCompilation(debugReason: String, virtualFile: VirtualFile): Unit = {
    val module = ScalaUtil.getModuleForFile(virtualFile)(project)
    val sourceScope =
      if (TestSourcesFilter.isTestSources(virtualFile, project)) SourceScope.Test
      else SourceScope.Production

    module.foreach { m =>
      CompilerHighlightingService.get(project).triggerIncrementalCompilation(virtualFile.toNioPath, m, sourceScope, debugReason)
    }
  }

  def beforeIncrementalCompilation(): Unit = invokeAndWait {
    val manager = FileDocumentManager.getInstance()
    val unsaved = try manager.getUnsavedDocuments catch { case NonFatal(_) => Array.empty[Document] }
    unsaved.foreach { document =>
      if (manager.getFile(document).isValid) {
        try manager.saveDocumentAsIs(document)
        catch {
          case NonFatal(_) =>
        }
      }
    }
  }

  def enableDocumentCompiler(path: Path): Unit = {
    if (ScalaHighlightingMode.documentCompilerEnabled) {
      inWriteAction(LocalFileSystem.getInstance().refresh(false))
      documentCompilerAvailable.put(path, java.lang.Boolean.TRUE)
    }
  }

  def disableDocumentCompiler(path: Path): Unit = {
    if (ScalaHighlightingMode.documentCompilerEnabled) {
      documentCompilerAvailable.remove(path, java.lang.Boolean.TRUE)
    }
  }

  private def triggerDocumentCompilation(
    debugReason: String,
    document: Document,
    psiFile: ScalaFile
  ): Unit =
    if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(psiFile))
      CompilerHighlightingService.get(project).triggerDocumentCompilation(
        psiFile.getVirtualFile.toNioPath,
        document,
        debugReason
      )

  private def triggerWorksheetCompilation(psiFile: ScalaFile,
                                          document: Document,
                                          debugReason: String): Unit =
    CompilerHighlightingService.get(project).triggerWorksheetCompilation(
      psiFile.getVirtualFile.toNioPath,
      psiFile,
      document,
      debugReason
    )
}

private[scala] object TriggerCompilerHighlightingService {

  def get(project: Project): TriggerCompilerHighlightingService =
    project.getService(classOf[TriggerCompilerHighlightingService])

  private def hasErrors(psiFile: PsiFile): Boolean =
    psiFile.elements.findByType[PsiErrorElement].isDefined
}
