package org.jetbrains.plugins.scala
package lang
package psi

import com.intellij.psi.PsiElement
import com.intellij.psi.search.{LocalSearchScope, SearchScope}
import com.intellij.psi.tree.{IElementType, TokenSet}
import org.jetbrains.plugins.scala.extensions.implementation.PsiElementExtTrait
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaFile}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.util.monads.MonadTransformer

trait ScalaPsiElement extends PsiElement with PsiElementExtTrait with MonadTransformer {
  protected override def repr = this
  protected var context: PsiElement = null
  protected var child: PsiElement = null

  implicit lazy val typeSystem = getProject.typeSystem

  def isInCompiledFile = getContainingFile match {
    case file: ScalaFile => file.isCompiled
    case _ => false
  }

  def setContext(element: PsiElement, child: PsiElement) {
    context = element
    this.child = child
  }

  def getSameElementInContext: PsiElement = {
    child match {
      case null => this
      case _ => child
    }
  }

  def getDeepSameElementInContext: PsiElement = {
    child match {
      case null => this
      case _ if child == context => this
      case child: ScalaPsiElement => child.getDeepSameElementInContext
      case _ => child
    }
  }

  def startOffsetInParent: Int = {
    child match {
      case s: ScalaPsiElement => s.startOffsetInParent
      case _ => getStartOffsetInParent
    }
  }

  protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T

  protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T]

  protected def findChild[T >: Null <: ScalaPsiElement](clazz: Class[T]): Option[T] = findChildByClassScala(clazz) match {
    case null => None
    case e => Some(e)
  }

  def findLastChildByType[T <: PsiElement](t: IElementType): T = {
    var node = getNode.getLastChildNode
    while (node != null && node.getElementType != t) {
      node = node.getTreePrev
    }
    if (node == null) null.asInstanceOf[T]
    else node.getPsi.asInstanceOf[T]
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

  abstract override def getUseScope: SearchScope = {
    ScalaPsiUtil.intersectScopes(super.getUseScope, containingFile match {
      case Some(file: ScalaFile) if file.isWorksheetFile || file.isScriptFile() => Some(new LocalSearchScope(file))
      case _ => None
    })
  }
}