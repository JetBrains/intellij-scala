package org.jetbrains.plugins.scala
package lang
package psi
package api

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.util.Key
import com.intellij.psi._

trait ScalaPsiElement extends PsiElement
  with project.ProjectContextOwner {

  import ScalaPsiElement._

  implicit def elementScope: ElementScope = ElementScope(this)

  implicit def projectContext: project.ProjectContext = this.getProject

  final def isInCompiledFile: Boolean = getContainingFile match {
    case sf: ScalaFile => sf.isCompiled
    case _ => false
  }

  abstract override def getContext: PsiElement = this.context match {
    case null => super.getContext
    case element => element
  }

  def getSameElementInContext: PsiElement = this.child match {
    case null => this
    case element => element
  }

  def getDeepSameElementInContext: PsiElement = this.child match {
    case null => this
    case element if element == this.context => this
    case element: ScalaPsiElement => element.getDeepSameElementInContext
    case element => element
  }

  def startOffsetInParent: Int = this.child match {
    case element: ScalaPsiElement => element.startOffsetInParent
    case _ => getStartOffsetInParent
  }

  protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T

  protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T]

  protected def findChild[T >: Null <: ScalaPsiElement](clazz: Class[T]): Option[T] =
    Option(findChildByClassScala(clazz))

  def findLastChildByType[T <: PsiElement](t: tree.IElementType): T = {
    var node = getNode.getLastChildNode
    while (node != null && node.getElementType != t) {
      node = node.getTreePrev
    }
    if (node == null) null.asInstanceOf[T]
    else node.getPsi.asInstanceOf[T]
  }

  def findFirstChildByType(t: tree.IElementType): PsiElement = {
    var node = getNode.getFirstChildNode
    while (node != null && node.getElementType != t) {
      node = node.getTreeNext
    }
    if (node == null) null else node.getPsi
  }

  def findChildrenByType(t: tree.IElementType): List[PsiElement] = {
    val buffer = new collection.mutable.ArrayBuffer[PsiElement]
    var node = getNode.getFirstChildNode
    while (node != null) {
      if (node.getElementType == t) buffer += node.getPsi
      node = node.getTreeNext
    }
    buffer.toList
  }

  def findLastChildByType(set: tree.TokenSet): PsiElement = {
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

  abstract override def accept(visitor: PsiElementVisitor): Unit = visitor match {
    case visitor: ScalaElementVisitor => acceptScala(visitor)
    case _ => super.accept(visitor)
  }

  protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitScalaElement(this)
  }

  def acceptChildren(visitor: ScalaElementVisitor): Unit = getChildren.foreach {
    case element: ScalaPsiElement => element.accept(visitor)
    case _ =>
  }
}

object ScalaPsiElement {

  private[this] val ContextKey = Key.create[PsiElement]("context.key")
  private[this] val ChildKey = Key.create[PsiElement]("child.key")

  implicit class ScalaPsiElementExt(private val element: ScalaPsiElement) extends AnyVal {

    def context: PsiElement = apply(ContextKey)

    def context_=(context: PsiElement): Unit = update(ContextKey, context)

    def child: PsiElement = apply(ChildKey)

    def child_=(child: PsiElement): Unit = update(ChildKey, child)

    private def apply[T](key: Key[T]) = element match {
      case file: PsiFileBase => file.getCopyableUserData(key)
      case _ => element.getUserData(key)
    }

    private def update[T](key: Key[T], value: T): Unit = element match {
      case file: PsiFileBase => file.putCopyableUserData(key, value)
      case _ => element.putUserData(key, value)
    }
  }

}
