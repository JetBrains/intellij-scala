package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.impl.{HighlightInfo, HighlightInfoType, UpdateHighlightersUtil}
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Document
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.extensions.Parent
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ImportExprUsed, ImportSelectorUsed, ImportUsed, ImportWildcardSelectorUsed}

import java.util

trait ScalaUnusedImportPassBase { self: TextEditorHighlightingPass =>
  def file: PsiFile
  def document: Document

  def collectHighlightings(unusedImports: Iterable[ImportUsed]): Iterable[HighlightInfo] =
    unusedImports
      // Do not highlight imports that were marked as unused by compiler and highlighted in
      // org.jetbrains.plugins.scala.compiler.highlighting.ExternalHighlighters.applyHighlighting
      // to avoid duplicated highlightings
      .filterNot(imp => imp.isAlwaysUsed || imp.markedAsUnusedByCompiler)
      .flatMap { (imp: ImportUsed) =>
        val psiOption: Option[PsiElement] = imp match {
          case ImportExprUsed(expr@Parent(importStmt: ScImportStmt)) if !PsiTreeUtil.hasErrorElements(expr) =>
            if (importStmt.importExprs.size == 1) Some(importStmt)
            else Some(expr)
          case ImportSelectorUsed(sel) => Some(sel)
          case ImportWildcardSelectorUsed(e) if e.selectors.nonEmpty => Some(e.wildcardElement.get)
          case ImportWildcardSelectorUsed(e) if !PsiTreeUtil.hasErrorElements(e) => Some(e.getParent)
          case _ => None
        }

        val qName = imp.qualName

        psiOption.flatMap { psi =>
          val highlightInfoBuilder =
            HighlightInfo.newHighlightInfo(HighlightInfoType.UNUSED_SYMBOL)
              .range(psi)
              .descriptionAndTooltip(ScalaInspectionBundle.message("unused.import.statement"))

          getFixes.foreach(action => highlightInfoBuilder.registerFix(action, null, null, null, null))
          qName.foreach(name => highlightInfoBuilder.registerFix(new MarkImportAsAlwaysUsed(name), null, null, null, null))
          // can be null if was rejected by com.intellij.codeInsight.daemon.impl.HighlightInfoFilter
          Option(highlightInfoBuilder.create())
        }
      }

  protected def highlightAll(highlights: util.Collection[HighlightInfo]): Unit = {
    UpdateHighlightersUtil.setHighlightersToEditor(file.getProject, document, 0,
      file.getTextLength, highlights, getColorsScheme, getId)
  }

  protected def getFixes: List[IntentionAction]
}
