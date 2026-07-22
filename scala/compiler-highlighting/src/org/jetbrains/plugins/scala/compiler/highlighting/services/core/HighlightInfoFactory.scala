package org.jetbrains.plugins.scala.compiler.highlighting.services.core

import com.intellij.codeInsight.daemon.impl.{HighlightInfo, HighlightInfoType}
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.modcommand.ModCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.xml.util.XmlStringUtil
import org.jetbrains.annotations.{Nls, Nullable}
import org.jetbrains.jps.incremental.scala.Client.PosInfo
import org.jetbrains.plugins.scala.annotator.element.ScTemplateDefinitionAnnotator
import org.jetbrains.plugins.scala.annotator.quickfix.AddParametersQuickfix
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaOptimizeImportsFix
import org.jetbrains.plugins.scala.compiler.diagnostics.Action
import org.jetbrains.plugins.scala.compiler.highlighting.core.ExternalHighlighting.RangeInfo
import org.jetbrains.plugins.scala.compiler.highlighting.core.{CompilerDiagnosticIntentionAction, CompilerMessages, ExternalHighlighting}
import org.jetbrains.plugins.scala.compiler.highlighting.events.HighlightingPhaseEvents.{FindUnresolvedReferenceEvent, RegisterQuickFixes}
import org.jetbrains.plugins.scala.compiler.highlighting.services.ExternalHighlightersService.{ScalaCompilerPassId, TextRangeWithEndOfLine}
import org.jetbrains.plugins.scala.compiler.tracing.Tracing
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, ObjectExt, Parent, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScArgumentExprList
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed.UnusedImportReportedByCompilerKey
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportOrExportStmt, ScImportSelector}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.util.CompilationId

import scala.annotation.tailrec

/**
 * Constructs IDE-compatible highlighting data structures from raw compiler diagnostics.
 *
 * Acts as a factory that combines calculated document ranges, textual descriptions,
 * and generated quick fixes into standardized information objects utilized by the
 * IDE's editor subsystem.
 */
private[highlighting] class HighlightInfoFactory(
  project: Project,
  rangeCalculator: HighlightingRangeCalculator,
  fixProvider: ExternalHighlightingFixProvider
) {
  
  private val tracer = Tracing(project)

  @RequiresReadLock
  def calculateHighlightInfos(
    externalHighlights: Set[ExternalHighlighting],
    document: Document,
    psiFile: PsiFile,
    compilationId: CompilationId
  ): Set[HighlightInfo] =
    externalHighlights.flatMap { highlighting =>
      ProgressManager.checkCanceled()
      toHighlightInfo(highlighting, document, psiFile, compilationId)
    }

  @RequiresReadLock
  private def toHighlightInfo(highlighting: ExternalHighlighting, document: Document, psiFile: PsiFile,
                              compilationId: CompilationId): Option[HighlightInfo] = {
    //NOTE: in case there is no location in the file, do not ignore/lose messages
    //instead report them in the beginning of the file
    val range = highlighting.rangeInfo.getOrElse {
      // Our PosInfo data structure expects 1-based line and column information.
      val start = PosInfo(1, 1)
      RangeInfo.Range(start, start, s"toHighlightInfo rangeInfo default case (1, 1), highlighting=$highlighting")
    }

    for {
      textRangeWithEndOfLine <- rangeCalculator.calculateRangeToHighlight(range, document, psiFile)
    } yield {
      val description = CompilerMessages.description(highlighting.message)
      val TextRangeWithEndOfLine(highlightRange, endOfLine) = textRangeWithEndOfLine

      def standardBuilder =
        highlightInfoBuilder(document, highlighting.highlightType, highlightRange, endOfLine, description, highlighting.diagnostics)

      val highlightInfo =
        if (CompilerMessages.isUnusedImport(description)) {
          val leaf = psiFile.findElementAt(highlightRange.getStartOffset)
          val unusedImportRange = unusedImportElementRange(leaf)
          if (unusedImportRange != null) {
            // modify highlighting info to mimic Scala 2 unused import highlighting in Scala 3
            val infoType =
              if (highlighting.highlightType == HighlightInfoType.ERROR) HighlightInfoType.ERROR
              else HighlightInfoType.UNUSED_SYMBOL

            highlightInfoBuilder(document, infoType, unusedImportRange, endOfLine, ScalaInspectionBundle.message("unused.import.statement"), Nil)
              .registerFix(new ScalaOptimizeImportsFix, null, null, unusedImportRange, null)
          } else standardBuilder
        } else if (highlighting.diagnostics.isEmpty && CompilerMessages.isNeedsToBeAbstract(description)) {
          def builderWithNeedsToBeAbstractFixes(td: ScTemplateDefinition) = {
            val fixes = ScTemplateDefinitionAnnotator.needsToBeAbstractFixes(td)
            fixes.foldLeft(standardBuilder) {
              case (builder, fix: ModCommandAction) =>
                //noinspection ApiStatus
                builder.registerFix(fix, null, null, highlightRange, null)
              case (builder, fix: IntentionAction) =>
                builder.registerFix(fix, null, null, highlightRange, null)
              case (builder, _) => builder
            }
          }

          psiFile.findElementAt(highlightRange.getStartOffset) match {
            // [class|object] <startOffset>Foo ...
            case Parent(td: ScTemplateDefinition) => builderWithNeedsToBeAbstractFixes(td)
            case Parent(ref: ScStableCodeReference) =>
              ref.parentsInFile.findByType[ScTemplateDefinition] match {
                // [new|given] <startOffset>Foo ...
                case Some(td) => builderWithNeedsToBeAbstractFixes(td)
                case _ => standardBuilder
              }
            case _ => standardBuilder
          }
        } else standardBuilder

      {
        // There are multiple errors that can occur which indicate a missing parameter,
        // so we just always add the AddParametersQuickfix (the quickfix itself is pretty conservative when it will appear)
        @tailrec
        def findArgumentList(e: PsiElement): Option[ScArgumentExprList] = {
          PsiTreeUtil.getParentOfType(e, classOf[ScArgumentExprList]) match {
            case null => None
            case args if args.getTextRange.contains(highlightRange) => Some(args)
            case args => findArgumentList(args)
          }
        }

        val argList = findArgumentList(psiFile.findElementAt(highlightRange.getStartOffset))

        val span = tracer.begin(RegisterQuickFixes(compilationId))
        var fixes = Set.empty[String]
        for (argList <- argList) {
          val quickfix = new AddParametersQuickfix(argList.createSmartPointer)
          highlightInfo.registerFix(quickfix, null, null, argList.getTextRange, null)
          fixes = fixes + quickfix.getText
        }
        tracer.mapAndEnd(span) {
          case RegisterQuickFixes(_,_) => Some(RegisterQuickFixes(compilationId, fixes))
        }
      }

      fixProvider.registerImportFixesFromMessage(highlighting.message, highlightRange, psiFile, highlightInfo)

      val fixes = tracer.trace(FindUnresolvedReferenceEvent(compilationId, psiFile.toString)) {
        fixProvider.findUnresolvedReferenceFixes(psiFile, highlightRange, highlighting.highlightType)
      }
      fixes.foreach(highlightInfo.registerFix(_, null, null, highlightRange, null))
      highlightInfo.create()
    }
  }

  private def highlightInfoBuilder(
    document: Document,
    highlightType: HighlightInfoType,
    highlightRange: TextRange,
    endOfLine: Boolean,
    @Nls description: String,
    diagnostics: List[Action]
  ): HighlightInfo.Builder = {
    val builder = HighlightInfo.newHighlightInfo(highlightType)
      .range(highlightRange)
      .description(description)
      .escapedToolTip(escapeHtmlWithNewLines(description))
      .group(ScalaCompilerPassId)

    if (endOfLine) {
      builder.endOfLine()
    }

    diagnostics
      .map(CompilerDiagnosticIntentionAction.create(document, _))
      .foreach(builder.registerFix(_, null, null, TextRange.create(highlightRange.getStartOffset, highlightRange.getEndOffset), null))

    builder
  }

  private def escapeHtmlWithNewLines(unescapedTooltip: String): String = {
    import scala.util.chaining.*
    unescapedTooltip
      .pipe(XmlStringUtil.escapeString)
      .pipe(_.replace("\n", "<br>"))
      .pipe(XmlStringUtil.wrapInHtmlTag(_, "pre"))
      .pipe(XmlStringUtil.wrapInHtml)
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
}
