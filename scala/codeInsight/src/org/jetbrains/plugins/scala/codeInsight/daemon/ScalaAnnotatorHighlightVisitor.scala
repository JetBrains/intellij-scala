package org.jetbrains.plugins.scala
package codeInsight
package daemon

import com.intellij.codeInsight.daemon.impl.analysis.{HighlightInfoHolder, HighlightingLevelManager}
import com.intellij.codeInsight.daemon.impl.{AnnotationHolderImpl, HighlightInfo, HighlightVisitor}
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.project.Project
import com.intellij.psi._
import org.jetbrains.plugins.scala.annotator.ScalaAnnotator
import org.jetbrains.plugins.scala.annotator.hints.AnnotatorHints
import org.jetbrains.plugins.scala.annotator.usageTracker.ScalaRefCountHolder
import org.jetbrains.plugins.scala.caches.CachesUtil.fileModCount
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, PsiFileExt}

/**
 * User: Alexander Podkhalyuzin
 * Date: 31.05.2010
 */
final class ScalaAnnotatorHighlightVisitor(project: Project) extends HighlightVisitor {

  private var myHolder: HighlightInfoHolder = _
  private var myRefCountHolder: ScalaRefCountHolder = _
  private var myAnnotationHolder: AnnotationHolderImpl = _

  override def suitableForFile(file: PsiFile): Boolean = {
    val hasScala = file.hasScalaPsi
    // TODO: we currently only check
    //  HighlightingLevelManager.shouldInspect ~ "Highlighting: All Problems" in code analyses widget,
    //  but we ignore HighlightingLevelManager.shouldInspect ~ "Highlighting: Syntax"
    //  we should review all our annotators and split them accordingly
    val shouldInspect = file.isScala3File || HighlightingLevelManager.getInstance(project).shouldInspect(file)
    hasScala && (shouldInspect || isUnitTestMode)
  }

  override def visit(element: PsiElement): Unit = {
    ScalaAnnotator(project).annotate(element, myAnnotationHolder)

    myAnnotationHolder.forEach { annotation =>
      myHolder.add(HighlightInfo.fromAnnotation(annotation))
    }
    myAnnotationHolder.clear()
  }

  override def analyze(file: PsiFile,
                       updateWholeFile: Boolean,
                       holder: HighlightInfoHolder,
                       analyze: Runnable): Boolean = {
//    val time = System.currentTimeMillis()
    val scalaFile = file.findScalaLikeFile.orNull
    if (scalaFile == null)
      return true

    if (InjectedLanguageManager.getInstance(project).isInjectedFragment(scalaFile))
      return true

    clearDirtyAnnotatorHintsIn(scalaFile)
    var success = true
    try {
      myHolder = holder
      myAnnotationHolder = new AnnotationHolderImpl(holder.getAnnotationSession)
      if (updateWholeFile) {
        myRefCountHolder = ScalaRefCountHolder.getInstance(scalaFile)
        success = myRefCountHolder.analyze(analyze, scalaFile)
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
    val dirtyScope = ScalaRefCountHolder.findDirtyScope(file).flatten

    file.elements.foreach { element =>
      if (AnnotatorHints.in(element).exists(_.modificationCount < fileModCount(file)) &&
        dirtyScope.forall(_.contains(element.getTextRange))) {

        AnnotatorHints.clearIn(element)
      }
    }
  }

  override def clone = new ScalaAnnotatorHighlightVisitor(project)
}