package org.jetbrains.plugins.scala
package codeInsight
package daemon

import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.project.Project
import com.intellij.psi._
import org.jetbrains.plugins.scala.annotator.HighlightingAdvisor
import org.jetbrains.plugins.scala.annotator.hints.AnnotatorHints
import org.jetbrains.plugins.scala.annotator.importUsageTracker.ScalaImportRefCountHolder
import org.jetbrains.plugins.scala.annotator.importUsageTracker.ImportUsageTracker._
import org.jetbrains.plugins.scala.caches.CachesUtil.fileModCount
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt, PsiFileExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

final class ScalaImportRefCountVisitor(project: Project) extends HighlightVisitor {

  private var myRefCountHolder: ScalaImportRefCountHolder = _

  override def suitableForFile(file: PsiFile): Boolean =
    HighlightingAdvisor.shouldInspect(file)

  private def filterResolveResultsAndRegisterUsedImports(element: PsiElement, results: Iterable[ScalaResolveResult]): Unit =
    for (resolveResult <- results if resolveResult != null) {
      registerUsedImports(element, resolveResult.importsUsed)
    }

  override def visit(element: PsiElement): Unit = {
    element match {
      case ref: ScReference =>
        val resolve = ref.multiResolveScala(false)
        filterResolveResultsAndRegisterUsedImports(ref, resolve)
      case selfInv: ScSelfInvocation =>
        val resolve = selfInv.multiResolve
        filterResolveResultsAndRegisterUsedImports(selfInv, resolve)
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
          filterResolveResultsAndRegisterUsedImports(element, params)
        }
      }
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
      if (updateWholeFile) {
        myRefCountHolder = ScalaImportRefCountHolder.getInstance(scalaFile)
        success = myRefCountHolder.analyze(analyze, scalaFile)
      } else {
        myRefCountHolder = null
        analyze.run()
      }
    } finally {
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
    val dirtyScope = ScalaImportRefCountHolder.findDirtyScope(file).flatten

    file.elements.foreach { element =>
      if (AnnotatorHints.in(element).exists(_.modificationCount < fileModCount(file)) &&
        dirtyScope.forall(_.contains(element.getTextRange))) {

        AnnotatorHints.clearIn(element)
      }
    }
  }

  override def clone = new ScalaImportRefCountVisitor(project)
}