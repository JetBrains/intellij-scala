package org.jetbrains.plugins.scala
package lang
package psi

import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaElementVisitor}
import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElementVisitor, PsiElement}
import com.intellij.psi.tree.{TokenSet, IElementType}
import com.intellij.psi.impl.source.tree.{LazyParseablePsiElement, SharedImplUtil}
import com.intellij.psi.impl.CheckUtil
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.psi.search.{LocalSearchScope, SearchScope}

/**
@author ven
 */
abstract class ScalaPsiElementImpl(node: ASTNode) extends ASTWrapperPsiElement(node) with ScalaPsiElement {
  override def accept(visitor: PsiElementVisitor) {
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
    child match {
      case null => super.getStartOffsetInParent
      case _ => child.getStartOffsetInParent
    }
  }

  override def getPrevSibling: PsiElement = {
    child match {
      case null => super.getPrevSibling
      case _ => child.getPrevSibling
    }
  }

  override def getNextSibling: PsiElement = {
    child match {
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

  protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    findChildrenByClass[T](clazz)

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

  override def delete() {
    getParent match {
      case x: LazyParseablePsiElement =>
        CheckUtil.checkWritable(this)
        x.deleteChildInternal(getNode)
      case _ => super.delete()
    }
  }
}

abstract class ScalaStubBasedElementImpl[T <: PsiElement]
        extends ScalaStubBaseElementImplJavaRawTypeHack[T] with ScalaPsiElement {
  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def getContext: PsiElement = {
    context match {
      case null => super.getContext
      case _ => context
    }
  }

  override def getStartOffsetInParent: Int = {
    child match {
      case null => super.getStartOffsetInParent
      case _ => child.getStartOffsetInParent
    }
  }


  override def getPrevSibling: PsiElement = {
    child match {
      case null => super.getPrevSibling
      case _ => ScalaPsiUtil.getPrevStubOrPsiElement(child)
    }
  }

  override def getNextSibling: PsiElement = {
    child match {
      case null => super.getNextSibling
      case _ => ScalaPsiUtil.getNextStubOrPsiElement(child)
    }
  }

  override def getParent: PsiElement = {
    val stub = getStub
    if (stub != null) {
      return stub.getParentStub.getPsi
    }
    SharedImplUtil.getParent(getNode)
  }

  def getLastChildStub: PsiElement = {
    val stub = getStub
    if (stub != null) {
      val children = stub.getChildrenStubs
      if (children.size() == 0) return null
      return children.get(children.size() - 1).getPsi
    }
    getLastChild
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

  override def delete() {
    getParent match {
      case x: LazyParseablePsiElement =>
        CheckUtil.checkWritable(this)
        x.deleteChildInternal(getNode)
      case _ => super.delete()
    }
  }
}
