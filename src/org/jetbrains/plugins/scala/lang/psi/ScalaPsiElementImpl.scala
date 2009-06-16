package org.jetbrains.plugins.scala.lang.psi

import _root_.com.intellij.extapi.psi.{StubBasedPsiElementBase, ASTWrapperPsiElement}
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.stubs.{StubElement, IStubElementType}
import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElement, StubBasedPsiElement}
import stubs.elements.wrappers.DummyASTNode

/**
@author ven
 */
abstract class ScalaPsiElementImpl(node: ASTNode) extends ASTWrapperPsiElement(node) with ScalaPsiElement {
  private val _locked = new ThreadLocal[Boolean] {
    override def initialValue: Boolean = false
  }

  override protected def lock(handler: => Unit) {
    if (!_locked.get) {
      _locked.set(true)
      handler
      _locked.set(false)
    }
  }

  // todo override in more specific cases
  override def replace(newElement: PsiElement): PsiElement = {
    getParent.getNode.replaceChild(getNode, newElement.getNode)
    newElement
  }
}

abstract class ScalaStubBasedElementImpl[T <: PsiElement]
        extends StubBasedPsiElementBase[StubElement[T]](DummyASTNode) with ScalaPsiElement with StubBasedPsiElement[StubElement[T]] {
  override def getElementType(): IStubElementType[StubElement[T], T] = {
    if (getNode != DummyASTNode && getNode != null) getNode.getElementType.asInstanceOf[IStubElementType[StubElement[T], T]]
    else getStub.getStubType.asInstanceOf[IStubElementType[StubElement[T], T]]
  }
}