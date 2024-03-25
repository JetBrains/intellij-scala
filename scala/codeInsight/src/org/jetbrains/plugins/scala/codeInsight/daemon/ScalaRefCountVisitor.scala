package org.jetbrains.plugins.scala
package codeInsight
package daemon

import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.{HighlightInfoHolder, HighlightingLevelManager}
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi._
import org.jetbrains.plugins.scala.annotator.{HighlightingAdvisor, ScalaAnnotator}
import org.jetbrains.plugins.scala.annotator.hints.AnnotatorHints
import org.jetbrains.plugins.scala.annotator.usageTracker.ScalaRefCountHolder
import org.jetbrains.plugins.scala.annotator.usageTracker.UsageTracker._
import org.jetbrains.plugins.scala.caches.CachesUtil.fileModCount
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt, PsiFileExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr._

final class ScalaRefCountVisitor(project: Project) extends HighlightVisitor {
  private val LOG = Logger.getInstance(classOf[ScalaRefCountVisitor])
  private var analyzedWholeFile = false

  override def suitableForFile(file: PsiFile): Boolean =
    HighlightingAdvisor.shouldInspect(file)

  override def visit(element: PsiElement): Unit =
    registerElementsAndImportsUsed(element)

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
    val success = if (updateWholeFile) {
      analyzedWholeFile = false

      val refCountHolder = ScalaRefCountHolder.getInstance(scalaFile)
      val isNew = refCountHolder.isNew
      val result = refCountHolder.analyze(analyze, scalaFile)

      // This will be true when the SoftReference that holds the ScalaRefCountHolder was garbage collected
      // and the highlighting pass only partially processes the file.
      // If that happens, the resulting RefCountHolder will look complete, but miss most references in the file
      // which leads to a lot of unused warnings.
      // To fix this, we can just fail the current analysis by returning false.
      // In that case the whole file will be analyzed.
      val incompleteRefCountHolder = isNew && !analyzedWholeFile
      if (incompleteRefCountHolder) {
        LOG.info(s"Failed ref-count analyzing '${scalaFile.name}' because the file was only partially analyzed and the RefCountHolder was new thus would be incomplete.")
      }

      result && !incompleteRefCountHolder
    } else {
      analyze.run()
      true
    }
    // TODO We should probably create a dedicated registry property that enables printing of the running time.
    // Otherwise, the output always pollutes the console, even when there's no need for that data.
    // IDEA's "internal mode" is a too coarse-grained switch for that.
//    val method: Long = System.currentTimeMillis() - time
//    if (method > 100 && ApplicationManager.getApplication.isInternal) println(s"File: ${file.getName}, Time: $method")
    success
  }

  private def registerElementsAndImportsUsed(element: PsiElement): Unit = {
    analyzedWholeFile ||= element.is[PsiFile]
    element match {
      case ref: ScReference =>
        val resolve = ref.multiResolveScala(false)
        registerUsedElementsAndImports(ref, resolve, checkWrite = true)
      case selfInv: ScSelfInvocation =>
        val resolve = selfInv.multiResolve
        registerUsedElementsAndImports(selfInv, resolve, checkWrite = false)
      case f: ScFor =>
        registerUsedImports(f, ScalaPsiUtil.getExprImports(f))
      case call: ScMethodCall =>
        registerUsedImports(call, call.getImportsUsed)

      case ret: ScReturn =>
        val importUsed = ret.expr
          .toSet[ScExpression]
          .flatMap(_.getTypeAfterImplicitConversion().importsUsed)
        registerUsedImports(element, importUsed)
      case _ =>
    }

    element.asOptionOf[ScExpression]
      .foreach { expr =>
        val fromUnderscore = ScUnderScoreSectionUtil.isUnderscoreFunction(expr)
        val importUsed = expr.getTypeAfterImplicitConversion(fromUnderscore = fromUnderscore).importsUsed

        registerUsedImports(element, importUsed)
      }

    element.asOptionOf[ImplicitArgumentsOwner]
      .foreach { owner =>
        owner.findImplicitArguments.foreach { params =>
          registerUsedElementsAndImports(element, params, checkWrite = false)
        }
      }
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

  override def clone = new ScalaRefCountVisitor(project)
}