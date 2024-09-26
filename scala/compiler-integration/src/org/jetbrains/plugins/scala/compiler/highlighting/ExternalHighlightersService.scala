package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.codeInsight.daemon.impl.{ErrorStripeUpdateManager, HighlightInfo, HighlightInfoType, UpdateHighlightersUtil}
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.{ModalityState, ReadAction}
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.{Document, Editor, EditorFactory}
import com.intellij.openapi.fileEditor.{FileDocumentManager, FileEditorManager}
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileManager}
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.xml.util.XmlStringUtil
import org.jetbrains.annotations.{Nls, Nullable}
import org.jetbrains.jps.incremental.scala.Client.PosInfo
import org.jetbrains.plugins.scala.annotator.UnresolvedReferenceFixProvider
import org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHints
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaOptimizeImportsFix
import org.jetbrains.plugins.scala.compiler.diagnostics.Action
import org.jetbrains.plugins.scala.compiler.highlighting.ExternalHighlighting.RangeInfo
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.extensions.{IteratorExt, ObjectExt, PsiElementExt, executeOnPooledThread, inWriteAction, invokeLater}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed.UnusedImportReportedByCompilerKey
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportOrExportStmt, ScImportSelector}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.settings.{ProblemSolverUtils, ScalaHighlightingMode}
import org.jetbrains.plugins.scala.util.{CompilationId, DocumentVersion}

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.Callable
import java.util.function.Consumer
import scala.jdk.CollectionConverters._

@Service(Array(Service.Level.PROJECT))
private final class ExternalHighlightersService(project: Project) { self =>
  import ExternalHighlightersService.{HighlightingData, ScalaCompilerPassId}

  private val errorTypes: Set[HighlightInfoType] = Set(HighlightInfoType.ERROR, HighlightInfoType.WRONG_REF)

  def applyHighlightingState(virtualFiles: Set[VirtualFile], state: HighlightingState, compilationId: CompilationId): Unit = {
    if (project.isDisposed) return

    val readActionCallable: Callable[(Seq[HighlightingData], Set[VirtualFile])] = { () =>
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
        val highlightInfos = calculateHighlightInfos(externalHighlights, document, psiFile)
        HighlightingData(editor, document, psiFile, highlightInfos)
      }
      val errorFiles = filterFilesToHighlightBasedOnFileLevel(state.filesWithHighlightings(errorTypes))
      (data, errorFiles)
    }

    val applyHighlightingInfo: Consumer[(Seq[HighlightingData], Set[VirtualFile])] = {
      case (infos, errorFiles) =>
        if (!project.isDisposed && stillValid(compilationId)) {
          // If the results are still valid, they will be applied to the editor.
          infos.foreach { case HighlightingData(editor, document, psiFile, highlightInfos) =>
            val collection = highlightInfos.asJavaCollection
            UpdateHighlightersUtil.setHighlightersToEditor(
              project,
              document, 0, document.getTextLength,
              collection,
              editor.getColorsScheme,
              ScalaCompilerPassId
            )
            ErrorStripeUpdateManager.getInstance(project).repaintErrorStripePanel(editor, psiFile)
          }
          // Show red squiggly lines for errors in Project View.
          executeOnPooledThread(informWolf(errorFiles))
        }
    }

    Option(FileEditorManager.getInstance(project).getFocusedEditor).foreach { editor =>
      state.externalTypes(editor.getFile).foreach { case ((begin, end), tpe) =>
        inWriteAction {
          val file = PsiManager.getInstance(project).findFile(editor.getFile)
          def toOffset(pos: PosInfo): Int = file.getText.split('\n').take(pos.line - 1).map(_.length + 1).sum + pos.column - 1
          file.elements.filterByType[ScMethodCall].find(e => e.getTextRange == new TextRange(toOffset(begin), toOffset(end))).foreach { expression =>
            expression.putUserData(ScExpression.CompilerTypeKey, tpe)
            ScalaPsiManager.instance(project).clearOnScalaElementChange(expression)
            ImplicitHints.updateInAllEditors()
          }
        }
      }
    }

    ReadAction
      .nonBlocking(readActionCallable)
      .inSmartMode(project)
      .expireWhen(() => project.isDisposed || !stillValid(compilationId))
      .coalesceBy(compilationId)
      .finishOnUiThread(ModalityState.nonModal(), applyHighlightingInfo)
      .submit(BackgroundExecutorService.instance(project).executor)
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

  private def stillValid(compilationId: CompilationId): Boolean =
    compilationId.documentVersion.forall { case DocumentVersion(path, version) =>
      val url = URLDecoder.decode(Path.of(path).toUri.toString, StandardCharsets.UTF_8)
      val virtualFile = VirtualFileManager.getInstance().findFileByUrl(url)
      if (virtualFile eq null) false
      else {
        val document = FileDocumentManager.getInstance().getCachedDocument(virtualFile)
        if (document eq null) false else version == DocumentUtil.version(document)
      }
    }

  private def informWolf(errorFiles: Set[VirtualFile]): Unit = {
    if (!project.isDisposed && ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) {
      ProblemSolverUtils.clearAllProblemsFromExternalSource(project, self)
      val wolf = WolfTheProblemSolver.getInstance(project)
      errorFiles.foreach(wolf.reportProblemsFromExternalSource(_, self))
    }
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

  private def highlightInfoBuilder(
    document: Document,
    highlightType: HighlightInfoType,
    highlightRange: TextRange,
    @Nls description: String,
    diagnostics: List[Action]
  ): HighlightInfo.Builder = {
    val builder = HighlightInfo.newHighlightInfo(highlightType)
      .range(highlightRange)
      .description(description)
      .escapedToolTip(escapeHtmlWithNewLines(description))
      .group(ScalaCompilerPassId)

    diagnostics
      .map(CompilerDiagnosticIntentionAction.create(document, _))
      .foreach(builder.registerFix(_, null, null, TextRange.create(highlightRange.getStartOffset, highlightRange.getEndOffset), null))

    builder
  }

  @RequiresReadLock
  private def calculateHighlightInfos(
    externalHighlights: Set[ExternalHighlighting],
    document: Document,
    psiFile: PsiFile
  ): Set[HighlightInfo] =
    externalHighlights.flatMap { highlighting =>
      ProgressManager.checkCanceled()
      toHighlightInfo(highlighting, document, psiFile)
    }

  @RequiresReadLock
  private def toHighlightInfo(highlighting: ExternalHighlighting, document: Document, psiFile: PsiFile): Option[HighlightInfo] = {
    //NOTE: in case there is no location in the file, do not ignore/loose messages
    //instead report them in the beginning of the file
    val range = highlighting.rangeInfo.getOrElse {
      // Our PosInfo data structure expects 1-based line and column information.
      val start = PosInfo(1, 1)
      RangeInfo.Range(start, start, s"toHighlightInfo rangeInfo default case (1, 1), highlighting=$highlighting")
    }

    for {
      highlightRange <- calculateRangeToHighlight(range, document, psiFile)
    } yield {
      val description = CompilerMessages.description(highlighting.message)

      def standardBuilder =
        highlightInfoBuilder(document, highlighting.highlightType, highlightRange, description, highlighting.diagnostics)

      val highlightInfo =
        if (CompilerMessages.isUnusedImport(description)) {
          val leaf = psiFile.findElementAt(highlightRange.getStartOffset)
          val unusedImportRange = unusedImportElementRange(leaf)
          if (unusedImportRange != null) {
            // modify highlighting info to mimic Scala 2 unused import highlighting in Scala 3
            highlightInfoBuilder(document, HighlightInfoType.UNUSED_SYMBOL, unusedImportRange, ScalaInspectionBundle.message("unused.import.statement"), Nil)
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
    import ExternalHighlightersService.Log
    rangeInfo match {
      case range@RangeInfo.Range(PosInfo(startLine, startColumn), PosInfo(endLine, endColumn), _) =>
        for {
          startOffset <- convertToOffset(startLine, startColumn, document)
          endOffset <- convertToOffset(endLine, endColumn, document)
        } yield {
          if (startOffset <= endOffset) {
            TextRange.create(startOffset, endOffset)
          } else {
            val message = s"Illegal highlighting range calculated, startOffset=$startOffset, endOffset=$endOffset, range=$range"
            Log.error(message)
            throw new IllegalArgumentException(message)
          }
        }
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
    if (file.getProject.isDisposed || highlightInfoType != HighlightInfoType.WRONG_REF || DumbService.isDumb(file.getProject))
      return Seq.empty

    val ref = PsiTreeUtil.findElementOfClassAtRange(file, range.getStartOffset, range.getEndOffset, classOf[ScReference])

    if (ref != null && ref.multiResolveScala(false).isEmpty)
      UnresolvedReferenceFixProvider.fixesFor(ref)
    else Seq.empty
  }
}

private object ExternalHighlightersService {
  final val ScalaCompilerPassId = 979132998

  private final case class HighlightingData(editor: Editor, document: Document, psiFile: PsiFile, highlightInfos: Set[HighlightInfo])

  final val Log: Logger = Logger.getInstance(classOf[ExternalHighlightersService])

  def instance(project: Project): ExternalHighlightersService =
    project.getService(classOf[ExternalHighlightersService])
}
