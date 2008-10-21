package org.jetbrains.plugins.scala.lang.psi

import _root_.com.intellij.extapi.psi.{StubBasedPsiElementBase, ASTWrapperPsiElement}
import com.intellij.psi.stubs.{StubElement, IStubElementType}
import com.intellij.psi.tree.IElementType
import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElement, StubBasedPsiElement}
import stubs.elements.wrappers.DummyASTNode

/**
  @author ven
*/
abstract class ScalaPsiElementImpl(node: ASTNode) extends ASTWrapperPsiElement(node) with ScalaPsiElement {
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