package org.jetbrains.plugins.scala
package lang
package psi

import com.intellij.psi.search.{LocalSearchScope, SearchScope}
import com.intellij.psi.tree.{IElementType, TokenSet}
import com.intellij.psi.{PsiElement, PsiManager}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.intersectScopes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.util.monads.MonadTransformer

trait ScalaPsiElement extends PsiElement with MonadTransformer {
  protected var context: PsiElement = null
  protected var child: PsiElement = null

  implicit def typeSystem: TypeSystem = getProject.typeSystem

  implicit def manager: PsiManager = getManager

  def isInCompiledFile: Boolean =
    this.containingScalaFile.exists {
      _.isCompiled
    }

  def setContext(element: PsiElement, child: PsiElement) {
    context = element
    this.child = child
  }

  def getSameElementInContext: PsiElement =
    child match {
      case null => this
      case _ => child
    }

  def getDeepSameElementInContext: PsiElement =
    child match {
      case null => this
      case _ if child == context => this
      case child: ScalaPsiElement => child.getDeepSameElementInContext
      case _ => child
    }

  def startOffsetInParent: Int =
    child match {
      case s: ScalaPsiElement => s.startOffsetInParent
      case _ => getStartOffsetInParent
    }

  protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T

  protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T]

  protected def findChild[T >: Null <: ScalaPsiElement](clazz: Class[T]): Option[T] =
    Option(findChildByClassScala(clazz))

  def findLastChildByType[T <: PsiElement](t: IElementType): T = {
    var node = getNode.getLastChildNode
    while (node != null && node.getElementType != t) {
      node = node.getTreePrev
    }
    if (node == null) null.asInstanceOf[T]
    else node.getPsi.asInstanceOf[T]
  }

  def findFirstChildByType(t: IElementType): PsiElement = {
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

  def findLastChildByType(set: TokenSet): PsiElement = {
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

  /**
   * Override in inheritors
   */
  def accept(visitor: ScalaElementVisitor): Unit = {
    visitor.visitElement(this)
  }

  /**
   * Override in inheritors
   */
  def acceptChildren(visitor: ScalaElementVisitor): Unit =
    getChildren.collect {
      case element: ScalaPsiElement => element
    }.foreach {
      _.accept(visitor)
    }


  abstract override def getUseScope: SearchScope = {
    val maybeFileScope = this.containingScalaFile.filter { file =>
      file.isWorksheetFile || file.isScriptFile()
    }.map {
      new LocalSearchScope(_)
    }
    intersectScopes(super.getUseScope, maybeFileScope)
  }
}