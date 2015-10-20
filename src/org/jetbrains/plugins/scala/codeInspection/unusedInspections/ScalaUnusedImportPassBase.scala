package org.jetbrains.plugins.scala
package codeInspection.unusedInspections

import java.util

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.impl.{HighlightInfo, UpdateHighlightersUtil}
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.{Annotation, AnnotationHolder}
import com.intellij.openapi.editor.Document
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.editor.importOptimizer.ScalaImportOptimizer._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ImportExprUsed, ImportSelectorUsed, ImportUsed, ImportWildcardSelectorUsed}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector, ScImportStmt}

/**
 * User: Dmitry Naydanov
 * Date: 3/2/13
 */
trait ScalaUnusedImportPassBase { self: TextEditorHighlightingPass =>
  def file: PsiFile
  def document: Document

  def collectAnnotations(unusedImports: Array[ImportUsed], annotationHolder: AnnotationHolder): Array[Annotation] = unusedImports flatMap {
    imp: ImportUsed => {
      val psiOption: Option[PsiElement] = imp match {
        case ImportExprUsed(expr) if !PsiTreeUtil.hasErrorElements(expr) && !isLanguageFeatureImport(imp) =>
          val impSt = expr.getParent.asInstanceOf[ScImportStmt]
          if (impSt == null) None //todo: investigate this case, this cannot be null
          else if (impSt.importExprs.length == 1) Some(impSt)
          else Some(expr)
        case ImportSelectorUsed(sel) if !isLanguageFeatureImport(imp) => Some(sel)
        case ImportWildcardSelectorUsed(e) if e.selectors.nonEmpty && !isLanguageFeatureImport(imp) => Some(e.wildcardElement.get)
        case ImportWildcardSelectorUsed(e) if !PsiTreeUtil.hasErrorElements(e) && !isLanguageFeatureImport(imp) => Some(e.getParent)
        case _ => None
      }

      val qName = imp.qualName
      
      psiOption match {
        case None => Seq[Annotation]()
        case Some(sel: ScImportSelector) if sel.importedName == "_" => Seq[Annotation]()
        case Some(psi) if qName.exists(qName => ScalaCodeStyleSettings.getInstance(file.getProject).isAlwaysUsedImport(qName)) => Seq.empty
        case Some(psi) =>
          val annotation = annotationHolder.createWarningAnnotation(psi, "Unused import statement")
          annotation setHighlightType ProblemHighlightType.LIKE_UNUSED_SYMBOL
          getFixes.foreach(annotation.registerFix)
          qName.foreach(name => annotation.registerFix(new MarkImportAsAlwaysUsed(name)))
          Seq[Annotation](annotation)
      }
    }
  }
  
  protected def highlightAll(highlights: util.Collection[HighlightInfo]) {
    UpdateHighlightersUtil.setHighlightersToEditor(file.getProject, document, 0,
      file.getTextLength, highlights, getColorsScheme, getId)
  }
  
  protected def getFixes: List[IntentionAction]
}
