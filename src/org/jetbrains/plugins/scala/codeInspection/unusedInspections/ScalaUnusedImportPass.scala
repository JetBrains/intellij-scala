package org.jetbrains.plugins.scala.codeInspection.unusedInspections


import annotator.importsTracker.ImportTracker
import collection.mutable.{Set, HashSet}
import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.codeInsight.daemon.impl.{HighlightInfo, UpdateHighlightersUtil, HighlightInfoFilter, AnnotationHolderImpl}
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.Annotation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile}
import java.util.ArrayList
import lang.psi.api.ScalaFile
import lang.psi.api.toplevel.imports.usages.{ImportWildcardSelectorUsed, ImportSelectorUsed, ImportExprUsed, ImportUsed}
/**
 * User: Alexander Podkhalyuzin
 * Date: 15.06.2009
 */

class ScalaUnusedImportPass(file: PsiFile, editor: Editor) extends TextEditorHighlightingPass(file.getProject, editor.getDocument) {
  def doApplyInformationToEditor: Unit = {
    if (file.isInstanceOf[ScalaFile]) {
      val sFile = file.asInstanceOf[ScalaFile]
      val annotationHolder = new AnnotationHolderImpl()
      val unusedImports: Set[ImportUsed] = ImportTracker.getInstance(file.getProject).getUnusedImport(sFile)
      //todo: rewrite this in more good style
      val annotations: Seq[Annotation] = unusedImports.filter({
        imp: ImportUsed => {
          imp match {
            case ImportExprUsed(expr) if !PsiTreeUtil.hasErrorElements(expr) => true
            case ImportSelectorUsed(_) => true
            case ImportWildcardSelectorUsed(e) if e.selectors.length > 0 => true
            case ImportWildcardSelectorUsed(e) if !PsiTreeUtil.hasErrorElements(e) => true
            case _ => false
          }
        }
      }).map({
        imp: ImportUsed => {
          //todo: add fix action
          val psi: PsiElement = imp match {
            case ImportExprUsed(expr) if !PsiTreeUtil.hasErrorElements(expr) => expr.getParent
            case ImportSelectorUsed(sel) => sel
            case ImportWildcardSelectorUsed(e) if e.selectors.length > 0 => e.wildcardElement match {case Some(p) => p}
            case ImportWildcardSelectorUsed(e) if !PsiTreeUtil.hasErrorElements(e) => e.getParent
          }
          val annotation: Annotation = annotationHolder.createWarningAnnotation(psi, "Unused import statement")
          annotation.setHighlightType(ProblemHighlightType.LIKE_UNUSED_SYMBOL)
          annotation
        }
      }).toSeq

      val holder = new HighlightInfoHolder(file, HighlightInfoFilter.EMPTY_ARRAY)
      val list = new ArrayList[HighlightInfo](annotations.length)
      for (annotation <- annotations) {
        list.add(HighlightInfo.fromAnnotation(annotation))
      }
      UpdateHighlightersUtil.setHighlightersToEditor(file.getProject, editor.getDocument, 0, file.getTextLength, list, getId)
    }
  }

  def doCollectInformation(progress: ProgressIndicator): Unit = {

  }
}