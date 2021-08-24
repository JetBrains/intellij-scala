package org.jetbrains.plugins.scala
package codeInspection
package unusedInspections

import java.util
import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.impl.{HighlightInfo, UpdateHighlightersUtil}
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.{Annotation, AnnotationHolder}
import com.intellij.openapi.editor.Document
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions.Parent
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ImportExprUsed, ImportSelectorUsed, ImportUsed, ImportWildcardSelectorUsed}

import scala.annotation.nowarn

/**
 * User: Dmitry Naydanov
 * Date: 3/2/13
 */
trait ScalaUnusedImportPassBase { self: TextEditorHighlightingPass =>
  def file: PsiFile
  def document: Document

  def collectAnnotations(unusedImports: Iterable[ImportUsed], annotationHolder: AnnotationHolder): Iterable[Annotation] = {
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
          val annotation = annotationHolder.createWarningAnnotation(psi, ScalaInspectionBundle.message("unused.import.statement")): @nowarn("cat=deprecation")
          annotation setHighlightType ProblemHighlightType.LIKE_UNUSED_SYMBOL
          getFixes.foreach(annotation.registerFix)
          qName.foreach(name => annotation.registerFix(new MarkImportAsAlwaysUsed(name)))
          Seq(annotation)
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
