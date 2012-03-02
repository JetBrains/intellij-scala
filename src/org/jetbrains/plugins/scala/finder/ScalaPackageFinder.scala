package org.jetbrains.plugins.scala.finder

import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import com.intellij.psi.{PsiPackage, PsiClass, PsiElementFinder}
import com.intellij.openapi.project.Project

/**
 * User: Alefas
 * Date: 10.02.12
 */
class ScalaPackageFinder(project: Project) extends PsiElementFinder {
  def findClass(qualifiedName: String, scope: GlobalSearchScope): PsiClass = null

  def findClasses(qualifiedName: String, scope: GlobalSearchScope): Array[PsiClass] = PsiClass.EMPTY_ARRAY

  override def findPackage(qName: String): PsiPackage =
    ScalaPsiManager.instance(project).syntheticPackage(qName)
}
