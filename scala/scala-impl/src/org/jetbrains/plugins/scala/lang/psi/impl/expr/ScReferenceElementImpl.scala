package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, CompletionProcessor}

/**
  * Nikolay.Tropin
  * 19-Jan-17
  */
abstract class ScReferenceElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScReferenceElement {

  def resolve(): PsiElement = {
    bind() match {
      case Some(result) if !result.isCyclicReference => result.element
      case _ => null
    }
  }

  @TestOnly
  def advancedResolve: Option[ScalaResolveResult] = {
    bind() match {
      case Some(result) if !result.isCyclicReference =>  Some(result)
      case _ => None
    }
  }

  def doResolve(processor: BaseProcessor, accessibilityCheck: Boolean = true): Array[ScalaResolveResult]

  def getVariants: Array[Object] = completionVariants()().toArray

  override def completionVariants(incomplete: Boolean,
                                  completion: Boolean,
                                  implicits: Boolean)
                                 (function: ScalaResolveResult => Seq[ScalaLookupItem]): Seq[ScalaLookupItem] = {
    val processor = new CompletionProcessor(getKinds(incomplete, completion), this)
    doResolve(processor).flatMap(function)
  }

  @inline
  def bind(): Option[ScalaResolveResult] = {
    ProgressManager.checkCanceled()
    multiResolveScala(false) match {
      case Array(r) => Some(r)
      case _ => None
    }
  }
}
