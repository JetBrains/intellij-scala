package org.jetbrains.plugins.scala
package lang
package psi
package api

import com.intellij.openapi.util.Key
import com.intellij.psi.{PsiElement, PsiElementVisitor, search, tree}

trait ScalaPsiElement extends PsiElement
  with project.ProjectContextOwner {

  import ScalaPsiElement._
  import extensions.NullSafe

  protected var child: PsiElement = null

  implicit def elementScope: ElementScope = ElementScope(this)

  implicit def projectContext: project.ProjectContext = this.getProject

  final def isInCompiledFile: Boolean = getContainingFile match {
    case sf: ScalaFile => sf.isCompiled
    case _ => false
  }

  def setContext(context: PsiElement, child: PsiElement): Unit = {
    this.context = context
    this.child = child
  }

  def context: PsiElement = getCopyableUserData(ContextKey)

  def context_=(context: PsiElement): Unit = {
    putCopyableUserData(ContextKey, context)
  }

  abstract override def getContext: PsiElement =
    NullSafe(context).getOrElse(super.getContext)

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

  abstract override def getUseScope: search.SearchScope = {
    import ScalaUseScope._

    val scriptScope = getContainingFile match {
      case file: ScalaFile if file.isWorksheetFile || file.isScriptFile =>
        Some(safeLocalScope(file))
      case _ => None
    }

    val mostNarrow = (this match {
      case parameter: statements.params.ScParameter => Option(parameterScope(parameter))
      case named: toplevel.ScNamedElement => namedScope(named)
      case member: toplevel.typedef.ScMember => memberScope(member)
      case _ => None
    }).map {
      intersect(_, scriptScope)
    }.orElse {
      scriptScope
    }

    intersect(super.getUseScope, mostNarrow)
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

  private val ContextKey = Key.create[PsiElement]("context.key")
}
