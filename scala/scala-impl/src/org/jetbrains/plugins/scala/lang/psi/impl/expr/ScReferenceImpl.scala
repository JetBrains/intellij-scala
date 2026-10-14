package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, CompletionProcessor}
import org.jetbrains.plugins.scala.lang.resolve.{ReferenceVariants, ScalaResolveResult}

abstract class ScReferenceImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScReference {
  override def resolve(): PsiElement = {
    bind() match {
      case Some(result) => result.element
      case _ => null
    }
  }

  def doResolve(processor: BaseProcessor, accessibilityCheck: Boolean = true): Array[ScalaResolveResult]

  override def getVariants: Array[Object] = ReferenceVariants.instance
    .lookupElements(this, completionVariants(withImplicitConversions = true))

  override def completionVariants(withImplicitConversions: Boolean): Array[ScalaResolveResult] = {
    val processor = new CompletionProcessor(
      getKinds(incomplete = true, completion = true),
      this,
      withImplicitConversions
    )
    doResolve(processor)
  }

  override final def bind(): Option[ScalaResolveResult] = {
    ProgressManager.checkCanceled()

    multiResolveScala(false) match {
      case Array(r) => Some(r)
      case _ => None
    }
  }
}
