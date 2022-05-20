package org.jetbrains.plugins.scala
package lang
package psi
package impl

import com.intellij.extapi.psi.{ASTWrapperPsiElement, StubBasedPsiElementBase}
import com.intellij.lang.{ASTNode, Language}
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.psi.impl.CheckUtil
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.{PsiElement, StubBasedPsiElement}
import com.intellij.util.ArrayFactory
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.caches.ModTracker
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{stubOrPsiNextSibling, stubOrPsiPrevSibling}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

abstract class ScalaPsiElementImpl(node: ASTNode) extends ASTWrapperPsiElement(node)
  with ScalaPsiElement {

  override def getStartOffsetInParent: Int = this.child match {
    case null => super.getStartOffsetInParent
    case element => element.getStartOffsetInParent
  }

  override def getPrevSibling: PsiElement = this.child match {
    case null => super.getPrevSibling
    case element => element.getPrevSibling
  }

  override def getNextSibling: PsiElement = this.child match {
    case null => super.getNextSibling
    case element => element.getNextSibling
  }

  override protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    findChildrenByClass[T](clazz)

  override protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T = findChildByClass[T](clazz)

  // todo override in more specific cases
  override def replace(newElement: PsiElement): PsiElement = {
    val newElementCopy = newElement.copy
    getParent.getNode.replaceChild(getNode, newElementCopy.getNode)
    newElementCopy
  }

  override def delete(): Unit = {
    getParent match {
      case x: LazyParseablePsiElement =>
        CheckUtil.checkWritable(this)
        x.deleteChildInternal(getNode)
      case _ => super.delete()
    }
  }

  override def subtreeChanged(): Unit = {
    ModTracker.anyScalaPsiChange.incModificationCount()
    super.subtreeChanged()
  }

  override final def getLanguage: Language = super.getLanguage

  override def getUseScope: SearchScope =
    ScalaUseScope(super.getUseScope, this)
}

abstract class ScalaStubBasedElementImpl[T <: PsiElement, S <: StubElement[T]](@Nullable stub: S,
                                                                               nodeType: stubs.elements.ScStubElementType[S, T],
                                                                               node: ASTNode)
  extends StubBasedPsiElementBase[S](stub, if (stub == null) null else nodeType, node)
    with StubBasedPsiElement[S]
    with ScalaPsiElement {

  override final def getElementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement] = super.getElementType

  override def getStartOffsetInParent: Int = this.child match {
    case null => super.getStartOffsetInParent
    case element => element.getStartOffsetInParent
  }

  override def getPrevSibling: PsiElement = this.child match {
    case null => super.getPrevSibling
    case element => stubOrPsiPrevSibling(element)
  }

  override def getNextSibling: PsiElement = this.child match {
    case null => super.getNextSibling
    case element => stubOrPsiNextSibling(element)
  }

  override protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    findChildrenByClass[T](clazz)

  override protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T = findChildByClass[T](clazz)

  override def getStubOrPsiChildren[Psi <: PsiElement](filter: TokenSet, f: ArrayFactory[_ <: Psi]): Array[Psi] = {
    assertFilterMakesSenseForStubs(filter)
    super.getStubOrPsiChildren(filter, f)
  }

  override def delete(): Unit = {
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

  override def subtreeChanged(): Unit = {
    ModTracker.anyScalaPsiChange.incModificationCount()
    super.subtreeChanged()
  }

  override def copyCopyableDataTo(clone: UserDataHolderBase): Unit = {
    super.copyCopyableDataTo(clone)

    val stubbed = clone.asInstanceOf[ScalaStubBasedElementImpl[_, _]]
    stubbed.context = this.context
    stubbed.child = this.child
  }

  override final def getLanguage: Language = super.getLanguage

  override def getUseScope: SearchScope =
    ScalaUseScope(super.getUseScope, this)

  private def assertFilterMakesSenseForStubs(filter: TokenSet): Unit = {
    if (isUnitTestMode && (filter ne TokenSet.ANY)) {
      val elementTypes = filter.getTypes
      val nonStubTypes = elementTypes.filterNot(_.is[IStubElementType[_, _]])
      if (nonStubTypes.nonEmpty)
        throw new IllegalArgumentException(s"Non-stub element types (${nonStubTypes.mkString(", ")}) should not be used in getStubOrPsiChildren")
    }
  }

}