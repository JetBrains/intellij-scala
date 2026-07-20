package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.codeInsight.daemon.impl.analysis.{FileHighlightingSetting, FileHighlightingSettingListener}
import com.intellij.ide.PowerSaveMode
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.{Document, EditorFactory}
import com.intellij.openapi.fileEditor.{FileDocumentManager, FileEditorManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{JavaProjectRootsUtil, ProjectRootManager}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.*
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.compiler.highlighting.BackgroundExecutorService.executeOnBackgroundThreadInNotDisposed
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.{DocumentSaveFailPhaseEvent, DocumentSavePhaseEvent, HighlightingTriggerPhaseEvent, RequestId}
import org.jetbrains.plugins.scala.compiler.tracing.Tracing
import org.jetbrains.plugins.scala.compiler.tracing.core.events.EndEvent
import org.jetbrains.plugins.scala.extensions.*
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings.{ScalaCompileServerSettings, ScalaHighlightingMode}

import scala.annotation.nowarn
import scala.collection.concurrent.TrieMap
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

@Service(Array(Service.Level.PROJECT))
private[scala] final class TriggerCompilerHighlightingService(project: Project) extends Disposable {

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
                    editor.getColorsScheme, ExternalHighlightersService.ScalaCompilerPassId): @nowarn("cat=deprecation")
                }
              }
              executeOnBackgroundThreadInNotDisposed(project) {
                if (virtualFile.isValid) { //file could be deleted (this code is called in background activity)
                  WolfTheProblemSolver.getInstance(project).clearProblemsFromExternalSource(virtualFile, ExternalHighlightersService.instance(project))
                }
              }

              if (isHighlightingEnabled && isHighlightingEnabledFor(psiFile, virtualFile)) {
                val debugReason = s"FileHighlightingSetting changed for ${virtualFile.getCanonicalPath}"
                val id = TriggerPhaseEvents.newRequestId()
                Tracing(project).instant(HighlightingTriggerPhaseEvent(id, debugReason))
                val dispatched =
                  if (psiFile.isScalaWorksheet)
                    doTriggerWorksheetCompilation(virtualFile, psiFile.asInstanceOf[ScalaFile], document, debugReason, id)
                  else
                    doTriggerIncrementalCompilation(debugReason, virtualFile, document, psiFile, id)
                if (!dispatched) Tracing(project).instant(EndEvent(id, "no compilation scheduled"))
              }
            }
          }
        }
      }
    }
  )

  private[highlighting] def triggerOnFileChange(psiFile: PsiFile, virtualFile: VirtualFile,
                                                requestId: RequestId): Unit = executeOnBackgroundThreadInNotDisposed(project) {
    //file could be deleted (this code is called in background activity)
    val process = isHighlightingEnabled &&
      !virtualFile.isInstanceOf[VirtualFileWindow] && //injected fragments
      virtualFile.isValid &&
      isHighlightingEnabledFor(psiFile, virtualFile)
    val dispatched = process && {
      val debugReason = s"file content changed: ${psiFile.name}"
      val document = inReadAction(FileDocumentManager.getInstance().getDocument(virtualFile))
      (document ne null) && {
        if (psiFile.isScalaWorksheet)
          doTriggerWorksheetCompilation(virtualFile, psiFile.asInstanceOf[ScalaFile], document, debugReason, requestId)
        else if (documentCompilerAvailable.contains(virtualFile))
          doTriggerDocumentCompilation(virtualFile, document, psiFile, debugReason, requestId)
        else
          doTriggerIncrementalCompilation(debugReason, virtualFile, document, psiFile, requestId)
      }
    }
    // Close the trigger span if nothing was scheduled (file deleted/ineligible, or no module), so it doesn't leak.
    if (!dispatched) Tracing(project).instant(EndEvent(requestId, "no compilation scheduled for file change"))
  }

  private[highlighting] def triggerOnEditorFocus(virtualFile: VirtualFile, request: RequestId): Unit = executeOnBackgroundThreadInNotDisposed(project) {
    //file could be deleted (this code is called in background activity)
    val dispatched =
      isHighlightingEnabled && ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project) && virtualFile.isValid && {
        val psiFile = inReadAction(PsiManager.getInstance(project).findFile(virtualFile))
        (psiFile ne null) && isHighlightingEnabledFor(psiFile, virtualFile) && {
          val document = inReadAction(FileDocumentManager.getInstance().getDocument(virtualFile))
          (document ne null) && {
            val debugReason = s"focused editor changed: ${virtualFile.getName}"
            if (psiFile.isScalaWorksheet)
              doTriggerWorksheetCompilation(virtualFile, psiFile.asInstanceOf[ScalaFile], document, debugReason, request)
            else
              doTriggerIncrementalCompilation(debugReason, virtualFile, document, psiFile, request)
          }
        }
      }
    // The trigger span is registered under `request`; if nothing was scheduled, no phase span will consume it,
    // so close it explicitly.
    if (!dispatched) Tracing(project).instant(EndEvent(request, "focused editor not eligible for compilation"))
  }

  private[highlighting] def triggerCompilationInSelectedEditor(requestId: RequestId): Unit = executeOnBackgroundThreadInNotDisposed(project) {
    // Disable the document compiler.
    documentCompilerAvailable.clear()
    // Find an active editor and start a compilation from that file. If no editors are open, the next compilation will
    // be scheduled the next time the user opens a source file.
    Option(FileEditorManager.getInstance(project).getSelectedEditor)
      .flatMap(editor => Option(editor.getFile))
      .fold(Tracing(project).instant(EndEvent(requestId, "No editor selected")))(triggerOnEditorFocus(_, requestId))
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

  /** @return `true` if a compilation was scheduled for `request` (i.e. its trigger span will be consumed). */
  private def doTriggerIncrementalCompilation(debugReason: String, virtualFile: VirtualFile, document: Document,
                                              psiFile: PsiFile, request: RequestId): Boolean = {
    val module = inReadAction(ProjectRootManager.getInstance(project).getFileIndex.getModuleForFile(virtualFile))
    if (module ne null) {
      CompilerHighlightingService.get(project)
        .triggerIncrementalCompilation(virtualFile, module, document, psiFile, debugReason, request)
      true
    } else false
  }

  @RequiresEdt
  def beforeIncrementalCompilation(request: RequestId): Unit = {
    val span = Tracing(project).begin(DocumentSavePhaseEvent(request))
    val fileDocumentManager = FileDocumentManager.getInstance()
    val psiDocumentManager = PsiDocumentManager.getInstance(project)
    def name(document: Document): String =
      Option(fileDocumentManager.getFile(document)).fold(document.toString)(_.getName)
    val toSave = fileDocumentManager.getUnsavedDocuments.filter(psiDocumentManager.getPsiFile(_) ne null)
    val requested = toSave.map(name).toSet
    var saved = Set.empty[String]
    toSave.foreach { document =>
      val fileName = name(document)
      try {
        fileDocumentManager.saveDocumentAsIs(document)
        saved += fileName
      }
      catch {
        case NonFatal(_) => Tracing(project).mark(span, DocumentSaveFailPhaseEvent(request, fileName))
      }
    }
    Tracing(project).mapAndEnd(span)(_ => Some(DocumentSavePhaseEvent(request, requested, saved)))
  }

  def enableDocumentCompiler(virtualFile: VirtualFile): Unit = {
    if (project.isDisposed) return
    if (!virtualFile.isValid) return
    val selectedEditor = FileEditorManager.getInstance(project).getSelectedEditor
    if (selectedEditor eq null) return
    if (virtualFile == selectedEditor.getFile) {
      documentCompilerAvailable.put(virtualFile, java.lang.Boolean.TRUE)
    }
  }

  def disableDocumentCompiler(virtualFile: VirtualFile): Unit = {
    documentCompilerAvailable.remove(virtualFile, java.lang.Boolean.TRUE)
  }

  /** @return `true` if a compilation was scheduled for `requestId` (i.e. its trigger span will be consumed). */
  private def doTriggerDocumentCompilation(
    virtualFile: VirtualFile,
    document: Document,
    psiFile: PsiFile,
    debugReason: String,
    requestId: RequestId
  ): Boolean = {
    val module = inReadAction(ProjectRootManager.getInstance(project).getFileIndex.getModuleForFile(virtualFile))
    if (module ne null) {
      CompilerHighlightingService.get(project).triggerDocumentCompilation(virtualFile, module, document, psiFile, debugReason, requestId)
      true
    } else false
  }

  /** @return always `true`: a worksheet compilation is always scheduled (its trigger span will be consumed). */
  private def doTriggerWorksheetCompilation(
    virtualFile: VirtualFile,
    psiFile: ScalaFile,
    document: Document,
    debugReason: String,
    request: RequestId
  ): Boolean = {
    CompilerHighlightingService.get(project).triggerWorksheetCompilation(
      virtualFile,
      psiFile,
      document,
      isFirstTimeHighlighting = !documentCompilerAvailable.contains(virtualFile),
      debugReason,
      request
    )
    true
  }
}

private[scala] object TriggerCompilerHighlightingService {

  def get(project: Project): TriggerCompilerHighlightingService =
    project.getService(classOf[TriggerCompilerHighlightingService])
}
