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
import com.intellij.codeInsight.daemon.impl._
import org.jetbrains.plugins.scala.util.ScalaLanguageDerivative

/**
 * User: Alexander Podkhalyuzin
 * Date: 31.05.2010
 */

class ScalaAnnotatorHighlightVisitor(project: Project) extends HighlightVisitor {
  def order: Int = 0

  private var myHolder: HighlightInfoHolder = null
  private var myRefCountHolder: ScalaRefCountHolder = null
  private var myAnnotationHolder: AnnotationHolderImpl = null

  override def suitableForFile(file: PsiFile): Boolean = file match {
    case _: ScalaFile => true
    case otherFile => ScalaLanguageDerivative hasDerivativeOnFile otherFile
  }

  def visit(element: PsiElement) {
    runAnnotator(element)
  }

  def analyze(file: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, action: Runnable): Boolean = {
    val time = System.currentTimeMillis()
    var success = true
    try {
      myHolder = holder
      myAnnotationHolder = new AnnotationHolderImpl(holder.getAnnotationSession)
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
      myAnnotationHolder = null
      myRefCountHolder = null
    }
    val method: Long = System.currentTimeMillis() - time
    if (method > 100) println(s"File: ${file.getName}, Time: $method")
    success
  }

  override def clone: HighlightVisitor = {
    new ScalaAnnotatorHighlightVisitor(project)
  }

  private def runAnnotator(element: PsiElement) {
    if (DumbService.getInstance(project).isDumb) {
      return
    }
    (new ScalaAnnotator).annotate(element, myAnnotationHolder)
    if (myAnnotationHolder.hasAnnotations) {
      import JavaConversions._
      for (annotation <- myAnnotationHolder) {
        myHolder.add(HighlightInfo.fromAnnotation(annotation))
      }
      myAnnotationHolder.clear()
    }
  }
}