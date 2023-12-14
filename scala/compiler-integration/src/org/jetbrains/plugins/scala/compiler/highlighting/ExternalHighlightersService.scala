package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.codeInsight.daemon.impl.{HighlightInfo, HighlightInfoType, UpdateHighlightersUtil}
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.{ModalityState, ReadAction}
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.{Document, EditorFactory}
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.xml.util.XmlStringUtil
import org.jetbrains.annotations.{Nls, Nullable}
import org.jetbrains.jps.incremental.scala.Client.PosInfo
import org.jetbrains.plugins.scala.annotator.UnresolvedReferenceFixProvider
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaOptimizeImportsFix
import org.jetbrains.plugins.scala.compiler.highlighting.ExternalHighlighting.RangeInfo
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt, inReadAction, invokeLater}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed.UnusedImportReportedByCompilerKey
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportOrExportStmt, ScImportSelector}
import org.jetbrains.plugins.scala.settings.{ProblemSolverUtils, ScalaHighlightingMode}

import java.util.Collections
import java.util.concurrent.{Callable, ConcurrentLinkedQueue}
import java.util.function.Consumer
import scala.jdk.CollectionConverters._

@Service(Array(Service.Level.PROJECT))
private final class ExternalHighlightersService(project: Project) { self =>
  import ExternalHighlightersService.ScalaCompilerPassId

  private final class ExecutionState {
    @volatile var obsolete: Boolean = false
  }

  private val queue: ConcurrentLinkedQueue[Cancellable] = new ConcurrentLinkedQueue()

  def applyHighlightingState(virtualFiles: Set[VirtualFile], state: HighlightingState): Unit = {
    if (project.isDisposed) return

    // Cancel all running highlighting info computations from previous compilation runs.
    // In practice, there will only ever be one running computation, because this method is called on a single thread.
    while (!queue.isEmpty) {
      val head = queue.poll()

      // It can happen that the queue was emptied between `queue.isEmpty` and `queue.poll`, due to a concurrent
      // `queue.remove` later.
      if (head ne null) {
        head.cancel()
      }
    }

    // Show red squiggly lines for errors in Project View.
    informWolf(state)

    // State necessary for cancelling every promise that will be fired for each open editor that needs to have
    // highlighting information applied.
    val executionState = new ExecutionState()

    val filteredVirtualFiles = filterFilesToHighlightBasedOnFileLevel(virtualFiles)
    val promises = for {
      editor <- EditorFactory.getInstance().getAllEditors if !project.isDisposed
      editorProject <- Option(editor.getProject) if editorProject == project
      document = editor.getDocument
      virtualFile <- document.virtualFile if filteredVirtualFiles.contains(virtualFile)
      psiFile <- Option(inReadAction(PsiManager.getInstance(project).findFile(virtualFile)))
      if ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(psiFile)
    } yield {
      val externalHighlights = state.externalHighlightings(virtualFile)

      val executionState = new ExecutionState()

      val readActionCallable: Callable[Either["Cancelled", Set[HighlightInfo]]] =
        () => calculateHighlightInfos(externalHighlights, document, psiFile, executionState)

      val applyHighlightingInfo: Consumer[Either["Cancelled", Set[HighlightInfo]]] = {
        case Left("Cancelled") => ()
        case Right(highlightInfos) =>
          if (!executionState.obsolete && !project.isDisposed) {
            // If the results are still valid, they will be applied to the editor.
            val collection = highlightInfos.asJavaCollection
            UpdateHighlightersUtil.setHighlightersToEditor(
              project,
              document, 0, document.getTextLength,
              collection,
              editor.getColorsScheme,
              ScalaCompilerPassId
            )
          }
          queue.remove(executionState)
      }

      ReadAction
        .nonBlocking(readActionCallable)
        .expireWhen(() => executionState.obsolete || project.isDisposed)
        .finishOnUiThread(ModalityState.defaultModalityState(), applyHighlightingInfo)
        .submit(BackgroundExecutorService.instance(project).executor)
    }

    queue.add(() => {
      // When cancelled, cancel all scheduled promises and mark the highlighting as obsolete.
      promises.foreach(_.cancel())
      executionState.obsolete = true
    })
  }

  def eraseAllHighlightings(): Unit = {
    for {
      editor <- EditorFactory.getInstance.getAllEditors
      editorProject <- Option(editor.getProject)
      if editorProject == project
    } invokeLater {
      if (!project.isDisposed) {
        val document = editor.getDocument
        UpdateHighlightersUtil.setHighlightersToEditor(
          project,
          document, 0, document.getTextLength,
          Collections.emptyList(),
          editor.getColorsScheme,
          ScalaCompilerPassId
        )
      }
    }
    ProblemSolverUtils.clearAllProblemsFromExternalSource(project, self)
  }

  private def informWolf(state: HighlightingState): Unit =
    if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) {
      val errorTypes = Set(HighlightInfoType.ERROR, HighlightInfoType.WRONG_REF)
      ProblemSolverUtils.clearAllProblemsFromExternalSource(project, self)
      val wolf = WolfTheProblemSolver.getInstance(project)
      val errorFiles = filterFilesToHighlightBasedOnFileLevel(state.filesWithHighlightings(errorTypes))
      inReadAction(errorFiles.foreach(wolf.reportProblemsFromExternalSource(_, self)))
    }

  private def filterFilesToHighlightBasedOnFileLevel(files: Set[VirtualFile]): Set[VirtualFile] = {
    val manager = PsiManager.getInstance(project)
    inReadAction {
      files.filter { vf =>
        if (vf.isValid) {
          val psiFile = manager.findFile(vf)
          if (psiFile ne null) ScalaHighlightingMode.shouldHighlightBasedOnFileLevel(psiFile, project) else false
        } else false
      }
    }
  }

  @Nullable
  private def unusedImportElementRange(@Nullable leaf: PsiElement): TextRange = {
    val importExpr = PsiTreeUtil.getParentOfType(leaf, classOf[ScImportExpr])
    if (importExpr == null) return null

    // Put user data to enable Optimize Imports action
    // See org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaUnusedImportPass
    def markAsUnused(element: PsiElement): Unit =
      element.putUserData(UnusedImportReportedByCompilerKey, true)

    val set = ImportUsed.buildAllFor(importExpr).map(_.element)
    if (set.contains(importExpr)) {
      markAsUnused(importExpr)
      importExpr.getParent.asOptionOf[ScImportOrExportStmt].getOrElse(importExpr).getTextRange
    } else {
      val selector = PsiTreeUtil.getParentOfType(leaf, classOf[ScImportSelector])
      if (selector != null && set.contains(selector)) {
        markAsUnused(selector)
        selector.getTextRange
      } else null
    }
  }

  private def highlightInfoBuilder(highlightType: HighlightInfoType, highlightRange: TextRange, @Nls description: String): HighlightInfo.Builder =
    HighlightInfo
      .newHighlightInfo(highlightType)
      .range(highlightRange)
      .description(description)
      .escapedToolTip(escapeHtmlWithNewLines(description))
      .group(ScalaCompilerPassId)

  @RequiresReadLock
  private def calculateHighlightInfos(
    externalHighlights: Set[ExternalHighlighting],
    document: Document,
    psiFile: PsiFile,
    executionState: ExecutionState
  ): Either["Cancelled", Set[HighlightInfo]] = {
    val res = externalHighlights.flatMap { highlighting =>
      ProgressManager.checkCanceled()
      if (executionState.obsolete) {
        return Left("Cancelled")
      }
      toHighlightInfo(highlighting, document, psiFile)
    }
    Right(res)
  }

  @RequiresReadLock
  private def toHighlightInfo(highlighting: ExternalHighlighting, document: Document, psiFile: PsiFile): Option[HighlightInfo] = {
    //NOTE: in case there is no location in the file, do not ignore/loose messages
    //instead report them in the beginning of the file
    val range = highlighting.rangeInfo.getOrElse {
      // Our PosInfo data structure expects 1-based line and column information.
      val start = PosInfo(1, 1)
      RangeInfo.Range(start, start)
    }

    for {
      highlightRange <- calculateRangeToHighlight(range, document, psiFile)
    } yield {
      val description = CompilerMessages.description(highlighting.message)

      def standardBuilder =
        highlightInfoBuilder(highlighting.highlightType, highlightRange, description)

      val highlightInfo =
        if (CompilerMessages.isUnusedImport(description)) {
          val leaf = psiFile.findElementAt(highlightRange.getStartOffset)
          val unusedImportRange = unusedImportElementRange(leaf)
          if (unusedImportRange != null) {
            // modify highlighting info to mimic Scala 2 unused import highlighting in Scala 3
            highlightInfoBuilder(HighlightInfoType.UNUSED_SYMBOL, unusedImportRange, ScalaInspectionBundle.message("unused.import.statement"))
              .registerFix(new ScalaOptimizeImportsFix, null, null, unusedImportRange, null)
          } else standardBuilder
        } else standardBuilder

      val fixes = findUnresolvedReferenceFixes(psiFile, highlightRange, highlighting.highlightType)
      fixes.foreach(highlightInfo.registerFix(_, null, null, highlightRange, null))
      highlightInfo.create()
    }
  }

  private def escapeHtmlWithNewLines(unescapedTooltip: String): String = {
    val escaped0 = XmlStringUtil.escapeString(unescapedTooltip)
    val escaped1 = escaped0.replace("\n", "<br>")
    val escaped2 = XmlStringUtil.wrapInHtml(escaped1)
    escaped2
  }

  @RequiresReadLock
  private def calculateRangeToHighlight(
    rangeInfo: RangeInfo,
    document: Document,
    psiFile: PsiFile
  ): Option[TextRange] = {
    rangeInfo match {
      case RangeInfo.Range(PosInfo(startLine, startColumn), PosInfo(endLine, endColumn)) =>
        for {
          startOffset <- convertToOffset(startLine, startColumn, document)
          endOffset <- convertToOffset(endLine, endColumn, document)
        } yield TextRange.create(startOffset, endOffset)
      case RangeInfo.Pointer(PosInfo(line, column)) =>
        convertToOffset(line, column, document).flatMap(guessRangeToHighlight(psiFile, _))
    }
  }

  @RequiresReadLock
  private def guessRangeToHighlight(psiFile: PsiFile, startOffset: Int): Option[TextRange] =
    elementToHighlight(psiFile, startOffset).map(_.getTextRange)

  @RequiresReadLock
  private def elementToHighlight(file: PsiFile, offset: Int): Option[PsiElement] =
    Option(file.findElementAt(offset)).flatMap {
      case whiteSpace: PsiWhiteSpace =>
        whiteSpace.prevElementNotWhitespace
      case javaToken: PsiJavaToken if javaToken.getTokenType == JavaTokenType.DOT =>
        javaToken.nextElementNotWhitespace
      case other =>
        Some(other)
    }

  /**
   * Must be called inside a read action in order to have a correct evaluation of `Document#getLineCount`,
   * ensuring that the document has not been modified before subsequently calling `Document.getLineStartOffset`.
   *
   * @param line     1-based line index
   * @param column   1-based column index
   * @param document the document that corresponds to the line and column information, used for calculating offsets
   */
  @RequiresReadLock
  private def convertToOffset(line: Int, column: Int, document: Document): Option[Int] = {
    // Document works with 0-based line and column indices.
    val ln = line - 1
    val cl = column - 1
    if (ln >= 0 && ln < document.getLineCount && cl >= 0) Some(document.getLineStartOffset(ln) + cl)
    else None
  }

  @RequiresReadLock
  private def findUnresolvedReferenceFixes(file: PsiFile,
                                           range: TextRange,
                                           highlightInfoType: HighlightInfoType): Seq[IntentionAction] = {
    // e.g. on opening project we are in dump mode, and can't do resolve to search quickfixes
    if (highlightInfoType != HighlightInfoType.WRONG_REF || DumbService.isDumb(file.getProject))
      return Seq.empty

    val ref = PsiTreeUtil.findElementOfClassAtRange(file, range.getStartOffset, range.getEndOffset, classOf[ScReference])

    if (ref != null && ref.multiResolveScala(false).isEmpty)
      UnresolvedReferenceFixProvider.fixesFor(ref)
    else Seq.empty
  }
}

private object ExternalHighlightersService {
  final val ScalaCompilerPassId = 979132998

  def instance(project: Project): ExternalHighlightersService =
    project.getService(classOf[ExternalHighlightersService])
}
