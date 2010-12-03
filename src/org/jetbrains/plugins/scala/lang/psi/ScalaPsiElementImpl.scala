package org.jetbrains.plugins.scala
package lang
package psi

import _root_.com.intellij.extapi.psi.{StubBasedPsiElementBase, ASTWrapperPsiElement}
import api.ScalaElementVisitor
import com.intellij.psi.stubs.{StubElement, IStubElementType}
import com.intellij.lang.ASTNode
import stubs.elements.wrappers.DummyASTNode
import com.intellij.psi.impl.source.tree.{SharedImplUtil, CompositeElement}
import com.intellij.psi.{PsiElementVisitor, PsiElement, StubBasedPsiElement}
import com.intellij.psi.tree.{TokenSet, IElementType}

/**
@author ven
 */
abstract class ScalaPsiElementImpl(node: ASTNode) extends ASTWrapperPsiElement(node) with ScalaPsiElement {
  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  private val _locked = new ThreadLocal[Boolean] {
    override def initialValue: Boolean = false
  }

  override def getContext: PsiElement = {
    context match {
      case null => super.getContext
      case _ => context
    }
  }

  override def getStartOffsetInParent: Int = {
    context match {
      case null => super.getStartOffsetInParent
      case _ => child.getStartOffsetInParent
    }
  }

  override def getPrevSibling: PsiElement = {
    context match {
      case null => super.getPrevSibling
      case _ => child.getPrevSibling
    }
  }

  override def getNextSibling: PsiElement = {
    context match {
      case null => super.getNextSibling
      case _ => child.getNextSibling
    }
  }

  override def findLastChildByType(t: IElementType) = {
    super[ScalaPsiElement].findLastChildByType(t)
  }

  override def findLastChildByType(t: TokenSet) = {
    super[ScalaPsiElement].findLastChildByType(t)
  }

  protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] = findChildrenByClass[T](clazz)

  protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T = findChildByClass[T](clazz)

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
  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def getElementType(): IStubElementType[StubElement[T], T] = {
    if (getNode != DummyASTNode && getNode != null) getNode.getElementType.asInstanceOf[IStubElementType[StubElement[T], T]]
    else getStub.getStubType.asInstanceOf[IStubElementType[StubElement[T], T]]
  }

  override def getContext: PsiElement = {
    context match {
      case null => super.getContext
      case _ => context
    }
  }

  override def getStartOffsetInParent: Int = {
    context match {
      case null => super.getStartOffsetInParent
      case _ => child.getStartOffsetInParent
    }
  }


  override def getPrevSibling: PsiElement = {
    context match {
      case null => super.getPrevSibling
      case _ => child.getPrevSibling
    }
  }

  override def getNextSibling: PsiElement = {
    context match {
      case null => super.getNextSibling
      case _ => child.getNextSibling
    }
  }

  override def getParent(): PsiElement = {
    val stub = getStub
    if (stub != null) {
      return stub.getParentStub.getPsi
    }
    SharedImplUtil.getParent(getNode)
  }

  override def findLastChildByType(t: IElementType) = {
    super[ScalaPsiElement].findLastChildByType(t)
  }

  override def findLastChildByType(t: TokenSet) = {
    super[ScalaPsiElement].findLastChildByType(t)
  }

  protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    findChildrenByClass[T](clazz)

  protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T = findChildByClass[T](clazz)
}
