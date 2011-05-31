package org.jetbrains.plugins.scala.annotator

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import org.jetbrains.plugins.scala.annotator.importsTracker.ScalaRefCountHolder
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.editor.Document
import com.intellij.psi._
import com.intellij.openapi.util.TextRange
import com.intellij.codeHighlighting.Pass
import collection.JavaConversions
import com.intellij.lang.annotation.{AnnotationSession, Annotator}
import com.intellij.codeInsight.daemon.impl._

/**
 * User: Alexander Podkhalyuzin
 * Date: 31.05.2010
 */

class ScalaAnnotatorHighlightVisitor(project: Project) extends HighlightVisitor {
  def order: Int = 0

  private var myHolder: HighlightInfoHolder = null
  private var myRefCountHolder: ScalaRefCountHolder = null

  override def suitableForFile(file: PsiFile): Boolean = {
    file.isInstanceOf[ScalaFile]
  }

  override def visit(element: PsiElement, holder: HighlightInfoHolder) {
    myHolder = holder
    runAnnotator(element)
  }

  override def analyze(action: Runnable, updateWholeFile: Boolean, file: PsiFile): Boolean = {
    var success = true
    try {
      if (updateWholeFile) {
        val project: Project = file.getProject
        val daemonCodeAnalyzer: DaemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(project)
        val fileStatusMap: FileStatusMap = (daemonCodeAnalyzer.asInstanceOf[DaemonCodeAnalyzerImpl]).getFileStatusMap
        val refCountHolder: ScalaRefCountHolder = ScalaRefCountHolder.getInstance(file)
        myRefCountHolder = refCountHolder
        val document: Document = PsiDocumentManager.getInstance(project).getDocument(file)
        val dirtyScope: TextRange = if (document == null) file.getTextRange else fileStatusMap.getFileDirtyScope(document, Pass.UPDATE_ALL)
        success = refCountHolder.analyze(action, dirtyScope, file)
      }
      else {
        myRefCountHolder = null
        action.run()
      }
    }
    finally {
      myHolder = null
      myRefCountHolder = null
    }
    success
  }

  override def clone: HighlightVisitor = {
    new ScalaAnnotatorHighlightVisitor(project)
  }

  private def runAnnotator(element: PsiElement) {
    if (DumbService.getInstance(project).isDumb) {
      return
    }
    val annotator: Annotator = new ScalaAnnotator
    val myAnnotationHolder = new AnnotationHolderImpl(new AnnotationSession(element.getContainingFile))
    annotator.annotate(element, myAnnotationHolder)
    if (myAnnotationHolder.hasAnnotations) {
      import JavaConversions._
      for (annotation <- myAnnotationHolder) {
        myHolder.add(HighlightInfo.fromAnnotation(annotation))
      }
      myAnnotationHolder.clear()
    }
  }
}