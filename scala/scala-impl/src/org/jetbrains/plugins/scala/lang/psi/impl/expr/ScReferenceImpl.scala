package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, CompletionProcessor}

/**
  * Nikolay.Tropin
  * 19-Jan-17
  */
abstract class ScReferenceImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScReference {
  override def resolve(): PsiElement = {
    bind() match {
      case Some(result) => result.element
      case _ => null
    }
  }

  def doResolve(processor: BaseProcessor, accessibilityCheck: Boolean = true): Array[ScalaResolveResult]

  override def getVariants: Array[Object] = completionVariants().toArray

  override def completionVariants(implicits: Boolean): Seq[ScalaLookupItem] = {
    val processor = new CompletionProcessor(getKinds(incomplete = true, completion = false), this)
    doResolve(processor).flatMap(toLookupItem)
  }

  protected def toLookupItem(result: ScalaResolveResult): Option[ScalaLookupItem] =
    result.getLookupElement(isInImport = PsiTreeUtil.getContextOfType(this, classOf[ScImportStmt]) != null)

  override final def bind(): Option[ScalaResolveResult] = {
    ProgressManager.checkCanceled()

    multiResolveScala(false) match {
      case Array(r) => Some(r)
      case _ => None
    }
  }
}
