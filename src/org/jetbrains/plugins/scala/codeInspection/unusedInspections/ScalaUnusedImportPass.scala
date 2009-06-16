package org.jetbrains.plugins.scala.codeInspection.unusedInspections


import annotator.importsTracker.ImportTracker
import collection.mutable.{HashSet, Set}
import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.codeInsight.daemon.impl.{HighlightInfo, UpdateHighlightersUtil, HighlightInfoFilter, AnnotationHolderImpl}
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.Annotation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.psi.util.{PsiTreeUtil, PsiUtilBase}
import com.intellij.psi.{PsiElement, PsiFile}
import java.util.ArrayList
import lang.lexer.ScalaTokenTypes
import lang.psi.api.ScalaFile
import lang.psi.api.toplevel.imports.usages.{ImportWildcardSelectorUsed, ImportSelectorUsed, ImportExprUsed, ImportUsed}
import lang.psi.{ScalaPsiElementImpl, ScalaPsiElement}
/**
 * User: Alexander Podkhalyuzin
 * Date: 15.06.2009
 */

class ScalaUnusedImportPass(file: PsiFile, editor: Editor) extends TextEditorHighlightingPass(file.getProject, editor.getDocument) {
  private var unusedImports: Set[ImportUsed] = new HashSet[ImportUsed]

  def doApplyInformationToEditor: Unit = {
    val annotationHolder = new AnnotationHolderImpl()
    val annotations: Seq[Annotation] = unusedImports.filter({
      imp: ImportUsed => {
        imp match {
          case expr: ImportExprUsed if !PsiTreeUtil.hasErrorElements(expr.expr)=> true
          case sel: ImportSelectorUsed => true
          case wild: ImportWildcardSelectorUsed if wild.e.selectors.length > 0 => true
          case wild: ImportWildcardSelectorUsed if !PsiTreeUtil.hasErrorElements(wild.e) => true
          case _ => false
        }
      }
    }).map({
      imp: ImportUsed => {
        //todo: add fix action
        val psi: PsiElement = imp match {
          case expr: ImportExprUsed if !PsiTreeUtil.hasErrorElements(expr.expr)=> expr.expr.getParent
          case sel: ImportSelectorUsed => sel.sel
          case wild: ImportWildcardSelectorUsed if wild.e.selectors.length > 0 => wild.e.wildcardElement match {case Some(p) => p}
          case wild: ImportWildcardSelectorUsed if !PsiTreeUtil.hasErrorElements(wild.e) => wild.e.getParent
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

  def doCollectInformation(progress: ProgressIndicator): Unit = {
    file match {
      case sFile: ScalaFile => {
        unusedImports = ImportTracker.getInstance(file.getProject).getUnusedImport(sFile)
      }
      case _ =>
    }
  }
}