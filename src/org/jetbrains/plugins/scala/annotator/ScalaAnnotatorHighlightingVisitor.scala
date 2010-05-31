package org.jetbrains.plugins.scala.annotator

import com.intellij.codeInsight.daemon.impl.analysis.{HighlightInfoHolder, DefaultHighlightVisitor}
import importsTracker.ScalaRefCountHolder
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.openapi.project.{DumbService, DumbAware, Project}
import com.intellij.lang.annotation.Annotator
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl._
import com.intellij.openapi.editor.Document
import com.intellij.psi._
import com.intellij.openapi.util.TextRange
import com.intellij.codeHighlighting.Pass
import collection.JavaConversions
import com.intellij.codeInsight.highlighting.HighlightErrorFilter
import com.intellij.openapi.extensions.Extensions

/**
 * User: Alexander Podkhalyuzin
 * Date: 31.05.2010
 */

class ScalaAnnotatorHighlightVisitor(project: Project) extends DefaultHighlightVisitor(project) {
  private final val myAnnotationHolder: AnnotationHolderImpl = new AnnotationHolderImpl
  private var myHolder: HighlightInfoHolder = null
  private var myRefCountHolder: ScalaRefCountHolder = null
  private val myErrorFilters: Array[HighlightErrorFilter] = 
          Extensions.getExtensions(DefaultHighlightVisitor.FILTER_EP_NAME, project)

  override def suitableForFile(file: PsiFile): Boolean = {
    return file.isInstanceOf[ScalaFile]
  }

  override def visit(element: PsiElement, holder: HighlightInfoHolder): Unit = {
    myHolder = holder
    assert(!myAnnotationHolder.hasAnnotations(), myAnnotationHolder)
    element.accept(this)
  }

  override def analyze(action: Runnable, updateWholeFile: Boolean, file: PsiFile): Boolean = {
    var success = true
    try {
      if (updateWholeFile) {
        var project: Project = file.getProject
        var daemonCodeAnalyzer: DaemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(project)
        var fileStatusMap: FileStatusMap = (daemonCodeAnalyzer.asInstanceOf[DaemonCodeAnalyzerImpl]).getFileStatusMap
        var refCountHolder: ScalaRefCountHolder = ScalaRefCountHolder.getInstance(file)
        myRefCountHolder = refCountHolder
        var document: Document = PsiDocumentManager.getInstance(project).getDocument(file)
        var dirtyScope: TextRange = if (document == null) file.getTextRange else fileStatusMap.getFileDirtyScope(document, Pass.UPDATE_ALL)
        success = refCountHolder.analyze(action, dirtyScope, file)
      }
      else {
        myRefCountHolder = null
        action.run
      }
    }
    finally {
      myAnnotationHolder.clear
      myHolder = null
      myRefCountHolder = null
    }
    return success
  }

  override def clone: HighlightVisitor = {
    return new ScalaAnnotatorHighlightVisitor(project)
  }

  override def visitElement(element: PsiElement): Unit = {
    runAnnotator(element)
  }

  private def runAnnotator(element: PsiElement): Unit = {
    val dumb: Boolean = DumbService.getInstance(project).isDumb

    var annotator: Annotator = new ScalaAnnotator
    if (dumb && !(annotator.isInstanceOf[DumbAware])) {
      return
    }
    annotator.annotate(element, myAnnotationHolder)
    if (myAnnotationHolder.hasAnnotations) {
      import JavaConversions._
      for (annotation <- myAnnotationHolder) {
        myHolder.add(HighlightInfo.fromAnnotation(annotation))
      }
      myAnnotationHolder.clear
    }
  }

  override def visitErrorElement(element: PsiErrorElement): Unit = {
    for (errorFilter <- myErrorFilters) {
      if (!errorFilter.shouldHighlightErrorElement(element)) return
    }
    var info: HighlightInfo = DefaultHighlightVisitor.createErrorElementInfo(element)
    myHolder.add(info)
  }
}