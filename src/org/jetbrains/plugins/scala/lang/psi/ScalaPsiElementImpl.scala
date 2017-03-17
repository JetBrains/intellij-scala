package org.jetbrains.plugins.scala
package lang
package psi

import com.intellij.extapi.psi.{ASTWrapperPsiElement, StubBasedPsiElementBase}
import com.intellij.lang.ASTNode
import com.intellij.psi.impl.CheckUtil
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import com.intellij.psi.tree.{IElementType, TokenSet}
import com.intellij.psi.{PsiElement, PsiElementVisitor, StubBasedPsiElement}
import org.jetbrains.plugins.scala.lang.psi.ScalaStubBasedElementImpl.ifNotNull
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubElementType

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

  override def findLastChildByType[T <: PsiElement](t: IElementType): T = {
    super[ScalaPsiElement].findLastChildByType(t)
  }

  override def findLastChildByType(t: TokenSet): PsiElement = {
    super[ScalaPsiElement].findLastChildByType(t)
  }

  protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    findChildrenByClass[T](clazz)

  protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T = findChildByClass[T](clazz)

  // todo override in more specific cases
  override def replace(newElement: PsiElement): PsiElement = {
    val newElementCopy = newElement.copy
    getParent.getNode.replaceChild(getNode, newElementCopy.getNode)
    newElementCopy
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

abstract class ScalaStubBasedElementImpl[T <: PsiElement, S <: StubElement[T]](stub: S,
                                                                               nodeType: ScStubElementType[S, T],
                                                                               node: ASTNode)
        extends StubBasedPsiElementBase[S](stub, ifNotNull(stub, nodeType), node)
          with StubBasedPsiElement[S] with ScalaPsiElement {

  override def getElementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement] = {
    byStubOrPsi(_.getStubType) {
      getNode.getElementType.asInstanceOf[IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement]]
    }
  }

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

  override def findLastChildByType[T <: PsiElement](t: IElementType): T = {
    super[ScalaPsiElement].findLastChildByType(t)
  }

  override def findLastChildByType(t: TokenSet): PsiElement = {
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

  //may use stubs even if AstNode exists
  def byStubOrPsi[R](byStub: S => R)(byPsi: => R): R = getGreenStub match {
    case null => byPsi
    case s => byStub(s)
  }

  //byStub branch is used only if AstNode is missing
  def byPsiOrStub[R](byPsi: => R)(byStub: S => R): R = getStub match {
    case null => byPsi
    case s => byStub(s)
  }
}

object ScalaStubBasedElementImpl {
  def ifNotNull[T >: Null](stub: AnyRef, node: T): T = if (stub == null) null else node
}