package org.jetbrains.plugins.scala.lang.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, TypeParameterType, UndefinedType}
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScalaType}
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * Nikolay.Tropin
  * 19-Apr-17
  */
case class ElementScope(project: Project, scope: GlobalSearchScope) {
  implicit def projectContext: ProjectContext = project

  def getCachedClass(fqn: String): Option[PsiClass] =
    getCachedClasses(fqn).find {
      !_.isInstanceOf[ScObject]
    }

  def getCachedObject(fqn: String): Option[ScObject] =
    getCachedClasses(fqn).collectFirst {
      case o: ScObject => o
    }

  def cachedFunction1Type: Option[ScParameterizedType] =
    manager.cachedFunction1Type(this)

  def function1Type(level: Int = 1): Option[ScParameterizedType] =
    getCachedClass(FunctionType.TypeName + 1).collect {
      case t: ScTrait => t
    }.map { t =>
      val parameters = t.typeParameters.map {
        UndefinedType(_, level = level)
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
  def apply(element: PsiElement): ElementScope = {
    val project = element.getProject
    val scope = element.resolveScope

    ElementScope(project, scope)
  }

  def apply(project: Project): ElementScope =
    ElementScope(project, GlobalSearchScope.allScope(project))

  implicit def toProjectContext(implicit elementScope: ElementScope): ProjectContext = elementScope.project
}
