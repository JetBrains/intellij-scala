package org.jetbrains.plugins.scala.lang.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.extensions.{ArrayExt, ObjectExt, OptionExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, UndefinedType}
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType, ScalaType}
import org.jetbrains.plugins.scala.project.ProjectContext

trait ElementScope extends ProjectContext {
  def scope: GlobalSearchScope

  def getCachedClass(fqn: String): Option[PsiClass] =
    getCachedClasses(fqn).find {
      !_.is[ScObject]
    }

  def getCachedObject(fqn: String): Option[ScObject] =
    getCachedClasses(fqn).findByType[ScObject]

  def cachedFunction1Type: Option[ScParameterizedType] =
    ScalaPsiManager.instance(project).cachedFunction1Type(this)

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
    ScalaPsiManager.instance(project).getCachedClasses(scope, fqn)

  def scalaSeqType: Option[ScType] =
    ScalaPsiManager.instance(project).scalaSeqAlias(scope).map(ScDesignatorType.apply)

  def scalaNamedTupleType: Option[ScTypeAlias] =
    ScalaPsiManager.instance(project).scalaNamedTupleAlias(scope)
}

object ElementScope {
  def apply(project: Project, scope: GlobalSearchScope): ElementScope =
    new SimpleElementScope(project, scope)

  def apply(element: PsiElement): ElementScope = {
    val project = element.getProject
    val scope   = element.resolveScope
    new SimpleElementScope(project, scope)
  }

  def apply(project: Project): ElementScope =
    new SimpleElementScope(project, GlobalSearchScope.allScope(project))

  def unapply(scope: ElementScope): Some[(Project, GlobalSearchScope)] =
    Some((scope.project, scope.scope))

  private class SimpleElementScope(
    override val project: Project,
    override val scope: GlobalSearchScope
  ) extends ElementScope
}
