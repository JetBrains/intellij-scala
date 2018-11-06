package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, CompletionProcessor}
import org.jetbrains.plugins.scala.util.UIFreezingGuard

/**
  * Nikolay.Tropin
  * 19-Jan-17
  */
abstract class ScReferenceElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScReferenceElement {

  def resolve(): PsiElement = {
    bind() match {
      case Some(result) => result.element
      case _ => null
    }
  }

  def doResolve(processor: BaseProcessor, accessibilityCheck: Boolean = true): Array[ScalaResolveResult]

  override def getVariants: Array[Object] = completionVariants().toArray

  override def completionVariants(implicits: Boolean): Seq[ScalaLookupItem] = {
    val processor = new CompletionProcessor(getKinds(incomplete = true, completion = false), this)
    doResolve(processor).flatMap(toLookupItems)
  }

  protected def toLookupItems(result: ScalaResolveResult): Seq[ScalaLookupItem] = {
    result.getLookupElement(isInImport = PsiTreeUtil.getContextOfType(this, classOf[ScImportStmt]) != null)
  }

  final def bind(): Option[ScalaResolveResult] = {
    ProgressManager.checkCanceled()
    val timeoutMs =
      if (ApplicationManager.getApplication.isDispatchThread) UIFreezingGuard.resolveTimeoutMs else -1

    val result =
      if (timeoutMs < 0) multiResolveScala(false)
      else UIFreezingGuard.withTimeout(timeoutMs, multiResolveScala(false), ScalaResolveResult.EMPTY_ARRAY)

    result match {
      case Array(r) => Some(r)
      case _ => None
    }
  }
}
