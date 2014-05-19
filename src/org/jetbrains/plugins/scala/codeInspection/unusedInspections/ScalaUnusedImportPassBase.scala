package org.jetbrains.plugins.scala
package codeInspection.unusedInspections

import lang.psi.api.toplevel.imports.usages.{ImportWildcardSelectorUsed, ImportSelectorUsed, ImportExprUsed, ImportUsed}
import com.intellij.lang.annotation.{Annotation, AnnotationHolder}
import com.intellij.psi.{PsiFile, PsiElement}
import com.intellij.psi.util.PsiTreeUtil
import editor.importOptimizer.ScalaImportOptimizer._
import lang.psi.api.toplevel.imports.{ScImportSelector, ScImportExpr, ScImportStmt}
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.editor.Editor
import com.intellij.codeHighlighting.TextEditorHighlightingPass
import java.util
import com.intellij.codeInsight.daemon.impl.{UpdateHighlightersUtil, HighlightInfo}
import com.intellij.codeInsight.intention.IntentionAction

/**
 * User: Dmitry Naydanov
 * Date: 3/2/13
 */
trait ScalaUnusedImportPassBase { self: TextEditorHighlightingPass =>
  protected val myEditor: Editor 
  protected val myFile: PsiFile
  
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
          annotation registerFix getOptimizeFix  
          Seq[Annotation](annotation)
      }
    }
  }
  
  protected def highlightAll(highlights: util.List[HighlightInfo]) {
    UpdateHighlightersUtil.setHighlightersToEditor(myFile.getProject, myEditor.getDocument, 0, 
      myFile.getTextLength, highlights, getColorsScheme, getId)
  }
  
  protected def getOptimizeFix: IntentionAction
}
