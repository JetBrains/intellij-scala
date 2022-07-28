package org.jetbrains.plugins.scala.lang.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.extensions.{ArrayExt, OptionExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, UndefinedType}
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType, ScalaType}
import org.jetbrains.plugins.scala.project.ProjectContext

case class ElementScope(project: Project, scope: GlobalSearchScope) {
  implicit def projectContext: ProjectContext = project

  def getCachedClass(fqn: String): Option[PsiClass] =
    getCachedClasses(fqn).find {
      !_.isInstanceOf[ScObject]
    }

  def getCachedObject(fqn: String): Option[ScObject] =
    getCachedClasses(fqn).findByType[ScObject]

  def cachedFunction1Type: Option[ScParameterizedType] =
    manager.cachedFunction1Type(this)

  def getFunctionTrait(parametersCount: Int = 1): Option[ScTrait] =
    getCachedClass(FunctionType.TypeName + parametersCount).filterByType[ScTrait]

  def function1Type(level: Int = 1): Option[ScParameterizedType] =
    getFunctionTrait().map { t =>
      val parameters = t.typeParameters.map {
        UndefinedType(_, level = level)
      }

      ScParameterizedType(ScalaType.designator(t), parameters)
    }.filterByType[ScParameterizedType]

  def getCachedClasses(fqn: String): Array[PsiClass] =
    manager.getCachedClasses(scope, fqn)

  def scalaSeqType: Option[ScType] =
    manager.scalaSeqAlias(scope).map(ScDesignatorType.apply)

  private def manager =
    ScalaPsiManager.instance(project)
}

object ElementScope {
  def apply(element: PsiElement): ElementScope = {
    val project = element.getProject
    val scope   = element.resolveScope

    ElementScope(project, scope)
  }

  def apply(project: Project): ElementScope =
    ElementScope(project, GlobalSearchScope.allScope(project))

  implicit def toProjectContext(implicit elementScope: ElementScope): ProjectContext =
    elementScope.project
}
