package org.jetbrains.plugins.scala
package lang
package psi

import com.intellij.openapi.project.Project
import com.intellij.psi.search.{GlobalSearchScope, LocalSearchScope, SearchScope}
import com.intellij.psi.tree.{IElementType, TokenSet}
import com.intellij.psi.{PsiClass, PsiElement, PsiManager}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement.ElementScope
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.intersectScopes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameterType, TypeSystem, UndefinedType}
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScalaType}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.util.monads.MonadTransformer

trait ScalaPsiElement extends PsiElement with MonadTransformer {
  protected var context: PsiElement = null
  protected var child: PsiElement = null

  implicit def typeSystem: TypeSystem = PsiElementExt(this).typeSystem

  implicit def elementScope: ElementScope = PsiElementExt(this).elementScope

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

object ScalaPsiElement {

  case class ElementScope(project: Project, scope: GlobalSearchScope) {

    implicit def typeSystem: TypeSystem = project.typeSystem

    def getCachedClass(fqn: String): Option[PsiClass] =
      getCachedClasses(fqn).find {
        !_.isInstanceOf[ScObject]
      }

    def getCachedObject(fqn: String): Option[ScObject] =
      getCachedClasses(fqn).collect {
        case o: ScObject => o
      }.headOption

    def cachedFunction1Type: Option[ScParameterizedType] =
      manager.cachedFunction1Type(this)

    def function1Type(level: Int = 1): Option[ScParameterizedType] =
      getCachedClass("scala.Function1").collect {
        case t: ScTrait => t
      }.map { t =>
        val parameters = t.typeParameters.map {
          TypeParameterType(_)
        }.map {
          UndefinedType(_, level = level)(project.typeSystem)
        }

        ScParameterizedType(ScalaType.designator(t), parameters)
      }.collect {
        case p: ScParameterizedType => p
      }

    def getCachedClasses(fqn: String): Array[PsiClass] =
      manager.getCachedClasses(scope, fqn)

    private def manager =
      ScalaPsiManager.instance(project)
  }

  object ElementScope {
    def apply(element: PsiElement): ElementScope =
      ElementScope(element.getProject, element.getResolveScope)

    def apply(project: Project): ElementScope =
      ElementScope(project, GlobalSearchScope.allScope(project))
  }

}