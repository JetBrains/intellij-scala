package org.jetbrains.plugins.scala
package codeInspection.unusedInspections

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.impl.{HighlightInfo, UpdateHighlightersUtil}
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.{Annotation, AnnotationHolder}
import com.intellij.openapi.editor.Document
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile}
import java.util
import org.jetbrains.plugins.scala.editor.importOptimizer.ScalaImportOptimizer._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ImportExprUsed, ImportSelectorUsed, ImportUsed, ImportWildcardSelectorUsed}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportSelector, ScImportStmt}

/**
 * User: Dmitry Naydanov
 * Date: 3/2/13
 */
trait ScalaUnusedImportPassBase { self: TextEditorHighlightingPass =>
  def file: PsiFile
  def document: Document

  def collectAnnotations(unusedImports: Array[ImportUsed], annotationHolder: AnnotationHolder): Array[Annotation] = unusedImports flatMap {
    imp: ImportUsed => {
      val psi: PsiElement = imp match {
        case ImportExprUsed(expr) if !PsiTreeUtil.hasErrorElements(expr) && !isLanguageFeatureImport(imp) =>
          val impSt = expr.getParent.asInstanceOf[ScImportStmt]
          if (impSt == null) null //todo: investigate this case, this cannot be null
          else if (impSt.importExprs.length == 1) impSt
          else expr
        case ImportSelectorUsed(sel) if !isLanguageFeatureImport(imp) => sel
        case ImportWildcardSelectorUsed(e) if e.selectors.length > 0 && !isLanguageFeatureImport(imp) => e.wildcardElement.get
        case ImportWildcardSelectorUsed(e) if !PsiTreeUtil.hasErrorElements(e) && !isLanguageFeatureImport(imp) => e.getParent
        case _ => null
      }
      
      psi match {
        case null => Seq[Annotation]()
        case sel: ScImportSelector if sel.importedName == "_" => Seq[Annotation]()
        case _ =>
          val annotation = annotationHolder.createWarningAnnotation(psi, "Unused import statement")
          annotation setHighlightType ProblemHighlightType.LIKE_UNUSED_SYMBOL
          getFixes.foreach(annotation.registerFix)
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
