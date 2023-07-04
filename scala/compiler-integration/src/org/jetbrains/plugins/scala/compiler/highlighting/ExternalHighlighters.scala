package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.codeInsight.daemon.impl.{HighlightInfo, HighlightInfoType, UpdateHighlightersUtil}
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.{Document, Editor, EditorFactory}
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
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
import scala.jdk.CollectionConverters._

object ExternalHighlighters {

  // A random number of highlighters group to avoid conflicts with standard groups.
  private[highlighting] final val ScalaCompilerPassId = 979132998

  def applyHighlighting(project: Project, editor: Editor, state: HighlightingState): Unit = {
    val document = editor.getDocument
    for {
      virtualFile <- document.virtualFile
      psiFile <- Option(inReadAction(PsiManager.getInstance(project).findFile(virtualFile)))
      if ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(psiFile)
    } {
      val externalHighlights = state.externalHighlightings(virtualFile)
      val highlightInfos = externalHighlights.flatMap(toHighlightInfo(_, document, psiFile))
      invokeLater {
        UpdateHighlightersUtil.setHighlightersToEditor(
          project,
          document, 0, document.getTextLength,
          highlightInfos.toSeq.asJava,
          editor.getColorsScheme,
          ScalaCompilerPassId
        )
      }
    }
  }

  def eraseAllHighlightings(project: Project): Unit = {
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
    ProblemSolverUtils.clearAllProblemsFromExternalSource(project, this)
  }

  def informWolf(project: Project, state: HighlightingState): Unit =
    if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) {
      val errorTypes = Set(HighlightInfoType.ERROR, HighlightInfoType.WRONG_REF)
      ProblemSolverUtils.clearAllProblemsFromExternalSource(project, this)
      val wolf = WolfTheProblemSolver.getInstance(project)
      val errorFiles = filterFilesToHighlightBasedOnFileLevel(state.filesWithHighlightings(errorTypes), project)
      inReadAction(errorFiles.foreach(wolf.reportProblemsFromExternalSource(_, this)))
    }

  private[highlighting] def filterFilesToHighlightBasedOnFileLevel(
    files: Set[VirtualFile],
    project: Project
  ): Set[VirtualFile] = {
    val manager = PsiManager.getInstance(project)
    inReadAction {
      files.filter { vf =>
        val psiFile = manager.findFile(vf)
        if (psiFile ne null) ScalaHighlightingMode.shouldHighlightBasedOnFileLevel(psiFile, project) else false
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

  private def toHighlightInfo(highlighting: ExternalHighlighting, document: Document, psiFile: PsiFile): Option[HighlightInfo] = {
    val message = highlighting.message

    //NOTE: in case there is no location in the file, do not ignore/loose messages
    //instead report them in the beginning of the file
    val range = highlighting.rangeInfo.getOrElse {
      val start = PosInfo(1, 1)
      RangeInfo.Range(start, start)
    }

    for {
      highlightRange <- calculateRangeToHighlight(range, document, psiFile)
    } yield {
      val description = message.trim.stripSuffix(lineText(message))

      def standardBuilder =
        highlightInfoBuilder(highlighting.highlightType, highlightRange, description)

      val highlightInfo =
        if (description.trim.equalsIgnoreCase("unused import")) {
          val leaf = psiFile.findElementAt(highlightRange.getStartOffset)
          val unusedImportRange = inReadAction(unusedImportElementRange(leaf))
          if (unusedImportRange != null) {
            // modify highlighting info to mimic Scala 2 unused import highlighting in Scala 3
            highlightInfoBuilder(HighlightInfoType.UNUSED_SYMBOL, unusedImportRange, ScalaInspectionBundle.message("unused.import.statement"))
              .registerFix(new ScalaOptimizeImportsFix, null, null, unusedImportRange, null)
          } else standardBuilder
        } else standardBuilder

      inReadAction {
        val fixes = findQuickFixes(psiFile, highlightRange, highlighting.highlightType)
        fixes.foreach(highlightInfo.registerFix(_, null, null, highlightRange, null))
      }
      highlightInfo.create()
    }
  }

  private def escapeHtmlWithNewLines(unescapedTooltip: String): String = {
    val escaped0 = XmlStringUtil.escapeString(unescapedTooltip)
    val escaped1 = escaped0.replace("\n", "<br>")
    val escaped2 = XmlStringUtil.wrapInHtml(escaped1)
    escaped2
  }

  private def calculateRangeToHighlight(
    rangeInfo: RangeInfo,
    document: Document,
    psiFile: PsiFile
  ): Option[TextRange] = inReadAction {
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

  private def guessRangeToHighlight(psiFile: PsiFile, startOffset: Int): Option[TextRange] =
    elementToHighlight(psiFile, startOffset).map(_.getTextRange)

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
   */
  private def convertToOffset(line: Int, column: Int, document: Document): Option[Int] = {
    val ln = line - 1
    val cl = column - 1
    if (ln >= 0 && ln < document.getLineCount && cl >= 0) Some(document.getLineStartOffset(ln) + cl)
    else None
  }

  private def lineText(messageText: String): String = {
    val trimmed = messageText.trim
    val lastLineSeparator = trimmed.lastIndexOf('\n')
    if (lastLineSeparator > 0) trimmed.substring(lastLineSeparator).trim else ""
  }

  private def findQuickFixes(file: PsiFile,
                             range: TextRange,
                             highlightInfoType: HighlightInfoType): Seq[IntentionAction] = {
    // e.g. on opening project we are in dump mode, and can't do resolve to search quickfixes
    if (DumbService.isDumb(file.getProject))
      return Seq.empty

    val ref = PsiTreeUtil.findElementOfClassAtRange(file, range.getStartOffset, range.getEndOffset, classOf[ScReference])

    if (ref != null && highlightInfoType == HighlightInfoType.WRONG_REF && ref.multiResolveScala(false).isEmpty)
      UnresolvedReferenceFixProvider.fixesFor(ref)
    else Seq.empty
  }
}
