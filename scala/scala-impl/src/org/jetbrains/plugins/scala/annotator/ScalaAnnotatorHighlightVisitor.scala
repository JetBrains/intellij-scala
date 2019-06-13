package org.jetbrains.plugins.scala
package annotator

import com.intellij.codeInsight.daemon.impl._
import com.intellij.codeInsight.daemon.impl.analysis.{HighlightInfoHolder, HighlightingLevelManager}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.psi._
import org.jetbrains.plugins.scala.annotator.hints.AnnotatorHints
import org.jetbrains.plugins.scala.extensions.PsiElementExt

/**
 * User: Alexander Podkhalyuzin
 * Date: 31.05.2010
 */
final class ScalaAnnotatorHighlightVisitor(project: Project) extends HighlightVisitor {

  import extensions.PsiFileExt
  import usageTracker.ScalaRefCountHolder

  override def order: Int = 0

  private var myHolder: HighlightInfoHolder = _
  private var myRefCountHolder: ScalaRefCountHolder = _
  private var myAnnotationHolder: AnnotationHolderImpl = _

  override def suitableForFile(file: PsiFile): Boolean = file.hasScalaPsi

  def visit(element: PsiElement): Unit = {
    if (DumbService.getInstance(project).isDumb) return

    val file = element.getContainingFile
    if (file == null) return

    val manager = HighlightingLevelManager.getInstance(project)

    if (manager.shouldHighlight(file)) {
      highlighter.AnnotatorHighlighter.highlightElement(element, myAnnotationHolder)
    }

    if (ApplicationManager.getApplication.isUnitTestMode || manager.shouldInspect(file)) {
      ScalaAnnotator(project).annotate(element, myAnnotationHolder)
    }

    myAnnotationHolder.forEach { annotation =>
      myHolder.add(HighlightInfo.fromAnnotation(annotation))
    }
    myAnnotationHolder.clear()
  }

  def analyze(file: PsiFile,
              updateWholeFile: Boolean,
              holder: HighlightInfoHolder,
              analyze: Runnable): Boolean = {
//    val time = System.currentTimeMillis()
    clearDirtyAnnotatorHintsIn(file)
    var success = true
    try {
      myHolder = holder
      myAnnotationHolder = new AnnotationHolderImpl(holder.getAnnotationSession)
      if (updateWholeFile) {
        myRefCountHolder = ScalaRefCountHolder.getInstance(file)
        success = myRefCountHolder.analyze(analyze, file)
      } else {
        myRefCountHolder = null
        analyze.run()
      }
    } finally {
      myHolder = null
      myAnnotationHolder = null
      myRefCountHolder = null
    }
    // TODO We should probably create a dedicated registry property that enables printing of the running time.
    // Otherwise, the output always pollutes the console, even when there's no need for that data.
    // IDEA's "internal mode" is a too coarse-grained switch for that.
//    val method: Long = System.currentTimeMillis() - time
//    if (method > 100 && ApplicationManager.getApplication.isInternal) println(s"File: ${file.getName}, Time: $method")
    success
  }

  // Annotator hints, SCL-15593
  private def clearDirtyAnnotatorHintsIn(file: PsiFile): Unit = {
    val dirtyScope = ScalaRefCountHolder.findDirtyScope(file)(file.getProject).flatten

    file.elements.foreach { element =>
      if (AnnotatorHints.in(element).exists(_.modificationCount < element.getManager.getModificationTracker.getModificationCount) &&
        dirtyScope.forall(_.contains(element.getTextRange))) {

        AnnotatorHints.clearIn(element)
      }
    }
  }

  override def clone = new ScalaAnnotatorHighlightVisitor(project)
}