package org.jetbrains.plugins.scala.lang.psi.stubs.index

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValue
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.StubIndexStringKeyExt

object StableValIndex {
  def forClassFqn(qualifiedName: String, scope: GlobalSearchScope)
                 (implicit project: Project): Set[ScValue] =
    ScalaIndexKeys.PROPERTY_CLASS_NAME_KEY
      .forClassFqn(qualifiedName, scope)
      .filterByType[ScValue]

  def findValuesOfClassType(c: PsiClass, scope: GlobalSearchScope): Set[ScValue] = {
    val className = c.qualifiedName
    forClassFqn(className, scope)(c.getProject)
      .filter {
        case v: ScValue if v.declaredElements.size == 1 =>
          v.`type`().toOption
            .flatMap(_.extractClass)
            .map(_.qualifiedName)
            .contains(className)
        case _ => false
      }
  }
}
