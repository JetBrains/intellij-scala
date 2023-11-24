package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.codeInsight.daemon.impl.analysis.{FileHighlightingSetting, FileHighlightingSettingListener}
import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.{Document, EditorFactory}
import com.intellij.openapi.fileEditor.{FileDocumentManager, FileEditorManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{JavaProjectRootsUtil, ProjectRootManager, TestSourcesFilter}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi._
import org.jetbrains.jps.incremental.scala.remote.SourceScope
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.compiler.highlighting.BackgroundExecutorService.executeOnBackgroundThreadInNotDisposed
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings.{ScalaCompileServerSettings, ScalaHighlightingMode}

import scala.collection.concurrent.TrieMap
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

@Service(Array(Service.Level.PROJECT))
private[scala] final class TriggerCompilerHighlightingService(project: Project) extends Disposable {

  import TriggerCompilerHighlightingService._

  private val documentCompilerAvailable: TrieMap[VirtualFile, java.lang.Boolean] = TrieMap.empty

  project.getMessageBus.connect(this).subscribe[FileHighlightingSettingListener](
    FileHighlightingSettingListener.SETTING_CHANGE,
    (root: PsiElement, _: FileHighlightingSetting) => {
      if (root.getLanguage.isKindOf(ScalaLanguage.INSTANCE)) {
        executeOnBackgroundThreadInNotDisposed(project) {
          val psiFile = inReadAction(root.getContainingFile)
          if (psiFile ne null) {
            val virtualFile = psiFile.getVirtualFile
            if ((virtualFile ne null) && virtualFile.isValid) { //file could be deleted (this code is called in background activity)
              val document = inReadAction(FileDocumentManager.getInstance().getDocument(virtualFile))
              invokeAndWait {
                EditorFactory.getInstance().getEditors(document).foreach { editor =>
                  UpdateHighlightersUtil.setHighlightersToEditor(
                    project, document,
                    0, document.getTextLength, Seq.empty.asJava,
                    editor.getColorsScheme, ExternalHighlightersService.ScalaCompilerPassId)
                }
              }
              executeOnBackgroundThreadInNotDisposed(project) {
                if (virtualFile.isValid) { //file could be deleted (this code is called in background activity)
                  WolfTheProblemSolver.getInstance(project).clearProblemsFromExternalSource(virtualFile, ExternalHighlightersService.instance(project))
                }
              }

              if (isHighlightingEnabled && isHighlightingEnabledFor(psiFile, virtualFile)) {
                val debugReason = s"FileHighlightingSetting changed for ${virtualFile.getCanonicalPath}"
                if (psiFile.isScalaWorksheet)
                  doTriggerWorksheetCompilation(virtualFile, psiFile.asInstanceOf[ScalaFile], document, debugReason)
                else
                  doTriggerIncrementalCompilation(debugReason, virtualFile, document, psiFile)
              }
            }
          }
        }
      }
    }
  )

  private[highlighting] def triggerOnFileChange(psiFile: PsiFile, virtualFile: VirtualFile): Unit = executeOnBackgroundThreadInNotDisposed(project) {
    //file could be deleted (this code is called in background activity)
    if (isHighlightingEnabled && virtualFile.isValid && isHighlightingEnabledFor(psiFile, virtualFile) && !hasErrors(psiFile)) {
      val debugReason = s"file content changed: ${psiFile.name}"
      val document = inReadAction(FileDocumentManager.getInstance().getDocument(virtualFile))
      if (document ne null) {
        if (psiFile.isScalaWorksheet) {
          doTriggerWorksheetCompilation(virtualFile, psiFile.asInstanceOf[ScalaFile], document, debugReason)
        } else {
          if (documentCompilerAvailable.contains(virtualFile)) {
            doTriggerDocumentCompilation(virtualFile, document, debugReason)
          } else {
            doTriggerIncrementalCompilation(debugReason, virtualFile, document, psiFile)
          }
        }
      }
    }
  }

  private[highlighting] def triggerOnEditorFocus(virtualFile: VirtualFile): Unit = executeOnBackgroundThreadInNotDisposed(project) {
    //file could be deleted (this code is called in background activity)
    if (isHighlightingEnabled && ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project) && virtualFile.isValid) {
      val psiFile = inReadAction(PsiManager.getInstance(project).findFile(virtualFile))
      if ((psiFile ne null) && isHighlightingEnabledFor(psiFile, virtualFile) && !hasErrors(psiFile)) {
        val document = inReadAction(FileDocumentManager.getInstance().getDocument(virtualFile))
        if (document ne null) {
          val debugReason = s"focused editor changed: ${virtualFile.getName}"
          if (psiFile.isScalaWorksheet)
            doTriggerWorksheetCompilation(virtualFile, psiFile.asInstanceOf[ScalaFile], document, debugReason)
          else
            doTriggerIncrementalCompilation(debugReason, virtualFile, document, psiFile)
        }
      }
    }
  }

  override def dispose(): Unit = {
    documentCompilerAvailable.clear()
  }

  private def isHighlightingEnabled: Boolean =
    !PowerSaveMode.isEnabled && ScalaCompileServerSettings.getInstance.COMPILE_SERVER_ENABLED

  private def isHighlightingEnabledFor(psiFile: PsiFile, virtualFile: VirtualFile): Boolean = inReadAction {
    ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(psiFile) &&
      virtualFile.isInLocalFileSystem &&
      (psiFile match {
        case _ if psiFile.isScalaWorksheet => true
        case _: ScalaFile | _: PsiJavaFile if !JavaProjectRootsUtil.isOutsideJavaSourceRoot(psiFile) => true
        case _ => false
      }) &&
      ScalaHighlightingMode.shouldHighlightBasedOnFileLevel(psiFile, project)
  }

  private def doTriggerIncrementalCompilation(debugReason: String, virtualFile: VirtualFile, document: Document, psiFile: PsiFile): Unit = {
    val module = inReadAction(ProjectRootManager.getInstance(project).getFileIndex.getModuleForFile(virtualFile))
    if (module ne null) {
      val sourceScope = calculateSourceScope(virtualFile)
      CompilerHighlightingService.get(project)
        .triggerIncrementalCompilation(virtualFile, module, sourceScope, document, psiFile, debugReason)
    }
  }

  def beforeIncrementalCompilation(): Unit = invokeAndWait {
    val fileDocumentManager = FileDocumentManager.getInstance()
    val psiDocumentManager = PsiDocumentManager.getInstance(project)
    val unsaved = fileDocumentManager.getUnsavedDocuments
    unsaved.filter(psiDocumentManager.getPsiFile(_) ne null).foreach { document =>
      try fileDocumentManager.saveDocumentAsIs(document)
      catch {
        case NonFatal(_) =>
      }
    }
  }

  def enableDocumentCompiler(virtualFile: VirtualFile): Unit = {
    if (project.isDisposed || !virtualFile.isValid) return
    val selectedEditor = FileEditorManager.getInstance(project).getSelectedEditor
    if (selectedEditor eq null) return
    if (virtualFile == selectedEditor.getFile) {
      DocumentCompiler.get(project).clearOutputDirectories()
      documentCompilerAvailable.put(virtualFile, java.lang.Boolean.TRUE)
    }
  }

  def disableDocumentCompiler(virtualFile: VirtualFile): Unit = {
    documentCompilerAvailable.remove(virtualFile, java.lang.Boolean.TRUE)
  }

  private def doTriggerDocumentCompilation(
    virtualFile: VirtualFile,
    document: Document,
    debugReason: String
  ): Unit = {
    val module = inReadAction(ProjectRootManager.getInstance(project).getFileIndex.getModuleForFile(virtualFile))
    if (module ne null) {
      val sourceScope = calculateSourceScope(virtualFile)
      CompilerHighlightingService.get(project).triggerDocumentCompilation(virtualFile, module, sourceScope, document, debugReason)
    }
  }

  private def doTriggerWorksheetCompilation(
    virtualFile: VirtualFile,
    psiFile: ScalaFile,
    document: Document,
    debugReason: String
  ): Unit =
    CompilerHighlightingService.get(project).triggerWorksheetCompilation(
      virtualFile,
      psiFile,
      document,
      isFirstTimeHighlighting = !documentCompilerAvailable.contains(virtualFile),
      debugReason
    )

  private def calculateSourceScope(file: VirtualFile): SourceScope =
    if (TestSourcesFilter.isTestSources(file, project)) SourceScope.Test
    else SourceScope.Production
}

private[scala] object TriggerCompilerHighlightingService {

  def get(project: Project): TriggerCompilerHighlightingService =
    project.getService(classOf[TriggerCompilerHighlightingService])

  private def hasErrors(psiFile: PsiFile): Boolean = inReadAction {
    psiFile.elements.findByType[PsiErrorElement].isDefined
  }
}
