package org.jetbrains.plugins.scala
package lang
package psi

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.{TokenSet, IElementType}
import util.monads.MonadTransformer
import org.jetbrains.plugins.scala.psi.api.ScalaElementVisitor

trait ScalaPsiElement extends PsiElement with ScTypeInferenceHelper with MonadTransformer {
  protected var context: PsiElement = null
  def setContext(element: PsiElement) {
    context = element
  }

  protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T

  protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T]

  protected def findChild[T >: Null <: ScalaPsiElement](clazz: Class[T]): Option[T] = findChildByClassScala(clazz) match {
    case null => None
    case e => Some(e)
  }

  def findLastChildByType(t: IElementType) = {
    var node = getNode.getLastChildNode
    while (node != null && node.getElementType != t) {
      node = node.getTreePrev
    }
    if (node == null) null else node.getPsi
  }

  def findFirstChildByType(t: IElementType) = {
    var node = getNode.getFirstChildNode
    while (node != null && node.getElementType != t) {
      node = node.getTreeNext
    }
    if (node == null) null else node.getPsi
  }

  def findChildrenByType(t: IElementType): List[PsiElement] = {
    val buffer = new collection.mutable.ArrayBuffer[PsiElement]
    var node = getNode.getFirstChildNode
    while (node != null) {
      if (node.getElementType == t) buffer += node.getPsi
      node = node.getTreeNext
    }
    buffer.toList
  }

  def findLastChildByType(set: TokenSet) = {
    var node = getNode.getLastChildNode
    while (node != null && !set.contains(node.getElementType)) {
      node = node.getTreePrev
    }
    if (node == null) null else node.getPsi
  }

  protected def findLastChild[T >: Null <: ScalaPsiElement](clazz: Class[T]): Option[T] = {
    var child = getLastChild
    while (child != null && !clazz.isInstance(child)) {
      child = child.getPrevSibling
    }
    if (child == null) None else Some(child.asInstanceOf[T])
  }

  protected def lock(handler: => Unit): Unit = {}

  /**
   * Override in inheritors
   */
  def accept(visitor: ScalaElementVisitor) {
    visitor.visitElement(this)
  }

  /**
   * Override in inheritors
   */

  def acceptChildren(visitor: ScalaElementVisitor) {
    for (c <- getChildren; if c.isInstanceOf[ScalaPsiElement]) {
      c.asInstanceOf[ScalaPsiElement].accept(visitor)
    }
  }
}