package org.jetbrains.plugins.scala
package codeInspection
package unusedInspections

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.impl.{HighlightInfo, HighlightInfoType, UpdateHighlightersUtil}
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Document
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions.Parent
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ImportExprUsed, ImportSelectorUsed, ImportUsed, ImportWildcardSelectorUsed}

import java.util

trait ScalaUnusedImportPassBase { self: TextEditorHighlightingPass =>
  def file: PsiFile
  def document: Document

  def collectHighlightings(unusedImports: Iterable[ImportUsed]): Iterable[HighlightInfo] = {
    unusedImports.filterNot(_.isAlwaysUsed).flatMap {
      (imp: ImportUsed) => {
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

        psiOption.toSeq.flatMap { psi =>
          val highlightInfo =
            HighlightInfo.newHighlightInfo(HighlightInfoType.UNUSED_SYMBOL)
              .range(psi.getTextRange)
              .descriptionAndTooltip(ScalaInspectionBundle.message("unused.import.statement"))
              .create()

          getFixes.foreach(action => highlightInfo.registerFix(action, null, null, null, null))
          qName.foreach(name => highlightInfo.registerFix(new MarkImportAsAlwaysUsed(name), null, null, null, null))
          Seq(highlightInfo)
        }
      }
    }
  }

  protected def highlightAll(highlights: util.Collection[HighlightInfo]): Unit = {
    UpdateHighlightersUtil.setHighlightersToEditor(file.getProject, document, 0,
      file.getTextLength, highlights, getColorsScheme, getId)
  }
  
  protected def getFixes: List[IntentionAction]
}
