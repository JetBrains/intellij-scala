package org.jetbrains.plugins.scala.compiler.highlighting.services

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.openapi.application.{ApplicationManager, ModalityState, ReadAction}
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiManager}
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.jps.incremental.scala.Client.PosInfo
import org.jetbrains.plugins.scala.compiler.highlighting.core.HighlightingState
import org.jetbrains.plugins.scala.compiler.highlighting.events.HighlightingPhaseEvents.HighlightingEvent
import org.jetbrains.plugins.scala.compiler.highlighting.listeners.ExternalHighlightingAppliedListener
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.{ExternalHighlightingFixProvider, ExternalHighlightingUpdater, HighlightInfoFactory, HighlightingRangeCalculator}
import org.jetbrains.plugins.scala.compiler.highlighting.util.DocumentUtil
import org.jetbrains.plugins.scala.compiler.tracing.Tracing
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.CompilerType
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode
import org.jetbrains.plugins.scala.util.CompilationId

import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean
import scala.util.control.NonFatal

/**
 * Orchestrates the application of external compiler diagnostics to the workspace.
 *
 * This service manages the asynchronous lifecycle, thread boundaries, and state mapping
 * required to retrieve raw compiler data, transform it into IDE-compatible structures,
 * and render those structures onto the active interface safely.
 */
@Service(Array(Service.Level.PROJECT))
private[highlighting] final class ExternalHighlightersService(project: Project) {
  self =>

  import ExternalHighlightersService.{HighlightInfoData, HighlightingData}

  private val errorTypes: Set[HighlightInfoType] = Set(HighlightInfoType.ERROR, HighlightInfoType.WRONG_REF)

  private val rangeCalculator = new HighlightingRangeCalculator()
  private val fixProvider = new ExternalHighlightingFixProvider(project)
  private val highlightInfoFactory = new HighlightInfoFactory(project, rangeCalculator, fixProvider)
  private val updater = new ExternalHighlightingUpdater(project, self)
  private val tracer = Tracing(project)
  
  private def notifyHighlightingApplied(virtualFiles: Set[VirtualFile]): Unit =
    BackgroundExecutorService.executeOnBackgroundThreadInNotDisposed(project) {
      project.getMessageBus
        .syncPublisher(ExternalHighlightingAppliedListener.topic)
        .highlightingApplied(virtualFiles)
    }

  def applyHighlightingState(virtualFiles: Set[VirtualFile], state: HighlightingState, compilationId: CompilationId): Unit = {
    if (project.isDisposed) return
    val span = tracer.begin(HighlightingEvent(compilationId))

    val readActionCallable: Callable[HighlightInfoData] = { () =>
      val filteredVirtualFiles = filterFilesToHighlightBasedOnFileLevel(virtualFiles)
      val psiManager = PsiManager.getInstance(project)
      val data = for {
        editor <- EditorFactory.getInstance().getAllEditors.toSeq if !project.isDisposed
        editorProject <- Option(editor.getProject) if editorProject == project
        document = editor.getDocument
        virtualFile <- document.virtualFile if filteredVirtualFiles.contains(virtualFile)
        psiFile <- Option(psiManager.findFile(virtualFile)) if ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(psiFile)
      } yield {
        val externalHighlights = state.externalHighlightings(virtualFile)
        val highlightInfos = highlightInfoFactory.calculateHighlightInfos(externalHighlights, document, psiFile, compilationId)
        HighlightingData(editor, document, psiFile, virtualFile, highlightInfos)
      }
      val errorFiles = filterFilesToHighlightBasedOnFileLevel(state.filesWithHighlightings(errorTypes))

      var elements = Vector.empty[PsiElement]
      // Add types only to a file opened in the current editor
      // In principle, JPS can process arbitrary files (currently disabled) unless we implement a filter (see CompilerDataFactory.scalaOptionsFor)
      // Can be extended to all opened editors
      Option(FileEditorManager.getInstance(project).getFocusedEditor).foreach { editor =>
        state.externalTypes(editor.getFile).foreach { case ((begin, end), tpe) =>
          val psiFile = PsiManager.getInstance(project).findFile(editor.getFile)
          val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)

          def toOffset(pos: PosInfo): Int = document.getLineStartOffset(pos.line - 1) + pos.column - 1

          val range = new TextRange(toOffset(begin), toOffset(end))
          psiFile
            .depthFirst(_.getTextRange.contains(range)) // Optimized iteration
            .filter(_.getTextRange == range)
            .find(e => e.is[ScExpression, ScStableCodeReference])
            .foreach { e =>
              // Skip if the same value already exists
              if (!CompilerType(e).contains(tpe)) {
                // Doesn't require a write action
                CompilerType(e) = Some(tpe)
                elements :+= e
              }
            }
        }
      }
      HighlightInfoData(data, errorFiles, elements)
    }

    /**
     * Closes `span` and reports the files it modified, at most once.
     *
     * The guard matters because the span must be closed by the '''last''' of two callbacks that both see
     * the same submission, and an already-ended span silently discards a second `end` together with the
     * attributes it carries. `NonBlockingReadAction` completes its promise ''before'' invoking the
     * `finishOnUiThread` action, and the promise callbacks run synchronously inside that completion, so a
     * plain "did the UI action run yet?" flag is always still `false` when the promise callback observes it
     * — closing the span with no files, before the highlightings have been applied.
     */
    val endAction: Set[VirtualFile] => Unit = {
      val ended = new AtomicBoolean(false)

      files =>
        if (ended.compareAndSet(false, true)) {
          tracer.mapAndEnd(span) {
            case event@HighlightingEvent(_, _) => Some(event.copy(files = files.map(_.toString)).closed())
            case e => Some(e.closed())
          }
          if (isUnitTestMode) {
            notifyHighlightingApplied(files)
          }
        }
    }

    val promise = ReadAction
      .nonBlocking(readActionCallable)
      .inSmartMode(project)
      .expireWhen(() => project.isDisposed || !DocumentUtil.stillValid(compilationId.documentVersions))
      .coalesceBy(compilationId)
      .finishOnUiThread(ModalityState.nonModal(), { data =>
        val modifiedFiles =
          try updater.applyHighlightingInfo(data, compilationId)
          catch {
            case NonFatal(t) =>
              // The span still has to be closed, but nothing reached the editors.
              endAction(Set.empty)
              throw t
          }
        endAction(modifiedFiles)
      })
      .submit(BackgroundExecutorService.instance(project).executor)

    // Only the paths that never reach the UI action are closed here: a cancelled or failed submission skips
    // it, whereas a succeeded one closes the span itself once the highlightings are on the editors.
    // The promise is already settled when this runs, so its state distinguishes the two.
    promise.onProcessed { _ =>
      if (!promise.isSucceeded) {
        endAction(Set.empty)
      }
    }
  }

  def eraseAllHighlightings(): Unit = {
    updater.eraseAllHighlightings()
  }

  @RequiresReadLock
  private def filterFilesToHighlightBasedOnFileLevel(files: Set[VirtualFile]): Set[VirtualFile] = {
    val manager = PsiManager.getInstance(project)
    files.filter { vf =>
      ProgressManager.checkCanceled()
      if (vf.isValid) {
        val psiFile = manager.findFile(vf)
        if (psiFile ne null) ScalaHighlightingMode.shouldHighlightBasedOnFileLevel(psiFile, project) else false
      } else false
    }
  }

  private def isUnitTestMode = {
    ApplicationManager.getApplication.isUnitTestMode
  }
}

private[highlighting] object ExternalHighlightersService {
  final val ScalaCompilerPassId = 979132998

  final case class HighlightInfoData(highlightingData: Seq[HighlightingData],
                                     virtualFiles: Set[VirtualFile],
                                     psiElements: Seq[PsiElement])

  final case class HighlightingData(editor: com.intellij.openapi.editor.Editor,
                                    document: com.intellij.openapi.editor.Document,
                                    psiFile: com.intellij.psi.PsiFile,
                                    virtualFile: VirtualFile,
                                    highlightInfos: Set[com.intellij.codeInsight.daemon.impl.HighlightInfo])

  final val Log: Logger = Logger.getInstance(classOf[ExternalHighlightersService])

  def instance(project: Project): ExternalHighlightersService =
    project.getService(classOf[ExternalHighlightersService])

  final case class TextRangeWithEndOfLine(textRange: TextRange, endOfLine: Boolean)
}
