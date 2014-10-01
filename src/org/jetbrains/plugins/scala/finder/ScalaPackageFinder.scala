package org.jetbrains.plugins.scala.finder

import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiClass, PsiElementFinder, PsiPackage}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager

/**
 * User: Alefas
 * Date: 10.02.12
 */
class ScalaPackageFinder(project: Project) extends PsiElementFinder {
  def findClass(qualifiedName: String, scope: GlobalSearchScope): PsiClass = null

  def findClasses(qualifiedName: String, scope: GlobalSearchScope): Array[PsiClass] = PsiClass.EMPTY_ARRAY

  override def findPackage(qName: String): PsiPackage = {
    if (DumbService.isDumb(project)) return null
    ScalaPsiManager.instance(project).syntheticPackage(qName)
  }
}
